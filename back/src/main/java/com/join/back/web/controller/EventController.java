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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.join.back.config.ApiDocs;

@Tag(name = ApiDocs.AFISHA)
@RestController
@RequestMapping("/api/events")
public class EventController extends BaseAuthController {

    private final EventService eventService;

    public EventController(UserRepository userRepository, EventService eventService) {
        super(userRepository);
        this.eventService = eventService;
    }

    @Operation(summary = "Лента событий",
            description = "Предстоящие события с фильтрами. Без `city` — город пользователя (или все города, если "
                    + "JOIN его пока не обслуживает).")
    @GetMapping
    public ResponseEntity<Page<EventCardResponse>> getEvents(
            @Parameter(description = "Поиск по названию", example = "Щелкунчик")
            @RequestParam(required = false) String search,
            @Parameter(description = "Цена от, ₽", example = "0")
            @RequestParam(required = false) BigDecimal minPrice,
            @Parameter(description = "Цена до, ₽", example = "1500")
            @RequestParam(required = false) BigDecimal maxPrice,
            @Parameter(description = "Не раньше даты", example = "2026-10-01")
            @RequestParam(required = false) LocalDate dateFrom,
            @Parameter(description = "Не позже даты", example = "2026-10-31")
            @RequestParam(required = false) LocalDate dateTo,
            @Parameter(description = "Категории; можно несколько")
            @RequestParam(required = false) List<EventType> type,
            @Parameter(description = "Теги из GET /api/tags; можно несколько")
            @RequestParam(required = false) List<Long> tagIds,
            @Parameter(description = "Только события, которые можно оплатить Пушкинской картой", example = "true")
            @RequestParam(required = false) Boolean pushkinCard,
            @Parameter(description = "Город; по умолчанию — город пользователя", example = "Казань")
            @RequestParam(required = false) String city,
            @ParameterObject Pageable pageable
    ) {
        EventFilterRequest filter = new EventFilterRequest(search, minPrice, maxPrice, dateFrom, dateTo, type, tagIds,
                pushkinCard, city);
        Long userId = getCurrentUserIdOrNull();
        return ResponseEntity.ok(eventService.getEvents(filter, pageable, userId));
    }

    @Operation(summary = "Подборка по интересам",
            description = "До `limit` событий под интересы пользователя в его городе. Без авторизации — пустой "
                    + "список.")
    @GetMapping("/recommended")
    public ResponseEntity<List<EventCardResponse>> getRecommendedEvents(
            @Parameter(description = "Сколько событий вернуть") @RequestParam(defaultValue = "6") int limit
    ) {
        Long userId = getCurrentUserIdOrNull();
        if (userId == null) {
            return ResponseEntity.ok(Collections.emptyList());
        }
        return ResponseEntity.ok(eventService.getRecommendedEvents(userId, limit));
    }

    @Operation(summary = "Карточка события",
            description = "Описание, цена, ссылка на покупку билета у продавца, отметка Пушкинской карты и сколько "
                    + "человек хотят пойти.")
    @GetMapping("/{id}")
    public ResponseEntity<EventDetailResponse> getEventById(@PathVariable Long id) {
        Long userId = getCurrentUserIdOrNull();
        return ResponseEntity.ok(eventService.getEventById(id, userId));
    }
}
