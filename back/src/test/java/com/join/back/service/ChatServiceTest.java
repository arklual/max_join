package com.join.back.service;

import com.join.back.model.dto.ChatMessageResponse;
import com.join.back.model.dto.ChatResponse;
import com.join.back.model.entity.Chat;
import com.join.back.model.entity.ChatMessage;
import com.join.back.model.entity.Event;
import com.join.back.model.entity.EventType;
import com.join.back.model.entity.User;
import com.join.back.repository.ChatMessageRepository;
import com.join.back.repository.ChatRepository;
import com.join.back.repository.EventRepository;
import com.join.back.repository.UserRepository;
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
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
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
class ChatServiceTest {

    private static final LocalDateTime FIXED_CREATED_AT = LocalDateTime.of(2026, 3, 1, 12, 0);
    private static final LocalDate FIXED_DATE = LocalDate.of(2026, 4, 15);

    @Mock
    private ChatRepository chatRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private UserBlockService userBlockService;

    @InjectMocks
    private ChatService chatService;

    @Test
    void shouldCreateChat() {
        Chat chat = Chat.builder()
                .id(1L)
                .matchId(100L)
                .user1Id(1L)
                .user2Id(2L)
                .eventId(10L)
                .createdAt(FIXED_CREATED_AT)
                .build();

        when(chatRepository.save(any(Chat.class))).thenReturn(chat);

        Chat result = chatService.createChat(100L, 1L, 2L, 10L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        verify(chatRepository).save(any(Chat.class));
    }

    @Test
    void shouldReturnChatsForUser() {
        Long userId = 1L;

        Chat chat = Chat.builder()
                .id(1L)
                .user1Id(1L)
                .user2Id(2L)
                .eventId(10L)
                .matchId(100L)
                .createdAt(FIXED_CREATED_AT)
                .build();

        User companion = User.builder()
                .id(2L)
                .firstName("Jane")
                .photo("/uploads/photos/jane.jpg")
                .createdAt(FIXED_CREATED_AT)
                .build();

        Event event = Event.builder()
                .id(10L)
                .title("Concert")
                .type(EventType.MUSIC)
                .eventDate(FIXED_DATE)
                .city("Moscow")
                .createdAt(FIXED_CREATED_AT)
                .build();

        when(chatRepository.findByUserId(userId)).thenReturn(List.of(chat));
        when(userRepository.findAllById(any())).thenReturn(List.of(companion));
        when(eventRepository.findAllById(any())).thenReturn(List.of(event));
        when(chatMessageRepository.findLastMessageByChatId(1L)).thenReturn(Optional.empty());
        when(chatMessageRepository.countByChatIdAndSenderIdNotAndIsReadFalse(1L, userId)).thenReturn(0L);

        List<ChatResponse> result = chatService.getChats(userId);

        assertEquals(1, result.size());
        assertEquals("Jane", result.get(0).companionName());
        assertEquals("Concert", result.get(0).eventTitle());
    }

    @Test
    void blockedUserCannotWriteToBlocker() {
        Chat chat = Chat.builder().id(1L).user1Id(1L).user2Id(2L).eventId(10L).matchId(100L)
                .createdAt(FIXED_CREATED_AT).build();
        when(chatRepository.findById(1L)).thenReturn(Optional.of(chat));
        org.mockito.Mockito.lenient().when(userBlockService.hasBlocked(1L, 2L)).thenReturn(true);

        org.junit.jupiter.api.Assertions.assertThrows(UserActionException.class,
                () -> chatService.sendMessage(1L, 2L, "Привет"));
        org.mockito.Mockito.verify(chatMessageRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void shouldSendAndReceiveMessages() {
        Long chatId = 1L;
        Long senderId = 1L;

        Chat chat = Chat.builder()
                .id(chatId)
                .user1Id(1L)
                .user2Id(2L)
                .eventId(10L)
                .matchId(100L)
                .createdAt(FIXED_CREATED_AT)
                .build();

        ChatMessage savedMessage = ChatMessage.builder()
                .id(1L)
                .chatId(chatId)
                .senderId(senderId)
                .text("Hello!")
                .createdAt(FIXED_CREATED_AT)
                .isRead(false)
                .build();

        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(chatMessageRepository.save(any(ChatMessage.class))).thenReturn(savedMessage);

        ChatMessageResponse result = chatService.sendMessage(chatId, senderId, "Hello!");

        assertNotNull(result);
        assertEquals("Hello!", result.text());
        assertEquals(senderId, result.senderId());

        verify(messagingTemplate).convertAndSendToUser(
                eq("2"),
                eq("/queue/messages"),
                any(ChatMessageResponse.class)
        );
    }

    @Test
    void shouldThrowAccessDeniedWhenUserNotInChat() {
        Long chatId = 1L;
        Long intruderId = 99L;

        Chat chat = Chat.builder()
                .id(chatId)
                .user1Id(1L)
                .user2Id(2L)
                .eventId(10L)
                .matchId(100L)
                .createdAt(FIXED_CREATED_AT)
                .build();

        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));

        assertThrows(AccessDeniedException.class, () ->
                chatService.sendMessage(chatId, intruderId, "Intruder!"));
    }

    @Test
    void shouldMarkMessagesAsRead() {
        Long chatId = 1L;
        Long userId = 1L;

        Chat chat = Chat.builder()
                .id(chatId)
                .user1Id(1L)
                .user2Id(2L)
                .eventId(10L)
                .matchId(100L)
                .createdAt(FIXED_CREATED_AT)
                .build();

        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));

        chatService.markAsRead(chatId, userId);

        verify(chatMessageRepository).markAsRead(chatId, userId);
    }

    @Test
    void shouldReturnMessagesWithPagination() {
        Long chatId = 1L;
        Long userId = 1L;
        Pageable pageable = PageRequest.of(0, 20);

        Chat chat = Chat.builder()
                .id(chatId)
                .user1Id(1L)
                .user2Id(2L)
                .eventId(10L)
                .matchId(100L)
                .createdAt(FIXED_CREATED_AT)
                .build();

        ChatMessage message = ChatMessage.builder()
                .id(1L)
                .chatId(chatId)
                .senderId(2L)
                .text("Hi there")
                .createdAt(FIXED_CREATED_AT)
                .isRead(false)
                .build();

        Page<ChatMessage> messagePage = new PageImpl<>(List.of(message), pageable, 1);

        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(chatMessageRepository.findByChatIdOrderByCreatedAtAsc(chatId, pageable)).thenReturn(messagePage);

        Page<ChatMessageResponse> result = chatService.getMessages(chatId, userId, pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals("Hi there", result.getContent().get(0).text());
    }
}
