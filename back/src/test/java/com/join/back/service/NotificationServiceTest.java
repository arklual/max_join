package com.join.back.service;

import com.join.back.model.dto.NotificationResponse;
import com.join.back.model.entity.Notification;
import com.join.back.model.entity.NotificationType;
import com.join.back.repository.ChatRepository;
import com.join.back.repository.EventRepository;
import com.join.back.repository.MatchRepository;
import com.join.back.repository.NotificationRepository;
import com.join.back.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 3, 21, 14, 0);

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private ChatRepository chatRepository;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    void createMatchNotificationShouldSaveAndReturnNotification() {
        Long userId = 1L;
        Long matchId = 100L;

        Notification saved = Notification.builder()
                .id(1L)
                .userId(userId)
                .type(NotificationType.MATCH)
                .title("Новый метч!")
                .message("Вы совпали с Alice на мероприятии «Test Concert»")
                .matchId(matchId)
                .read(false)
                .createdAt(FIXED_NOW)
                .build();

        when(notificationRepository.save(any(Notification.class))).thenReturn(saved);

        Notification result = notificationService.createMatchNotification(userId, matchId, "Alice", "Test Concert");

        assertNotNull(result);
        assertEquals(userId, result.getUserId());
        assertEquals(NotificationType.MATCH, result.getType());
        assertFalse(result.isRead());

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification captured = captor.getValue();
        assertEquals(matchId, captured.getMatchId());
        assertEquals("Нашлась компания", captured.getTitle());
    }

    @Test
    void getNotificationsShouldReturnPageOfResponses() {
        Long userId = 1L;
        Pageable pageable = PageRequest.of(0, 10);

        Notification notification = Notification.builder()
                .id(1L)
                .userId(userId)
                .type(NotificationType.MATCH)
                .title("New match!")
                .message("You matched with Bob at event \"Rock Fest\"")
                .matchId(200L)
                .read(false)
                .createdAt(FIXED_NOW)
                .build();

        Page<Notification> page = new PageImpl<>(List.of(notification), pageable, 1);
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)).thenReturn(page);

        Page<NotificationResponse> result = notificationService.getNotifications(userId, pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        NotificationResponse response = result.getContent().get(0);
        assertEquals(1L, response.id());
        assertEquals(NotificationType.MATCH, response.type());
        assertFalse(response.read());
    }

    @Test
    void countUnreadShouldReturnCorrectCount() {
        when(notificationRepository.countByUserIdAndReadFalse(1L)).thenReturn(3L);

        long count = notificationService.countUnread(1L);

        assertEquals(3L, count);
    }

    @Test
    void markAllReadShouldCallRepository() {
        notificationService.markAllRead(1L);

        verify(notificationRepository).markAllReadByUserId(1L);
    }
}
