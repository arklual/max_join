package com.join.back.service;

import com.join.back.model.dto.EventCardResponse;
import com.join.back.model.dto.LikeResultResponse;
import com.join.back.model.entity.Event;
import com.join.back.model.entity.EventLike;
import com.join.back.model.entity.Match;
import com.join.back.model.entity.User;
import com.join.back.repository.ChatRepository;
import com.join.back.repository.EventLikeRepository;
import com.join.back.repository.EventRepository;
import com.join.back.repository.UserRepository;
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
    private final ChatRepository chatRepository;
    private final UserRepository userRepository;

    @Transactional(transactionManager = "transactionManager")
    public LikeResultResponse like(Long userId, Long eventId) {
        if (eventLikeRepository.existsByUserIdAndEventId(userId, eventId)) {
            return new LikeResultResponse(List.of(), countOthersInterested(userId, eventId));
        }

        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        long likesToday = eventLikeRepository.countByUserIdAndCreatedAtAfter(userId, startOfDay);
        if (likesToday >= DAILY_LIKE_LIMIT) {
            throw new UserActionException("Сегодня можно лайкнуть не больше " + DAILY_LIKE_LIMIT + " событий — возвращайся завтра");
        }

        EventLike eventLike = EventLike.builder()
                .userId(userId)
                .eventId(eventId)
                .createdAt(LocalDateTime.now())
                .build();

        eventLikeRepository.save(eventLike);

        List<Match> matches = matchService.checkAndCreateMatch(userId, eventId);
        return new LikeResultResponse(toNewMatches(userId, matches), countOthersInterested(userId, eventId));
    }

    private List<LikeResultResponse.NewMatch> toNewMatches(Long userId, List<Match> matches) {
        if (matches == null || matches.isEmpty()) {
            return List.of();
        }
        return matches.stream().map(match -> {
            Long companionId = match.getUser1Id().equals(userId) ? match.getUser2Id() : match.getUser1Id();
            String companionName = userRepository.findById(companionId).map(User::getFirstName).orElse(null);
            Long chatId = chatRepository.findByMatchId(match.getId()).map(chat -> chat.getId()).orElse(null);
            return new LikeResultResponse.NewMatch(chatId, companionId, companionName);
        }).toList();
    }

    private long countOthersInterested(Long userId, Long eventId) {
        return eventLikeRepository.findByEventId(eventId).stream()
                .filter(like -> !like.getUserId().equals(userId))
                .count();
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
