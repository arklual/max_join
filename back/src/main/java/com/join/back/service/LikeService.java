package com.join.back.service;

import com.join.back.model.dto.EventCardResponse;
import com.join.back.model.entity.Event;
import com.join.back.model.entity.EventLike;
import com.join.back.repository.EventLikeRepository;
import com.join.back.repository.EventRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LikeService {

    static final int DAILY_LIKE_LIMIT = 10;

    private final EventLikeRepository eventLikeRepository;
    private final EventRepository eventRepository;
    private final EventMapper eventMapper;
    private final MatchService matchService;

    @Transactional(transactionManager = "transactionManager")
    public void like(Long userId, Long eventId) {
        if (eventLikeRepository.existsByUserIdAndEventId(userId, eventId)) {
            return;
        }

        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        long likesToday = eventLikeRepository.countByUserIdAndCreatedAtAfter(userId, startOfDay);
        if (likesToday >= DAILY_LIKE_LIMIT) {
            throw new IllegalStateException("Daily like limit of " + DAILY_LIKE_LIMIT + " reached");
        }

        EventLike eventLike = EventLike.builder()
                .userId(userId)
                .eventId(eventId)
                .createdAt(LocalDateTime.now())
                .build();

        eventLikeRepository.save(eventLike);

        matchService.checkAndCreateMatch(userId, eventId);
    }

    @Transactional(transactionManager = "transactionManager")
    public void unlike(Long userId, Long eventId) {
        eventLikeRepository.deleteByUserIdAndEventId(userId, eventId);
        matchService.deleteMatchesForUserAndEvent(userId, eventId);
    }

    @Transactional(readOnly = true, transactionManager = "transactionManager")
    public Page<EventCardResponse> getLikedEvents(Long userId, Pageable pageable) {
        Page<EventLike> likePage = eventLikeRepository.findByUserId(userId, pageable);

        List<Long> eventIds = likePage.getContent().stream()
                .map(EventLike::getEventId)
                .collect(Collectors.toList());

        List<Event> events = eventRepository.findAllById(eventIds);

        java.util.Map<Long, Event> eventMap = events.stream()
                .collect(Collectors.toMap(Event::getId, event -> event));

        return likePage.map(like -> {
            Event event = eventMap.get(like.getEventId());
            if (event == null) {
                throw new EntityNotFoundException("Event not found with id: " + like.getEventId());
            }
            EventCardResponse card = eventMapper.toCardResponse(event).withLiked(true);
            if (matchService.hasMatchesForEvent(userId, like.getEventId())) {
                card = card.withHasMatch(true);
            }
            return card;
        });
    }

    @Transactional(readOnly = true, transactionManager = "transactionManager")
    public boolean isLiked(Long userId, Long eventId) {
        return eventLikeRepository.existsByUserIdAndEventId(userId, eventId);
    }
}
