package com.join.back.service;

import com.join.back.model.dto.ChatMessageResponse;
import com.join.back.model.dto.ChatResponse;
import com.join.back.model.entity.Chat;
import com.join.back.model.entity.ChatMessage;
import com.join.back.model.entity.Event;
import com.join.back.model.entity.User;
import com.join.back.repository.ChatMessageRepository;
import com.join.back.repository.ChatRepository;
import com.join.back.repository.EventRepository;
import com.join.back.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatRepository chatRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final MessengerNotificationService messengerNotificationService;
    private final UserBlockService userBlockService;

    @Transactional(transactionManager = "transactionManager")
    public Chat createChat(Long matchId, Long user1Id, Long user2Id, Long eventId) {
        Chat chat = Chat.builder()
                .matchId(matchId)
                .user1Id(user1Id)
                .user2Id(user2Id)
                .eventId(eventId)
                .createdAt(LocalDateTime.now())
                .build();
        return chatRepository.save(chat);
    }

    @Transactional(readOnly = true, transactionManager = "transactionManager")
    public List<ChatResponse> getChats(Long userId) {
        List<Chat> chats = chatRepository.findByUserId(userId);

        Set<Long> companionIds = chats.stream()
                .map(chat -> chat.getUser1Id().equals(userId) ? chat.getUser2Id() : chat.getUser1Id())
                .collect(Collectors.toSet());

        Set<Long> eventIds = chats.stream()
                .map(Chat::getEventId)
                .collect(Collectors.toSet());

        Map<Long, User> usersMap = userRepository.findAllById(companionIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));

        Map<Long, Event> eventsMap = eventRepository.findAllById(eventIds).stream()
                .collect(Collectors.toMap(Event::getId, event -> event));

        return chats.stream()
                .filter(chat -> {
                    // Filter out chats soft-deleted by this user (unless new messages arrived after deletion)
                    LocalDateTime deletedAt = chat.getUser1Id().equals(userId)
                            ? chat.getUser1DeletedAt() : chat.getUser2DeletedAt();
                    if (deletedAt == null) return true;
                    ChatMessage last = chatMessageRepository.findLastMessageByChatId(chat.getId()).orElse(null);
                    return last != null && last.getCreatedAt().isAfter(deletedAt);
                })
                .sorted(Comparator.comparing((Chat chat) -> chat.pinnedAtFor(userId),
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(chat -> {
                    Long companionId = chat.getUser1Id().equals(userId) ? chat.getUser2Id() : chat.getUser1Id();
                    User companion = usersMap.get(companionId);
                    Event event = eventsMap.get(chat.getEventId());

                    ChatMessage lastMessage = chatMessageRepository.findLastMessageByChatId(chat.getId())
                            .orElse(null);

                    long unreadCount = chatMessageRepository.countByChatIdAndSenderIdNotAndIsReadFalse(
                            chat.getId(), userId);

                    return new ChatResponse(
                            chat.getId(),
                            companionId,
                            companion != null ? companion.getFirstName() : null,
                            companion != null ? companion.getPhoto() : null,
                            event != null ? event.getTitle() : null,
                            chat.getEventId(),
                            lastMessage != null ? lastMessage.getText() : null,
                            lastMessage != null ? lastMessage.getCreatedAt() : null,
                            unreadCount,
                            blockStatus(userId, companionId),
                            chat.pinnedAtFor(userId) != null
                    );
                })
                // Pinned chats stay on top (latest pinned first), the rest go by the last message.
                .sorted(Comparator.comparing(ChatResponse::pinned).reversed()
                        .thenComparing(response -> response.pinned() ? null : response.lastMessageTime(),
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true, transactionManager = "transactionManager")
    public Page<ChatMessageResponse> getMessages(Long chatId, Long userId, Pageable pageable) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new EntityNotFoundException("Chat not found with id: " + chatId));

        verifyAccess(chat, userId);

        LocalDateTime deletedAt = chat.getUser1Id().equals(userId)
                ? chat.getUser1DeletedAt() : chat.getUser2DeletedAt();

        Page<ChatMessage> messages = deletedAt != null
                ? chatMessageRepository.findByChatIdAndCreatedAtAfterOrderByCreatedAtAsc(chatId, deletedAt, pageable)
                : chatMessageRepository.findByChatIdOrderByCreatedAtAsc(chatId, pageable);

        return messages.map(message -> new ChatMessageResponse(
                message.getId(),
                message.getChatId(),
                message.getSenderId(),
                message.getText(),
                message.getCreatedAt(),
                message.isRead()
        ));
    }

    @Transactional(transactionManager = "transactionManager")
    public ChatMessageResponse sendMessage(Long chatId, Long senderId, String text) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new EntityNotFoundException("Chat not found with id: " + chatId));

        verifyAccess(chat, senderId);

        Long otherId = chat.getUser1Id().equals(senderId) ? chat.getUser2Id() : chat.getUser1Id();
        String blockStatus = blockStatus(senderId, otherId);
        if ("BLOCKED_BY_ME".equals(blockStatus)) {
            throw new UserActionException("Этот человек в твоём чёрном списке — разблокируй, чтобы написать");
        }
        if ("BLOCKED_ME".equals(blockStatus)) {
            throw new UserActionException("Собеседник ограничил переписку с тобой");
        }

        ChatMessage message = ChatMessage.builder()
                .chatId(chatId)
                .senderId(senderId)
                .text(text)
                .createdAt(LocalDateTime.now())
                .isRead(false)
                .build();

        ChatMessage savedMessage = chatMessageRepository.save(message);

        ChatMessageResponse response = new ChatMessageResponse(
                savedMessage.getId(),
                savedMessage.getChatId(),
                savedMessage.getSenderId(),
                savedMessage.getText(),
                savedMessage.getCreatedAt(),
                savedMessage.isRead()
        );

        Long recipientId = chat.getUser1Id().equals(senderId) ? chat.getUser2Id() : chat.getUser1Id();
        messagingTemplate.convertAndSendToUser(
                recipientId.toString(),
                "/queue/messages",
                response
        );

        // Bot notification to the recipient's messengers (MAX / Telegram)
        try {
            User sender = userRepository.findById(senderId).orElse(null);
            User recipient = userRepository.findById(recipientId).orElse(null);
            messengerNotificationService.sendChatMessageNotification(
                    recipient,
                    sender != null ? sender.getFirstName() : null,
                    text,
                    chat.getId()
            );
        } catch (Exception e) {
            // Don't fail the message send if notification fails
        }

        return response;
    }

    @Transactional(transactionManager = "transactionManager")
    public void markAsRead(Long chatId, Long userId) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new EntityNotFoundException("Chat not found with id: " + chatId));

        verifyAccess(chat, userId);

        chatMessageRepository.markAsRead(chatId, userId);
    }

    @Transactional(transactionManager = "transactionManager")
    public void deleteChat(Long chatId, Long userId) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new EntityNotFoundException("Chat not found with id: " + chatId));

        verifyAccess(chat, userId);

        if (chat.getUser1Id().equals(userId)) {
            chat.setUser1DeletedAt(LocalDateTime.now());
            chat.setUser1PinnedAt(null);
        } else {
            chat.setUser2DeletedAt(LocalDateTime.now());
            chat.setUser2PinnedAt(null);
        }
        chatRepository.save(chat);
    }

    @Transactional(transactionManager = "transactionManager")
    public void setPinned(Long chatId, Long userId, boolean pinned) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new EntityNotFoundException("Chat not found with id: " + chatId));

        verifyAccess(chat, userId);

        LocalDateTime pinnedAt = pinned ? LocalDateTime.now() : null;
        if (chat.getUser1Id().equals(userId)) {
            chat.setUser1PinnedAt(pinnedAt);
        } else {
            chat.setUser2PinnedAt(pinnedAt);
        }
        chatRepository.save(chat);
    }

    private String blockStatus(Long userId, Long companionId) {
        if (userBlockService.hasBlocked(userId, companionId)) return "BLOCKED_BY_ME";
        if (userBlockService.hasBlocked(companionId, userId)) return "BLOCKED_ME";
        return null;
    }

    private void verifyAccess(Chat chat, Long userId) {
        if (!chat.getUser1Id().equals(userId) && !chat.getUser2Id().equals(userId)) {
            throw new AccessDeniedException("User does not have access to this chat");
        }
    }
}
