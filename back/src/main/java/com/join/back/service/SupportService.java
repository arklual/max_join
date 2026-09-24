package com.join.back.service;

import com.join.back.model.dto.SendSupportMessageRequest;
import com.join.back.model.dto.SupportMessageResponse;
import com.join.back.model.dto.SupportOperatorNotification;
import com.join.back.model.dto.SupportTicketResponse;
import com.join.back.model.entity.SupportMessage;
import com.join.back.model.entity.SupportSenderType;
import com.join.back.model.entity.SupportTicket;
import com.join.back.model.entity.SupportTicketStatus;
import com.join.back.model.entity.User;
import com.join.back.repository.SupportMessageRepository;
import com.join.back.repository.SupportTicketRepository;
import com.join.back.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SupportService {

    private final SupportTicketRepository supportTicketRepository;
    private final SupportMessageRepository supportMessageRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Get or create a support ticket for the given user.
     * If the user has a CLOSED ticket, create a new OPEN one.
     * If user has an OPEN ticket, return it.
     */
    @Transactional(transactionManager = "transactionManager")
    public SupportTicketResponse getOrCreateTicket(Long userId) {
        SupportTicket ticket = supportTicketRepository.findByUserId(userId)
                .orElseGet(() -> createNewTicket(userId));

        return toTicketResponse(ticket, userId);
    }

    /**
     * Get paginated messages for a user's ticket.
     */
    @Transactional(readOnly = true, transactionManager = "transactionManager")
    public Page<SupportMessageResponse> getMessages(Long userId, Pageable pageable) {
        SupportTicket ticket = supportTicketRepository.findByUserId(userId)
                .orElseThrow(() -> new EntityNotFoundException("Support ticket not found for user: " + userId));

        return supportMessageRepository.findByTicketIdOrderByCreatedAtDesc(ticket.getId(), pageable)
                .map(this::toMessageResponse);
    }

    /**
     * Send a message from the user to support.
     * Side effect: push notification to operator via WebSocket.
     */
    @Transactional(transactionManager = "transactionManager")
    public SupportMessageResponse sendUserMessage(Long userId, SendSupportMessageRequest request) {
        SupportTicket ticket = supportTicketRepository.findByUserId(userId)
                .orElseGet(() -> createNewTicket(userId));

        SupportMessage message = SupportMessage.builder()
                .ticketId(ticket.getId())
                .senderType(SupportSenderType.USER)
                .text(request.text())
                .createdAt(LocalDateTime.now())
                .isRead(false)
                .build();

        SupportMessage saved = supportMessageRepository.save(message);

        ticket.setLastMessageAt(saved.getCreatedAt());
        supportTicketRepository.save(ticket);

        SupportMessageResponse response = toMessageResponse(saved);

        // Notify operators via WebSocket
        User user = userRepository.findById(userId).orElse(null);
        String firstName = user != null ? user.getFirstName() : null;

        SupportOperatorNotification notification = new SupportOperatorNotification(
                ticket.getId(),
                userId,
                firstName,
                response
        );
        messagingTemplate.convertAndSend("/topic/support/operator", notification);

        return response;
    }

    /**
     * Send a message from the operator to a user's ticket.
     * Side effect: push notification to user via WebSocket.
     */
    @Transactional(transactionManager = "transactionManager")
    public SupportMessageResponse sendOperatorMessage(Long ticketId, SendSupportMessageRequest request) {
        SupportTicket ticket = supportTicketRepository.findById(ticketId)
                .orElseThrow(() -> new EntityNotFoundException("Support ticket not found with id: " + ticketId));

        if (ticket.getStatus() == SupportTicketStatus.CLOSED) {
            throw new IllegalStateException("Ticket is closed");
        }

        SupportMessage message = SupportMessage.builder()
                .ticketId(ticketId)
                .senderType(SupportSenderType.OPERATOR)
                .text(request.text())
                .createdAt(LocalDateTime.now())
                .isRead(false)
                .build();

        SupportMessage saved = supportMessageRepository.save(message);

        ticket.setLastMessageAt(saved.getCreatedAt());
        supportTicketRepository.save(ticket);

        SupportMessageResponse response = toMessageResponse(saved);

        // Push to user via WebSocket
        messagingTemplate.convertAndSendToUser(
                ticket.getUserId().toString(),
                "/queue/support",
                response
        );

        return response;
    }

    /**
     * Mark all OPERATOR messages in the user's ticket as read (user opened the chat).
     */
    @Transactional(transactionManager = "transactionManager")
    public void markReadByUser(Long userId) {
        SupportTicket ticket = supportTicketRepository.findByUserId(userId)
                .orElseThrow(() -> new EntityNotFoundException("Support ticket not found for user: " + userId));

        supportMessageRepository.markAsReadBySenderType(ticket.getId(), SupportSenderType.OPERATOR);
    }

    /**
     * Mark all USER messages in a ticket as read (operator opened the ticket).
     */
    @Transactional(transactionManager = "transactionManager")
    public void markReadByOperator(Long ticketId) {
        supportTicketRepository.findById(ticketId)
                .orElseThrow(() -> new EntityNotFoundException("Support ticket not found with id: " + ticketId));

        supportMessageRepository.markAsReadBySenderType(ticketId, SupportSenderType.USER);
    }

    /**
     * Update ticket status (OPEN / CLOSED).
     */
    @Transactional(transactionManager = "transactionManager")
    public SupportTicketResponse updateStatus(Long ticketId, SupportTicketStatus newStatus) {
        SupportTicket ticket = supportTicketRepository.findById(ticketId)
                .orElseThrow(() -> new EntityNotFoundException("Support ticket not found with id: " + ticketId));

        ticket.setStatus(newStatus);
        SupportTicket saved = supportTicketRepository.save(ticket);

        return toTicketResponse(saved, saved.getUserId());
    }

    /**
     * Get all tickets (admin), paginated, ordered by lastMessageAt DESC.
     */
    @Transactional(readOnly = true, transactionManager = "transactionManager")
    public List<SupportTicketResponse> getAllTickets(Pageable pageable) {
        Page<SupportTicket> tickets = supportTicketRepository.findAllByOrderByLastMessageAtDesc(pageable);

        return tickets.stream()
                .map(ticket -> toTicketResponse(ticket, ticket.getUserId()))
                .collect(Collectors.toList());
    }

    /**
     * Get paginated messages for a specific ticket (admin view).
     */
    @Transactional(readOnly = true, transactionManager = "transactionManager")
    public Page<SupportMessageResponse> getTicketMessages(Long ticketId, Pageable pageable) {
        supportTicketRepository.findById(ticketId)
                .orElseThrow(() -> new EntityNotFoundException("Support ticket not found with id: " + ticketId));

        return supportMessageRepository.findByTicketIdOrderByCreatedAtDesc(ticketId, pageable)
                .map(this::toMessageResponse);
    }

    // ---- private helpers ----

    private SupportTicket createNewTicket(Long userId) {
        SupportTicket ticket = SupportTicket.builder()
                .userId(userId)
                .status(SupportTicketStatus.OPEN)
                .createdAt(LocalDateTime.now())
                .build();
        return supportTicketRepository.save(ticket);
    }

    private SupportTicketResponse toTicketResponse(SupportTicket ticket, Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        String firstName = user != null ? user.getFirstName() : null;
        Long maxId = user != null ? user.getMaxId() : null;

        SupportMessage lastMessage = supportMessageRepository
                .findLastMessageByTicketId(ticket.getId())
                .orElse(null);

        String lastMessagePreview = lastMessage != null ? truncate(lastMessage.getText(), 100) : null;

        long unreadCount = supportMessageRepository
                .countByTicketIdAndSenderTypeAndIsReadFalse(ticket.getId(), SupportSenderType.OPERATOR);

        return new SupportTicketResponse(
                ticket.getId(),
                userId,
                firstName,
                maxId,
                ticket.getStatus(),
                ticket.getCreatedAt(),
                ticket.getLastMessageAt(),
                lastMessagePreview,
                unreadCount
        );
    }

    private SupportMessageResponse toMessageResponse(SupportMessage message) {
        return new SupportMessageResponse(
                message.getId(),
                message.getTicketId(),
                message.getSenderType(),
                message.getText(),
                message.getCreatedAt(),
                message.isRead()
        );
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return null;
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }
}
