package com.join.back.service;

import com.join.back.model.dto.EventCardResponse;
import com.join.back.model.dto.LikeResultResponse;
import com.join.back.model.entity.Chat;
import com.join.back.model.entity.Event;
import com.join.back.model.entity.EventLike;
import com.join.back.model.entity.EventType;
import com.join.back.model.entity.Match;
import com.join.back.model.entity.User;
import com.join.back.repository.ChatRepository;
import com.join.back.repository.EventLikeRepository;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LikeServiceTest {

    private static final LocalDate FIXED_DATE = LocalDate.of(2026, 4, 15);
    private static final LocalTime FIXED_TIME = LocalTime.of(19, 0);
    private static final LocalDateTime FIXED_CREATED_AT = LocalDateTime.of(2026, 3, 1, 12, 0);

    @Mock
    private EventLikeRepository eventLikeRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private EventMapper eventMapper;

    @Mock
    private MatchService matchService;

    @Mock
    private ChatRepository chatRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private LikeService likeService;

    @Test
    void likeShouldCreateNewLikeWhenNotExists() {
        when(eventLikeRepository.existsByUserIdAndEventId(1L, 10L)).thenReturn(false);
        when(eventLikeRepository.countByUserIdAndCreatedAtAfter(eq(1L), any(LocalDateTime.class))).thenReturn(0L);

        likeService.like(1L, 10L);

        verify(eventLikeRepository).save(any(EventLike.class));
    }

    @Test
    void likeShouldReturnNewMatchWithChatAndCompanion() {
        when(eventLikeRepository.existsByUserIdAndEventId(1L, 10L)).thenReturn(false);
        when(eventLikeRepository.countByUserIdAndCreatedAtAfter(eq(1L), any(LocalDateTime.class))).thenReturn(0L);
        Match match = Match.builder().id(5L).user1Id(1L).user2Id(2L).eventId(10L).build();
        when(matchService.checkAndCreateMatch(1L, 10L)).thenReturn(List.of(match));
        when(userRepository.findById(2L)).thenReturn(Optional.of(User.builder().id(2L).firstName("Аня").build()));
        when(chatRepository.findByMatchId(5L)).thenReturn(Optional.of(Chat.builder().id(77L).build()));
        when(eventLikeRepository.findByEventId(10L)).thenReturn(List.of(
                EventLike.builder().userId(1L).eventId(10L).build(),
                EventLike.builder().userId(2L).eventId(10L).build(),
                EventLike.builder().userId(3L).eventId(10L).build()));

        LikeResultResponse result = likeService.like(1L, 10L);

        assertEquals(1, result.matches().size());
        assertEquals(77L, result.matches().get(0).chatId());
        assertEquals(2L, result.matches().get(0).companionId());
        assertEquals("Аня", result.matches().get(0).companionName());
        assertEquals(2, result.othersInterested());
    }

    @Test
    void likeWithoutCompanionsShouldReturnNoMatches() {
        when(eventLikeRepository.existsByUserIdAndEventId(1L, 10L)).thenReturn(false);
        when(eventLikeRepository.countByUserIdAndCreatedAtAfter(eq(1L), any(LocalDateTime.class))).thenReturn(0L);
        when(matchService.checkAndCreateMatch(1L, 10L)).thenReturn(List.of());
        when(eventLikeRepository.findByEventId(10L)).thenReturn(List.of(
                EventLike.builder().userId(1L).eventId(10L).build()));

        LikeResultResponse result = likeService.like(1L, 10L);

        assertTrue(result.matches().isEmpty());
        assertEquals(0, result.othersInterested());
    }

    @Test
    void likeShouldDoNothingWhenAlreadyLiked() {
        when(eventLikeRepository.existsByUserIdAndEventId(1L, 10L)).thenReturn(true);

        likeService.like(1L, 10L);

        verify(eventLikeRepository, never()).save(any(EventLike.class));
    }

    @Test
    void likeShouldThrowWhenDailyLimitReached() {
        when(eventLikeRepository.existsByUserIdAndEventId(1L, 10L)).thenReturn(false);
        when(eventLikeRepository.countByUserIdAndCreatedAtAfter(eq(1L), any(LocalDateTime.class)))
                .thenReturn((long) LikeService.DAILY_LIKE_LIMIT);

        assertThrows(IllegalStateException.class, () -> likeService.like(1L, 10L));
        verify(eventLikeRepository, never()).save(any(EventLike.class));
    }

    @Test
    void likeShouldAllowWhenUnderDailyLimit() {
        when(eventLikeRepository.existsByUserIdAndEventId(1L, 10L)).thenReturn(false);
        when(eventLikeRepository.countByUserIdAndCreatedAtAfter(eq(1L), any(LocalDateTime.class)))
                .thenReturn((long) LikeService.DAILY_LIKE_LIMIT - 1);

        likeService.like(1L, 10L);

        verify(eventLikeRepository).save(any(EventLike.class));
    }

    @Test
    void unlikeShouldDeleteLikeAndMatches() {
        likeService.unlike(1L, 10L);

        verify(eventLikeRepository).deleteByUserIdAndEventId(1L, 10L);
        verify(matchService).deleteMatchesForUserAndEvent(1L, 10L);
    }

    @Test
    void isLikedShouldReturnTrueWhenLikeExists() {
        when(eventLikeRepository.existsByUserIdAndEventId(1L, 10L)).thenReturn(true);

        boolean result = likeService.isLiked(1L, 10L);

        assertTrue(result);
    }

    @Test
    void isLikedShouldReturnFalseWhenLikeNotExists() {
        when(eventLikeRepository.existsByUserIdAndEventId(1L, 10L)).thenReturn(false);

        boolean result = likeService.isLiked(1L, 10L);

        assertFalse(result);
    }

    @Test
    void getLikedEventsShouldReturnPageOfEventCardResponses() {
        Pageable pageable = PageRequest.of(0, 10);
        EventLike like = EventLike.builder()
                .id(1L)
                .userId(1L)
                .eventId(10L)
                .createdAt(FIXED_CREATED_AT)
                .build();

        Page<EventLike> likePage = new PageImpl<>(List.of(like), pageable, 1);
        Event event = createTestEvent(10L, "Test Event", EventType.MUSIC);
        EventCardResponse cardResponse = new EventCardResponse(
                10L, "Test Event", EventType.MUSIC, "https://example.com/image.jpg",
                BigDecimal.valueOf(1500), null, null, null, FIXED_DATE, FIXED_TIME, "Moscow", false, false
        , false, 0L);

        when(eventLikeRepository.findByUserId(eq(1L), eq(pageable))).thenReturn(likePage);
        when(eventRepository.findAllById(List.of(10L))).thenReturn(List.of(event));
        when(eventMapper.toCardResponse(event)).thenReturn(cardResponse);

        Page<EventCardResponse> result = likeService.getLikedEvents(1L, pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertTrue(result.getContent().get(0).liked());
    }

    private Event createTestEvent(Long id, String title, EventType type) {
        return Event.builder()
                .id(id)
                .title(title)
                .description("Description")
                .type(type)
                .imageUrl("https://example.com/image.jpg")
                .price(BigDecimal.valueOf(1500))
                .eventDate(FIXED_DATE)
                .eventTime(FIXED_TIME)
                .ticketUrl("https://example.com/tickets")
                .city("Moscow")
                .createdAt(FIXED_CREATED_AT)
                .build();
    }
}
