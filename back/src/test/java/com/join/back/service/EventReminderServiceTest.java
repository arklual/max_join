package com.join.back.service;

import com.join.back.model.entity.Chat;
import com.join.back.model.entity.Event;
import com.join.back.model.entity.User;
import com.join.back.repository.ChatRepository;
import com.join.back.repository.EventRepository;
import com.join.back.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventReminderServiceTest {

    private static final LocalDate TOMORROW = LocalDate.of(2026, 10, 1);

    @Mock
    private EventRepository eventRepository;

    @Mock
    private ChatRepository chatRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MessengerNotificationService messengerNotificationService;

    @InjectMocks
    private EventReminderService eventReminderService;

    private final User anya = User.builder().id(1L).firstName("Аня").maxId(101L).build();
    private final User boris = User.builder().id(2L).firstName("Борис").maxId(102L).build();

    @Test
    void remindsBothCompanionsWithEventTime() {
        Event event = Event.builder().id(10L).title("Щелкунчик").eventDate(TOMORROW).eventTime(LocalTime.of(19, 0)).build();
        when(eventRepository.findByEventDate(TOMORROW)).thenReturn(List.of(event));
        when(chatRepository.findByEventIdIn(Set.of(10L)))
                .thenReturn(List.of(Chat.builder().id(77L).user1Id(1L).user2Id(2L).eventId(10L).build()));
        when(userRepository.findById(1L)).thenReturn(Optional.of(anya));
        when(userRepository.findById(2L)).thenReturn(Optional.of(boris));

        int sent = eventReminderService.sendReminders(TOMORROW);

        assertEquals(2, sent);
        verify(messengerNotificationService).sendEventReminder(anya, "Борис", "Щелкунчик", "завтра в 19:00", 77L);
        verify(messengerNotificationService).sendEventReminder(boris, "Аня", "Щелкунчик", "завтра в 19:00", 77L);
    }

    @Test
    void skipsUserWhoDeletedTheChat() {
        Event event = Event.builder().id(10L).title("Выставка").eventDate(TOMORROW).build();
        when(eventRepository.findByEventDate(TOMORROW)).thenReturn(List.of(event));
        when(chatRepository.findByEventIdIn(Set.of(10L))).thenReturn(List.of(Chat.builder()
                .id(77L).user1Id(1L).user2Id(2L).eventId(10L).user2DeletedAt(LocalDateTime.now()).build()));
        when(userRepository.findById(1L)).thenReturn(Optional.of(anya));
        when(userRepository.findById(2L)).thenReturn(Optional.of(boris));

        int sent = eventReminderService.sendReminders(TOMORROW);

        assertEquals(1, sent);
        verify(messengerNotificationService).sendEventReminder(anya, "Борис", "Выставка", "завтра", 77L);
        verify(messengerNotificationService, never()).sendEventReminder(eq(boris), anyString(), anyString(), anyString(), any());
    }

    @Test
    void doesNothingWithoutEventsTomorrow() {
        when(eventRepository.findByEventDate(TOMORROW)).thenReturn(List.of());

        assertEquals(0, eventReminderService.sendReminders(TOMORROW));
        verifyNoInteractions(chatRepository, messengerNotificationService);
    }
}
