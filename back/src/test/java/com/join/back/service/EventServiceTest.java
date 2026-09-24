package com.join.back.service;

import com.join.back.model.dto.EventCardResponse;
import com.join.back.model.dto.EventDetailResponse;
import com.join.back.model.dto.EventFilterRequest;
import com.join.back.model.entity.Event;
import com.join.back.model.entity.EventType;
import com.join.back.repository.EventLikeRepository;
import com.join.back.repository.EventRepository;
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
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    private static final LocalDate FIXED_DATE = LocalDate.of(2026, 4, 15);
    private static final LocalTime FIXED_TIME = LocalTime.of(19, 0);
    private static final LocalDateTime FIXED_CREATED_AT = LocalDateTime.of(2026, 3, 1, 12, 0);

    @Mock
    private EventRepository eventRepository;

    @Mock
    private EventMapper eventMapper;

    @Mock
    private EventLikeRepository eventLikeRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MatchService matchService;

    @InjectMocks
    private EventService eventService;

    @Test
    void shouldReturnPaginatedEvents() {
        Event event = createTestEvent(1L, "Test Event", EventType.MUSIC);
        Pageable pageable = PageRequest.of(0, 10);
        Page<Event> eventPage = new PageImpl<>(List.of(event), pageable, 1);

        EventCardResponse cardResponse = new EventCardResponse(
                1L, "Test Event", EventType.MUSIC, "https://example.com/image.jpg",
                BigDecimal.valueOf(1500), null, null, null, FIXED_DATE, FIXED_TIME, "Moscow", false, false
        , false);

        EventFilterRequest filter = new EventFilterRequest(null, null, null, null, null, null, null, null);

        when(eventRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(eventPage);
        when(eventMapper.toCardResponse(event)).thenReturn(cardResponse);

        Page<EventCardResponse> result = eventService.getEvents(filter, pageable, null);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("Test Event", result.getContent().get(0).title());
        assertFalse(result.getContent().get(0).liked());
    }

    @Test
    void shouldReturnPaginatedEventsWithLikedFlag() {
        Event event = createTestEvent(1L, "Test Event", EventType.MUSIC);
        Pageable pageable = PageRequest.of(0, 10);
        Page<Event> eventPage = new PageImpl<>(List.of(event), pageable, 1);

        EventCardResponse cardResponse = new EventCardResponse(
                1L, "Test Event", EventType.MUSIC, "https://example.com/image.jpg",
                BigDecimal.valueOf(1500), null, null, null, FIXED_DATE, FIXED_TIME, "Moscow", false, false
        , false);

        EventFilterRequest filter = new EventFilterRequest(null, null, null, null, null, null, null, null);

        when(eventRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(eventPage);
        when(eventMapper.toCardResponse(event)).thenReturn(cardResponse);
        when(eventLikeRepository.findEventIdsByUserId(42L)).thenReturn(Set.of(1L));

        Page<EventCardResponse> result = eventService.getEvents(filter, pageable, 42L);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertTrue(result.getContent().get(0).liked());
    }

    @Test
    void shouldReturnEventById() {
        Event event = createTestEvent(1L, "Test Event", EventType.ART);
        EventDetailResponse detailResponse = new EventDetailResponse(
                1L, "Test Event", "Description", EventType.ART,
                "https://example.com/image.jpg", BigDecimal.valueOf(800), null, null, null,
                FIXED_DATE, FIXED_TIME, "https://example.com/tickets",
                "Moscow", FIXED_CREATED_AT, false, false
        , false);

        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(eventMapper.toDetailResponse(event)).thenReturn(detailResponse);

        EventDetailResponse result = eventService.getEventById(1L, null);

        assertNotNull(result);
        assertEquals("Test Event", result.title());
        assertEquals(EventType.ART, result.type());
    }

    @Test
    void shouldThrowExceptionWhenEventNotFound() {
        when(eventRepository.findById(999L)).thenReturn(Optional.empty());

        EntityNotFoundException exception = assertThrows(
                EntityNotFoundException.class,
                () -> eventService.getEventById(999L, null)
        );

        assertEquals("Event not found with id: 999", exception.getMessage());
    }

    @Test
    void shouldReturnEmptyPageWhenNoEvents() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Event> emptyPage = new PageImpl<>(List.of(), pageable, 0);

        EventFilterRequest filter = new EventFilterRequest(null, null, null, null, null, null, null, null);

        when(eventRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(emptyPage);

        Page<EventCardResponse> result = eventService.getEvents(filter, pageable, null);

        assertNotNull(result);
        assertEquals(0, result.getTotalElements());
    }

    @Test
    void shouldApplySearchFilter() {
        Event event = createTestEvent(1L, "Concert Night", EventType.MUSIC);
        Pageable pageable = PageRequest.of(0, 10);
        Page<Event> eventPage = new PageImpl<>(List.of(event), pageable, 1);

        EventCardResponse cardResponse = new EventCardResponse(
                1L, "Concert Night", EventType.MUSIC, "https://example.com/image.jpg",
                BigDecimal.valueOf(1500), null, null, null, FIXED_DATE, FIXED_TIME, "Moscow", false, false
        , false);

        EventFilterRequest filter = new EventFilterRequest("concert", null, null, null, null, null, null, null);

        when(eventRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(eventPage);
        when(eventMapper.toCardResponse(event)).thenReturn(cardResponse);

        Page<EventCardResponse> result = eventService.getEvents(filter, pageable, null);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("Concert Night", result.getContent().get(0).title());
    }

    @Test
    void shouldApplyPriceRangeFilter() {
        Event event = createTestEvent(1L, "Test Event", EventType.MUSIC);
        Pageable pageable = PageRequest.of(0, 10);
        Page<Event> eventPage = new PageImpl<>(List.of(event), pageable, 1);

        EventCardResponse cardResponse = new EventCardResponse(
                1L, "Test Event", EventType.MUSIC, "https://example.com/image.jpg",
                BigDecimal.valueOf(1500), null, null, null, FIXED_DATE, FIXED_TIME, "Moscow", false, false
        , false);

        EventFilterRequest filter = new EventFilterRequest(null, BigDecimal.valueOf(100), BigDecimal.valueOf(2000), null, null, null, null, null);

        when(eventRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(eventPage);
        when(eventMapper.toCardResponse(event)).thenReturn(cardResponse);

        Page<EventCardResponse> result = eventService.getEvents(filter, pageable, null);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
    }

    @Test
    void shouldApplyTypeFilter() {
        Event event = createTestEvent(1L, "Test Event", EventType.MUSIC);
        Pageable pageable = PageRequest.of(0, 10);
        Page<Event> eventPage = new PageImpl<>(List.of(event), pageable, 1);

        EventCardResponse cardResponse = new EventCardResponse(
                1L, "Test Event", EventType.MUSIC, "https://example.com/image.jpg",
                BigDecimal.valueOf(1500), null, null, null, FIXED_DATE, FIXED_TIME, "Moscow", false, false
        , false);

        EventFilterRequest filter = new EventFilterRequest(null, null, null, null, null, List.of(EventType.MUSIC, EventType.SPORT), null, null);

        when(eventRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(eventPage);
        when(eventMapper.toCardResponse(event)).thenReturn(cardResponse);

        Page<EventCardResponse> result = eventService.getEvents(filter, pageable, null);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
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
