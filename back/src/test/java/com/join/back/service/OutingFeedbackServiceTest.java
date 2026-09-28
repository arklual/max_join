package com.join.back.service;

import com.join.back.model.entity.Chat;
import com.join.back.model.entity.Event;
import com.join.back.model.entity.OutingConfirmation;
import com.join.back.model.entity.OutingResult;
import com.join.back.model.entity.User;
import com.join.back.repository.ChatRepository;
import com.join.back.repository.EventRepository;
import com.join.back.repository.OutingConfirmationRepository;
import com.join.back.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OutingFeedbackServiceTest {

    @Mock private ChatRepository chatRepository;
    @Mock private OutingConfirmationRepository confirmationRepository;
    @Mock private EventRepository eventRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserBlockService userBlockService;
    @Mock private MessengerNotificationService messengerNotificationService;

    @InjectMocks
    private OutingFeedbackService service;

    private final LocalDate today = LocalDate.now(ZoneId.of("Europe/Moscow"));
    private final User anya = User.builder().id(1L).firstName("Аня").build();
    private final User boris = User.builder().id(2L).firstName("Борис").build();
    private final Chat chat = Chat.builder().id(10L).user1Id(1L).user2Id(2L).eventId(5L).build();
    private final Event event = Event.builder().id(5L).title("Щелкунчик").eventDate(today.minusDays(1)).build();
    private final Map<Long, OutingConfirmation> stored = new HashMap<>();

    @BeforeEach
    void setUp() {
        when(chatRepository.findById(10L)).thenReturn(Optional.of(chat));
        when(eventRepository.findById(5L)).thenReturn(Optional.of(event));
        when(userRepository.findById(1L)).thenReturn(Optional.of(anya));
        when(userRepository.findById(2L)).thenReturn(Optional.of(boris));
        when(eventRepository.findByEventDate(today.minusDays(1))).thenReturn(List.of(event));
        when(chatRepository.findByEventIdIn(any())).thenReturn(List.of(chat));
        when(confirmationRepository.findByChatIdAndUserId(anyLong(), anyLong()))
                .thenAnswer(inv -> Optional.ofNullable(stored.get(inv.<Long>getArgument(1))));
        when(confirmationRepository.findByChatId(10L)).thenAnswer(inv -> List.copyOf(stored.values()));
        when(confirmationRepository.save(any())).thenAnswer(inv -> {
            OutingConfirmation c = inv.getArgument(0);
            stored.put(c.getUserId(), c);
            return c;
        });
    }

    @Test
    void asksEveryPairWithAChatOnceTheDayAfter() {
        assertEquals(2, service.askAbout(today.minusDays(1)));
        assertEquals(0, service.askAbout(today.minusDays(1)));
        verify(messengerNotificationService).sendOutingFeedback(anya, "Борис", "Щелкунчик", 10L);
        verify(messengerNotificationService).sendOutingFeedback(boris, "Аня", "Щелкунчик", 10L);
    }

    @Test
    void doesNotAskBlockedPairs() {
        when(userBlockService.hasBlocked(2L, 1L)).thenReturn(true);

        assertEquals(0, service.askAbout(today.minusDays(1)));
        verify(messengerNotificationService, never()).sendOutingFeedback(any(), anyString(), anyString(), anyLong());
    }

    @Test
    void botButtonRecordsTheAnswer() {
        String reply = service.handleButton(anya, "went_10_yes");

        assertTrue(reply.startsWith("🎉"));
        assertEquals(OutingResult.WENT, stored.get(1L).getWent());
        assertEquals("WENT", service.getState(10L, 2L).companionWent());
        assertNull(service.handleButton(anya, "accept_10"));
        assertEquals("Этот чат недоступен.", service.handleButton(User.builder().id(9L).build(), "went_10_no"));
    }

    @Test
    void cannotAnswerBeforeTheEvent() {
        event.setEventDate(today.plusDays(2));
        assertThrows(UserActionException.class, () -> service.answer(10L, 1L, true));
    }
}
