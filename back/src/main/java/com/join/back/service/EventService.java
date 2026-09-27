package com.join.back.service;

import com.join.back.model.dto.EventCardResponse;
import com.join.back.model.dto.EventDetailResponse;
import com.join.back.model.dto.EventFilterRequest;
import com.join.back.model.entity.Event;
import com.join.back.repository.EventLikeRepository;
import com.join.back.repository.EventRepository;
import com.join.back.repository.EventSpecification;
import com.join.back.model.entity.User;
import com.join.back.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final EventMapper eventMapper;
    private final EventLikeRepository eventLikeRepository;
    private final UserRepository userRepository;
    private final MatchService matchService;
    private final CityScope cityScope;

    @Transactional(readOnly = true, transactionManager = "transactionManager")
    public Page<EventCardResponse> getEvents(EventFilterRequest filter, Pageable pageable, Long userId) {
        Specification<Event> spec = buildSpecification(filter);
        String city = cityScope.resolve(filter.city(), userId);
        if (city != null) {
            spec = spec.and(EventSpecification.inCity(city));
        }

        Page<Event> eventPage = eventRepository.findAll(spec, pageable);

        Set<Long> likedEventIds = Set.of();
        if (userId != null) {
            likedEventIds = eventLikeRepository.findEventIdsByUserId(userId);
        }

        Set<Long> finalLikedEventIds = likedEventIds;
        Map<Long, Long> likeCounts = likeCounts(eventPage.getContent());
        return eventPage.map(event -> {
            EventCardResponse response = eventMapper.toCardResponse(event)
                    .withInterestedCount(othersInterested(likeCounts, event.getId(), finalLikedEventIds));
            if (finalLikedEventIds.contains(event.getId())) {
                response = response.withLiked(true);
                if (userId != null && matchService.hasMatchesForEvent(userId, event.getId())) {
                    response = response.withHasMatch(true);
                }
            }
            return response;
        });
    }

    @Transactional(readOnly = true, transactionManager = "transactionManager")
    public EventDetailResponse getEventById(Long id, Long userId) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Event not found with id: " + id));
        EventDetailResponse response = eventMapper.toDetailResponse(event);
        long likes = eventLikeRepository.countByEventId(id);
        if (userId != null) {
            if (eventLikeRepository.existsByUserIdAndEventId(userId, id)) {
                response = response.withLiked(true);
                likes = Math.max(0, likes - 1);
            }
            if (matchService.hasMatchesForEvent(userId, id)) {
                response = response.withHasMatch(true);
            }
        }
        return response.withInterestedCount(likes);
    }

    /** Like counts per event id for one page of events. */
    private Map<Long, Long> likeCounts(List<Event> events) {
        if (events.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> counts = new HashMap<>();
        List<Long> ids = events.stream().map(Event::getId).toList();
        for (Object[] row : eventLikeRepository.countByEventIds(ids)) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    /** Likes by other people: the viewer's own like is not "someone wants to go". */
    private static long othersInterested(Map<Long, Long> likeCounts, Long eventId, Set<Long> likedByViewer) {
        long total = likeCounts.getOrDefault(eventId, 0L);
        return likedByViewer.contains(eventId) ? Math.max(0, total - 1) : total;
    }

    @Transactional(readOnly = true, transactionManager = "transactionManager")
    public List<EventCardResponse> getRecommendedEvents(Long userId, int limit) {
        if (userId == null) {
            return List.of();
        }

        Set<Long> likedEventIds = eventLikeRepository.findEventIdsByUserId(userId);
        Set<Long> tagIds = eventLikeRepository.findTagIdsByUserId(userId);
        String city = cityScope.resolve(null, userId);

        // If user has no likes, fall back to their interest-based types
        if (tagIds.isEmpty() && likedEventIds.isEmpty()) {
            User user = userRepository.findById(userId).orElse(null);
            if (user != null && user.getInterests() != null && !user.getInterests().isEmpty()) {
                Specification<Event> spec = Specification.where(EventSpecification.futureOrToday())
                        .and(EventSpecification.typeIn(user.getInterests()));
                if (city != null) {
                    spec = spec.and(EventSpecification.inCity(city));
                }
                Pageable pageable = PageRequest.of(0, limit, Sort.by("eventDate").ascending());
                return eventRepository.findAll(spec, pageable).getContent().stream()
                        .map(event -> eventMapper.toCardResponse(event))
                        .collect(Collectors.toList());
            }
            return List.of();
        }

        // Find future events with matching tags, excluding already liked events
        Specification<Event> spec = Specification.where(EventSpecification.futureOrToday());
        if (city != null) {
            spec = spec.and(EventSpecification.inCity(city));
        }
        if (!tagIds.isEmpty()) {
            spec = spec.and(EventSpecification.hasTagIds(tagIds.stream().toList()));
        }

        Pageable pageable = PageRequest.of(0, limit, Sort.by("eventDate").ascending());
        Page<Event> eventPage = eventRepository.findAll(spec, pageable);

        return eventPage.getContent().stream()
                .filter(event -> !likedEventIds.contains(event.getId()))
                .map(eventMapper::toCardResponse)
                .limit(limit)
                .collect(Collectors.toList());
    }

    private Specification<Event> buildSpecification(EventFilterRequest filter) {
        Specification<Event> spec = Specification.where(null);

        if (filter.search() != null && !filter.search().isBlank()) {
            spec = spec.and(EventSpecification.titleOrTagsContainKeywords(filter.search()));
        }
        if (filter.minPrice() != null) {
            spec = spec.and(EventSpecification.priceGreaterThanOrEqual(filter.minPrice()));
        }
        if (filter.maxPrice() != null) {
            spec = spec.and(EventSpecification.priceLessThanOrEqual(filter.maxPrice()));
        }
        if (filter.dateFrom() != null) {
            spec = spec.and(EventSpecification.dateFrom(filter.dateFrom()));
        } else {
            // By default, show only future (and today's) events
            spec = spec.and(EventSpecification.futureOrToday());
        }
        if (filter.dateTo() != null) {
            spec = spec.and(EventSpecification.dateTo(filter.dateTo()));
        }
        if (filter.type() != null && !filter.type().isEmpty()) {
            spec = spec.and(EventSpecification.typeIn(filter.type()));
        }
        if (filter.tagIds() != null && !filter.tagIds().isEmpty()) {
            spec = spec.and(EventSpecification.hasTagIds(filter.tagIds()));
        }
        if (Boolean.TRUE.equals(filter.pushkinCard())) {
            spec = spec.and(EventSpecification.pushkinCard());
        }

        return spec;
    }
}
