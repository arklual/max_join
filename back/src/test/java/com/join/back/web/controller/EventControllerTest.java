package com.join.back.web.controller;

import com.join.back.model.dto.EventCardResponse;
import com.join.back.model.dto.EventDetailResponse;
import com.join.back.model.dto.EventFilterRequest;
import com.join.back.model.entity.EventType;
import com.join.back.repository.UserRepository;
import com.join.back.service.EventService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = EventController.class,
        excludeAutoConfiguration = {org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class})
@ActiveProfiles("test")
class EventControllerTest {

    private static final LocalDate FIXED_DATE = LocalDate.of(2026, 4, 15);
    private static final LocalTime FIXED_TIME = LocalTime.of(19, 0);
    private static final LocalDateTime FIXED_CREATED_AT = LocalDateTime.of(2026, 3, 1, 12, 0);

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EventService eventService;

    @MockBean
    private UserRepository userRepository;

    @Test
    void shouldReturnPaginatedEvents() throws Exception {
        EventCardResponse cardResponse = new EventCardResponse(
                1L, "Test Event", EventType.MUSIC, "https://example.com/image.jpg",
                BigDecimal.valueOf(1500), null, null, null, FIXED_DATE, FIXED_TIME, "Moscow", false, false
        , false, 0L);
        Page<EventCardResponse> page = new PageImpl<>(List.of(cardResponse), PageRequest.of(0, 10), 1);

        when(eventService.getEvents(any(EventFilterRequest.class), any(Pageable.class), any())).thenReturn(page);

        mockMvc.perform(get("/api/events")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Test Event"))
                .andExpect(jsonPath("$.content[0].type").value("MUSIC"))
                .andExpect(jsonPath("$.content[0].city").value("Moscow"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void shouldReturnEventById() throws Exception {
        EventDetailResponse detailResponse = new EventDetailResponse(
                1L, "Test Event", "Event description", EventType.MUSIC,
                "https://example.com/image.jpg", BigDecimal.valueOf(1500), null, null, null,
                FIXED_DATE, FIXED_TIME, "https://example.com/tickets",
                "Moscow", FIXED_CREATED_AT, false, false
        , false, 0L);

        when(eventService.getEventById(eq(1L), any())).thenReturn(detailResponse);

        mockMvc.perform(get("/api/events/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("Test Event"))
                .andExpect(jsonPath("$.description").value("Event description"))
                .andExpect(jsonPath("$.type").value("MUSIC"))
                .andExpect(jsonPath("$.city").value("Moscow"));
    }

    @Test
    void shouldReturn404WhenEventNotFound() throws Exception {
        when(eventService.getEventById(eq(999L), any())).thenThrow(new EntityNotFoundException("Event not found with id: 999"));

        mockMvc.perform(get("/api/events/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Event not found with id: 999"));
    }

    @Test
    void shouldReturnEventsWithSearchFilter() throws Exception {
        EventCardResponse cardResponse = new EventCardResponse(
                1L, "Concert Night", EventType.MUSIC, "https://example.com/image.jpg",
                BigDecimal.valueOf(1500), null, null, null, FIXED_DATE, FIXED_TIME, "Moscow", false, false
        , false, 0L);
        Page<EventCardResponse> page = new PageImpl<>(List.of(cardResponse), PageRequest.of(0, 10), 1);

        when(eventService.getEvents(any(EventFilterRequest.class), any(Pageable.class), any())).thenReturn(page);

        mockMvc.perform(get("/api/events")
                        .param("search", "concert")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Concert Night"));
    }

    @Test
    void shouldReturnEventsWithTypeFilter() throws Exception {
        EventCardResponse cardResponse = new EventCardResponse(
                1L, "Test Event", EventType.MUSIC, "https://example.com/image.jpg",
                BigDecimal.valueOf(1500), null, null, null, FIXED_DATE, FIXED_TIME, "Moscow", false, false
        , false, 0L);
        Page<EventCardResponse> page = new PageImpl<>(List.of(cardResponse), PageRequest.of(0, 10), 1);

        when(eventService.getEvents(any(EventFilterRequest.class), any(Pageable.class), any())).thenReturn(page);

        mockMvc.perform(get("/api/events")
                        .param("type", "MUSIC")
                        .param("type", "SPORT")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].type").value("MUSIC"));
    }

    @Test
    void shouldReturnEmptyPageWhenNoEvents() throws Exception {
        Page<EventCardResponse> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);

        when(eventService.getEvents(any(EventFilterRequest.class), any(Pageable.class), any())).thenReturn(emptyPage);

        mockMvc.perform(get("/api/events")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
    }
}
