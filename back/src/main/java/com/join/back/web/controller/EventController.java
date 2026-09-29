package com.join.back.web.controller;

import com.join.back.model.dto.EventCardResponse;
import com.join.back.model.dto.EventDetailResponse;
import com.join.back.model.dto.EventFilterRequest;
import com.join.back.model.entity.EventType;
import com.join.back.repository.UserRepository;
import com.join.back.service.EventService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import org.springdoc.core.annotations.ParameterObject;

@RestController
@RequestMapping("/api/events")
public class EventController extends BaseAuthController {

    private final EventService eventService;

    public EventController(UserRepository userRepository, EventService eventService) {
        super(userRepository);
        this.eventService = eventService;
    }

    @GetMapping
    public ResponseEntity<Page<EventCardResponse>> getEvents(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo,
            @RequestParam(required = false) List<EventType> type,
            @RequestParam(required = false) List<Long> tagIds,
            @RequestParam(required = false) Boolean pushkinCard,
            @RequestParam(required = false) String city,
            @ParameterObject Pageable pageable
    ) {
        EventFilterRequest filter = new EventFilterRequest(search, minPrice, maxPrice, dateFrom, dateTo, type, tagIds,
                pushkinCard, city);
        Long userId = getCurrentUserIdOrNull();
        return ResponseEntity.ok(eventService.getEvents(filter, pageable, userId));
    }

    @GetMapping("/recommended")
    public ResponseEntity<List<EventCardResponse>> getRecommendedEvents(
            @RequestParam(defaultValue = "6") int limit
    ) {
        Long userId = getCurrentUserIdOrNull();
        if (userId == null) {
            return ResponseEntity.ok(Collections.emptyList());
        }
        return ResponseEntity.ok(eventService.getRecommendedEvents(userId, limit));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EventDetailResponse> getEventById(@PathVariable Long id) {
        Long userId = getCurrentUserIdOrNull();
        return ResponseEntity.ok(eventService.getEventById(id, userId));
    }
}
