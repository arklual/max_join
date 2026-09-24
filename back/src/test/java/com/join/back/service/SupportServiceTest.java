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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupportServiceTest {

    private static final LocalDateTime FIXED_TIME = LocalDateTime.of(2026, 3, 21, 10, 0);

    @Mock
    private SupportTicketRepository supportTicketRepository;

    @Mock
    private SupportMessageRepository supportMessageRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private SupportService supportService;

    // ---- getOrCreateTicket ----

    @Test
    void shouldReturnExistingTicket() {
        Long userId = 1L;
        SupportTicket ticket = buildTicket(1L, userId, SupportTicketStatus.OPEN);

        when(supportTicketRepository.findByUserId(userId)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(userId)).thenReturn(Optional.of(buildUser(userId)));
        when(supportMessageRepository.findLastMessageByTicketId(ticket.getId())).thenReturn(Optional.empty());
        when(supportMessageRepository.countByTicketIdAndSenderTypeAndIsReadFalse(ticket.getId(), SupportSenderType.OPERATOR)).thenReturn(0L);

        SupportTicketResponse result = supportService.getOrCreateTicket(userId);

        assertNotNull(result);
        assertEquals(1L, result.id());
        assertEquals(SupportTicketStatus.OPEN, result.status());
    }

    @Test
    void shouldCreateNewTicketWhenNotExists() {
        Long userId = 2L;
        SupportTicket newTicket = buildTicket(10L, userId, SupportTicketStatus.OPEN);

        when(supportTicketRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(supportTicketRepository.save(any(SupportTicket.class))).thenReturn(newTicket);
        when(userRepository.findById(userId)).thenReturn(Optional.of(buildUser(userId)));
        when(supportMessageRepository.findLastMessageByTicketId(newTicket.getId())).thenReturn(Optional.empty());
        when(supportMessageRepository.countByTicketIdAndSenderTypeAndIsReadFalse(newTicket.getId(), SupportSenderType.OPERATOR)).thenReturn(0L);

        SupportTicketResponse result = supportService.getOrCreateTicket(userId);

        assertNotNull(result);
        assertEquals(SupportTicketStatus.OPEN, result.status());
        verify(supportTicketRepository).save(any(SupportTicket.class));
    }

    // ---- getMessages ----

    @Test
    void shouldReturnPaginatedMessages() {
        Long userId = 1L;
        SupportTicket ticket = buildTicket(1L, userId, SupportTicketStatus.OPEN);
        SupportMessage message = buildMessage(1L, ticket.getId(), SupportSenderType.USER, "Hello");
        Pageable pageable = PageRequest.of(0, 20);
        Page<SupportMessage> messagePage = new PageImpl<>(List.of(message), pageable, 1);

        when(supportTicketRepository.findByUserId(userId)).thenReturn(Optional.of(ticket));
        when(supportMessageRepository.findByTicketIdOrderByCreatedAtDesc(ticket.getId(), pageable)).thenReturn(messagePage);

        Page<SupportMessageResponse> result = supportService.getMessages(userId, pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals("Hello", result.getContent().get(0).text());
        assertEquals(SupportSenderType.USER, result.getContent().get(0).senderType());
    }

    @Test
    void shouldThrowWhenTicketNotFoundForUser() {
        Long userId = 99L;
        when(supportTicketRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () ->
                supportService.getMessages(userId, Pageable.unpaged()));
    }

    // ---- sendUserMessage ----

    @Test
    void shouldSendUserMessageAndNotifyOperator() {
        Long userId = 1L;
        SupportTicket ticket = buildTicket(1L, userId, SupportTicketStatus.OPEN);
        SupportMessage savedMessage = buildMessage(5L, ticket.getId(), SupportSenderType.USER, "My question");

        when(supportTicketRepository.findByUserId(userId)).thenReturn(Optional.of(ticket));
        when(supportMessageRepository.save(any(SupportMessage.class))).thenReturn(savedMessage);
        when(supportTicketRepository.save(any(SupportTicket.class))).thenReturn(ticket);
        when(userRepository.findById(userId)).thenReturn(Optional.of(buildUser(userId)));

        SupportMessageResponse result = supportService.sendUserMessage(userId, new SendSupportMessageRequest("My question"));

        assertNotNull(result);
        assertEquals("My question", result.text());
        assertEquals(SupportSenderType.USER, result.senderType());

        verify(messagingTemplate).convertAndSend(
                eq("/topic/support/operator"),
                any(SupportOperatorNotification.class)
        );
    }

    // ---- sendOperatorMessage ----

    @Test
    void shouldSendOperatorMessageAndNotifyUser() {
        Long ticketId = 1L;
        Long userId = 42L;
        SupportTicket ticket = buildTicket(ticketId, userId, SupportTicketStatus.OPEN);
        SupportMessage savedMessage = buildMessage(6L, ticketId, SupportSenderType.OPERATOR, "Operator reply");

        when(supportTicketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(supportMessageRepository.save(any(SupportMessage.class))).thenReturn(savedMessage);
        when(supportTicketRepository.save(any(SupportTicket.class))).thenReturn(ticket);

        SupportMessageResponse result = supportService.sendOperatorMessage(ticketId, new SendSupportMessageRequest("Operator reply"));

        assertNotNull(result);
        assertEquals("Operator reply", result.text());
        assertEquals(SupportSenderType.OPERATOR, result.senderType());

        verify(messagingTemplate).convertAndSendToUser(
                eq("42"),
                eq("/queue/support"),
                any(SupportMessageResponse.class)
        );
    }

    @Test
    void shouldThrowWhenSendingToClosedTicket() {
        Long ticketId = 1L;
        SupportTicket ticket = buildTicket(ticketId, 1L, SupportTicketStatus.CLOSED);

        when(supportTicketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        assertThrows(IllegalStateException.class, () ->
                supportService.sendOperatorMessage(ticketId, new SendSupportMessageRequest("test")));
    }

    // ---- markReadByUser ----

    @Test
    void shouldMarkOperatorMessagesAsReadForUser() {
        Long userId = 1L;
        SupportTicket ticket = buildTicket(1L, userId, SupportTicketStatus.OPEN);

        when(supportTicketRepository.findByUserId(userId)).thenReturn(Optional.of(ticket));

        supportService.markReadByUser(userId);

        verify(supportMessageRepository).markAsReadBySenderType(ticket.getId(), SupportSenderType.OPERATOR);
    }

    // ---- markReadByOperator ----

    @Test
    void shouldMarkUserMessagesAsReadForOperator() {
        Long ticketId = 1L;
        SupportTicket ticket = buildTicket(ticketId, 1L, SupportTicketStatus.OPEN);

        when(supportTicketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        supportService.markReadByOperator(ticketId);

        verify(supportMessageRepository).markAsReadBySenderType(ticketId, SupportSenderType.USER);
    }

    // ---- updateStatus ----

    @Test
    void shouldUpdateTicketStatus() {
        Long ticketId = 1L;
        Long userId = 1L;
        SupportTicket ticket = buildTicket(ticketId, userId, SupportTicketStatus.OPEN);
        SupportTicket closedTicket = buildTicket(ticketId, userId, SupportTicketStatus.CLOSED);

        when(supportTicketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(supportTicketRepository.save(any(SupportTicket.class))).thenReturn(closedTicket);
        when(userRepository.findById(userId)).thenReturn(Optional.of(buildUser(userId)));
        when(supportMessageRepository.findLastMessageByTicketId(ticketId)).thenReturn(Optional.empty());
        when(supportMessageRepository.countByTicketIdAndSenderTypeAndIsReadFalse(ticketId, SupportSenderType.OPERATOR)).thenReturn(0L);

        SupportTicketResponse result = supportService.updateStatus(ticketId, SupportTicketStatus.CLOSED);

        assertEquals(SupportTicketStatus.CLOSED, result.status());
    }

    // ---- getAllTickets ----

    @Test
    void shouldReturnAllTickets() {
        Long userId = 1L;
        SupportTicket ticket = buildTicket(1L, userId, SupportTicketStatus.OPEN);
        Page<SupportTicket> page = new PageImpl<>(List.of(ticket));

        when(supportTicketRepository.findAllByOrderByLastMessageAtDesc(any(Pageable.class))).thenReturn(page);
        when(userRepository.findById(userId)).thenReturn(Optional.of(buildUser(userId)));
        when(supportMessageRepository.findLastMessageByTicketId(ticket.getId())).thenReturn(Optional.empty());
        when(supportMessageRepository.countByTicketIdAndSenderTypeAndIsReadFalse(ticket.getId(), SupportSenderType.OPERATOR)).thenReturn(0L);

        List<SupportTicketResponse> result = supportService.getAllTickets(PageRequest.of(0, 50));

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).id());
    }

    // ---- helpers ----

    private SupportTicket buildTicket(Long id, Long userId, SupportTicketStatus status) {
        return SupportTicket.builder()
                .id(id)
                .userId(userId)
                .status(status)
                .createdAt(FIXED_TIME)
                .build();
    }

    private SupportMessage buildMessage(Long id, Long ticketId, SupportSenderType senderType, String text) {
        return SupportMessage.builder()
                .id(id)
                .ticketId(ticketId)
                .senderType(senderType)
                .text(text)
                .createdAt(FIXED_TIME)
                .isRead(false)
                .build();
    }

    private User buildUser(Long userId) {
        return User.builder()
                .id(userId)
                .maxId(100L + userId)
                .firstName("TestUser")
                .createdAt(FIXED_TIME)
                .build();
    }
}
