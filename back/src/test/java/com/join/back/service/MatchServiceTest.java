package com.join.back.service;

import com.join.back.model.entity.Chat;
import com.join.back.model.entity.Event;
import com.join.back.model.entity.EventLike;
import com.join.back.model.entity.EventType;
import com.join.back.model.entity.Gender;
import com.join.back.model.entity.Match;
import com.join.back.model.entity.Notification;
import com.join.back.model.entity.User;
import com.join.back.repository.ChatMessageRepository;
import com.join.back.repository.ChatRepository;
import com.join.back.repository.EventLikeRepository;
import com.join.back.repository.EventRepository;
import com.join.back.repository.MatchRepository;
import com.join.back.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchServiceTest {

    private static final LocalDateTime FIXED_CREATED_AT = LocalDateTime.of(2026, 3, 1, 12, 0);

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private EventLikeRepository eventLikeRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private ChatRepository chatRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private ChatService chatService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private MessengerNotificationService messengerNotificationService;

    @InjectMocks
    private MatchService matchService;

    @Test
    void shouldCreateMatchWhenMutualLikeAndMutualCriteriaMatch() {
        Long userId1 = 1L;
        Long userId2 = 2L;
        Long eventId = 10L;

        User user1 = createUser(userId1, 25, Gender.MALE, 20, 30, Gender.FEMALE);
        User user2 = createUser(userId2, 22, Gender.FEMALE, 20, 30, Gender.MALE);
        Event event = createEvent(eventId, "Test Concert");

        EventLike like1 = EventLike.builder().userId(userId1).eventId(eventId).createdAt(FIXED_CREATED_AT).build();
        EventLike like2 = EventLike.builder().userId(userId2).eventId(eventId).createdAt(FIXED_CREATED_AT).build();

        when(eventLikeRepository.findByEventId(eventId)).thenReturn(List.of(like1, like2));
        when(userRepository.findById(userId1)).thenReturn(Optional.of(user1));
        when(userRepository.findAllById(List.of(userId2))).thenReturn(List.of(user2));
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(matchRepository.existsByUsersAndEvent(userId1, userId2, eventId)).thenReturn(false);

        Match savedMatch = Match.builder().id(100L).user1Id(userId1).user2Id(userId2).eventId(eventId)
                .createdAt(FIXED_CREATED_AT).build();
        when(matchRepository.save(any(Match.class))).thenReturn(savedMatch);

        when(chatService.createChat(eq(100L), eq(userId1), eq(userId2), eq(eventId)))
                .thenReturn(Chat.builder().id(1L).build());
        when(notificationService.createMatchNotification(anyLong(), anyLong(), anyString(), anyString()))
                .thenReturn(Notification.builder().id(1L).build());

        List<Match> result = matchService.checkAndCreateMatch(userId1, eventId);

        assertEquals(1, result.size());
        verify(matchRepository).save(any(Match.class));
        verify(chatService).createChat(eq(100L), eq(userId1), eq(userId2), eq(eventId));
        // Both users should receive a notification
        verify(notificationService, times(2)).createMatchNotification(anyLong(), eq(100L), anyString(), anyString());
    }

    @Test
    void shouldNotCreateMatchWhenAgeOutOfRange() {
        Long userId1 = 1L;
        Long userId2 = 2L;
        Long eventId = 10L;

        User user1 = createUser(userId1, 40, Gender.MALE, 20, 30, Gender.FEMALE);
        User user2 = createUser(userId2, 22, Gender.FEMALE, 20, 30, Gender.MALE);
        Event event = createEvent(eventId, "Test Concert");

        EventLike like1 = EventLike.builder().userId(userId1).eventId(eventId).createdAt(FIXED_CREATED_AT).build();
        EventLike like2 = EventLike.builder().userId(userId2).eventId(eventId).createdAt(FIXED_CREATED_AT).build();

        when(eventLikeRepository.findByEventId(eventId)).thenReturn(List.of(like1, like2));
        when(userRepository.findById(userId1)).thenReturn(Optional.of(user1));
        when(userRepository.findAllById(List.of(userId2))).thenReturn(List.of(user2));
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(matchRepository.existsByUsersAndEvent(userId1, userId2, eventId)).thenReturn(false);

        List<Match> result = matchService.checkAndCreateMatch(userId1, eventId);

        assertTrue(result.isEmpty());
        verify(matchRepository, never()).save(any(Match.class));
    }

    @Test
    void shouldNotCreateMatchWhenGenderMismatch() {
        Long userId1 = 1L;
        Long userId2 = 2L;
        Long eventId = 10L;

        User user1 = createUser(userId1, 25, Gender.MALE, 20, 30, Gender.FEMALE);
        User user2 = createUser(userId2, 22, Gender.MALE, 20, 30, Gender.FEMALE);
        Event event = createEvent(eventId, "Test Concert");

        EventLike like1 = EventLike.builder().userId(userId1).eventId(eventId).createdAt(FIXED_CREATED_AT).build();
        EventLike like2 = EventLike.builder().userId(userId2).eventId(eventId).createdAt(FIXED_CREATED_AT).build();

        when(eventLikeRepository.findByEventId(eventId)).thenReturn(List.of(like1, like2));
        when(userRepository.findById(userId1)).thenReturn(Optional.of(user1));
        when(userRepository.findAllById(List.of(userId2))).thenReturn(List.of(user2));
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(matchRepository.existsByUsersAndEvent(userId1, userId2, eventId)).thenReturn(false);

        List<Match> result = matchService.checkAndCreateMatch(userId1, eventId);

        assertTrue(result.isEmpty());
        verify(matchRepository, never()).save(any(Match.class));
    }

    @Test
    void shouldNotCreateDuplicateMatch() {
        Long userId1 = 1L;
        Long userId2 = 2L;
        Long eventId = 10L;

        User user1 = createUser(userId1, 25, Gender.MALE, 20, 30, Gender.FEMALE);
        User user2 = createUser(userId2, 22, Gender.FEMALE, 20, 30, Gender.MALE);
        Event event = createEvent(eventId, "Test Concert");

        EventLike like1 = EventLike.builder().userId(userId1).eventId(eventId).createdAt(FIXED_CREATED_AT).build();
        EventLike like2 = EventLike.builder().userId(userId2).eventId(eventId).createdAt(FIXED_CREATED_AT).build();

        when(eventLikeRepository.findByEventId(eventId)).thenReturn(List.of(like1, like2));
        when(userRepository.findById(userId1)).thenReturn(Optional.of(user1));
        when(userRepository.findAllById(List.of(userId2))).thenReturn(List.of(user2));
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(matchRepository.existsByUsersAndEvent(userId1, userId2, eventId)).thenReturn(true);

        List<Match> result = matchService.checkAndCreateMatch(userId1, eventId);

        assertTrue(result.isEmpty());
        verify(matchRepository, never()).save(any(Match.class));
    }

    @Test
    void shouldMatchWhenPreferredGenderIsNull() {
        Long userId1 = 1L;
        Long userId2 = 2L;
        Long eventId = 10L;

        User user1 = createUser(userId1, 25, Gender.MALE, 20, 30, null);
        User user2 = createUser(userId2, 22, Gender.FEMALE, 20, 30, null);
        Event event = createEvent(eventId, "Test Concert");

        EventLike like1 = EventLike.builder().userId(userId1).eventId(eventId).createdAt(FIXED_CREATED_AT).build();
        EventLike like2 = EventLike.builder().userId(userId2).eventId(eventId).createdAt(FIXED_CREATED_AT).build();

        when(eventLikeRepository.findByEventId(eventId)).thenReturn(List.of(like1, like2));
        when(userRepository.findById(userId1)).thenReturn(Optional.of(user1));
        when(userRepository.findAllById(List.of(userId2))).thenReturn(List.of(user2));
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(matchRepository.existsByUsersAndEvent(userId1, userId2, eventId)).thenReturn(false);

        Match savedMatch = Match.builder().id(100L).user1Id(userId1).user2Id(userId2).eventId(eventId)
                .createdAt(FIXED_CREATED_AT).build();
        when(matchRepository.save(any(Match.class))).thenReturn(savedMatch);

        when(chatService.createChat(eq(100L), eq(userId1), eq(userId2), eq(eventId)))
                .thenReturn(Chat.builder().id(1L).build());
        when(notificationService.createMatchNotification(anyLong(), anyLong(), anyString(), anyString()))
                .thenReturn(Notification.builder().id(1L).build());

        List<Match> result = matchService.checkAndCreateMatch(userId1, eventId);

        assertEquals(1, result.size());
        verify(matchRepository).save(any(Match.class));
    }

    @Test
    void deleteMatchesForUserAndEventShouldDeleteExistingMatchesAndChats() {
        Long userId = 1L;
        Long eventId = 10L;

        Match match = Match.builder().id(100L).user1Id(userId).user2Id(2L).eventId(eventId)
                .createdAt(FIXED_CREATED_AT).build();

        Chat chat = Chat.builder().id(50L).matchId(100L).user1Id(userId).user2Id(2L).eventId(eventId)
                .createdAt(FIXED_CREATED_AT).build();

        when(matchRepository.findByUserIdAndEventId(userId, eventId)).thenReturn(List.of(match));
        when(chatRepository.findByMatchIdIn(List.of(100L))).thenReturn(List.of(chat));

        matchService.deleteMatchesForUserAndEvent(userId, eventId);

        verify(chatMessageRepository).deleteByChatIdIn(List.of(50L));
        verify(chatRepository).deleteAll(List.of(chat));
        verify(matchRepository).deleteAll(List.of(match));
    }

    @Test
    void deleteMatchesForUserAndEventShouldDoNothingWhenNoMatches() {
        Long userId = 1L;
        Long eventId = 10L;

        when(matchRepository.findByUserIdAndEventId(userId, eventId)).thenReturn(List.of());

        matchService.deleteMatchesForUserAndEvent(userId, eventId);

        verify(matchRepository, never()).deleteAll(any());
    }

    @Test
    void isMutualCriteriaMatchShouldReturnTrueWhenBothFit() {
        User userA = createUser(1L, 25, Gender.MALE, 20, 30, Gender.FEMALE);
        User userB = createUser(2L, 22, Gender.FEMALE, 20, 30, Gender.MALE);

        assertTrue(matchService.isMutualCriteriaMatch(userA, userB));
    }

    @Test
    void isMutualCriteriaMatchShouldReturnFalseWhenOneSideDoesNotFit() {
        User userA = createUser(1L, 25, Gender.MALE, 20, 30, Gender.FEMALE);
        User userB = createUser(2L, 22, Gender.FEMALE, 30, 40, Gender.MALE);

        assertFalse(matchService.isMutualCriteriaMatch(userA, userB));
    }

    private User createUser(Long id, Integer age, Gender gender,
                            Integer preferredAgeMin, Integer preferredAgeMax, Gender preferredGender) {
        return User.builder()
                .id(id)
                .maxId(id * 1000)
                .firstName("User" + id)
                .age(age)
                .gender(gender)
                .preferredAgeMin(preferredAgeMin)
                .preferredAgeMax(preferredAgeMax)
                .preferredGender(preferredGender)
                .createdAt(FIXED_CREATED_AT)
                .build();
    }

    private Event createEvent(Long id, String title) {
        return Event.builder()
                .id(id)
                .title(title)
                .type(EventType.MUSIC)
                .eventDate(LocalDate.of(2026, 4, 15))
                .eventTime(LocalTime.of(19, 0))
                .city("Moscow")
                .createdAt(FIXED_CREATED_AT)
                .build();
    }
}
