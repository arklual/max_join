package com.join.back.service;

import com.join.back.model.dto.MatchResponse;
import com.join.back.model.entity.Chat;
import com.join.back.model.entity.Event;
import com.join.back.model.entity.EventLike;
import com.join.back.model.entity.Match;
import com.join.back.model.entity.User;
import com.join.back.repository.ChatMessageRepository;
import com.join.back.repository.ChatRepository;
import com.join.back.repository.EventLikeRepository;
import com.join.back.repository.EventRepository;
import com.join.back.repository.MatchRepository;
import com.join.back.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MatchService {

    private final MatchRepository matchRepository;
    private final EventLikeRepository eventLikeRepository;
    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final ChatRepository chatRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatService chatService;
    private final NotificationService notificationService;
    private final MessengerNotificationService messengerNotificationService;

    @Transactional(transactionManager = "transactionManager")
    public List<Match> checkAndCreateMatch(Long userId, Long eventId) {
        List<EventLike> allLikes = eventLikeRepository.findByEventId(eventId);

        User currentUser = userRepository.findById(userId).orElse(null);
        if (currentUser == null) {
            return List.of();
        }

        Event event = eventRepository.findById(eventId).orElse(null);
        String eventTitle = event != null ? event.getTitle() : "мероприятие";

        List<Long> otherUserIds = allLikes.stream()
                .map(EventLike::getUserId)
                .filter(id -> !id.equals(userId))
                .toList();

        if (otherUserIds.isEmpty()) {
            return List.of();
        }

        List<User> otherUsers = userRepository.findAllById(otherUserIds);

        List<Match> newMatches = new ArrayList<>();

        for (User otherUser : otherUsers) {
            if (matchRepository.existsByUsersAndEvent(userId, otherUser.getId(), eventId)) {
                continue;
            }

            if (!isMutualCriteriaMatch(currentUser, otherUser)) {
                continue;
            }

            Match match = Match.builder()
                    .user1Id(Math.min(userId, otherUser.getId()))
                    .user2Id(Math.max(userId, otherUser.getId()))
                    .eventId(eventId)
                    .createdAt(LocalDateTime.now())
                    .build();

            Match savedMatch = matchRepository.save(match);
            newMatches.add(savedMatch);

            Chat chat = chatService.createChat(savedMatch.getId(), savedMatch.getUser1Id(), savedMatch.getUser2Id(), eventId);
            Long chatId = chat != null ? chat.getId() : null;

            // Notify both users about the new match
            String currentUserName = currentUser.getFirstName() != null ? currentUser.getFirstName() : "Собеседник";
            String otherUserName = otherUser.getFirstName() != null ? otherUser.getFirstName() : "Собеседник";

            notificationService.createMatchNotification(userId, savedMatch.getId(), otherUserName, eventTitle);
            notificationService.createMatchNotification(otherUser.getId(), savedMatch.getId(), currentUserName, eventTitle);

            // Уведомления от бота в мессенджеры пользователей (MAX / Telegram)
            messengerNotificationService.sendMatchNotification(currentUser, otherUserName, eventTitle, chatId);
            messengerNotificationService.sendMatchNotification(otherUser, currentUserName, eventTitle, chatId);
        }

        return newMatches;
    }

    @Transactional(readOnly = true, transactionManager = "transactionManager")
    public List<MatchResponse> getMatches(Long userId) {
        List<Match> matches = matchRepository.findByUserId(userId);

        Set<Long> companionIds = matches.stream()
                .map(match -> match.getUser1Id().equals(userId) ? match.getUser2Id() : match.getUser1Id())
                .collect(Collectors.toSet());

        Set<Long> eventIds = matches.stream()
                .map(Match::getEventId)
                .collect(Collectors.toSet());

        Map<Long, User> usersMap = userRepository.findAllById(companionIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));

        Map<Long, Event> eventsMap = eventRepository.findAllById(eventIds).stream()
                .collect(Collectors.toMap(Event::getId, event -> event));

        return matches.stream().map(match -> {
            Long companionId = match.getUser1Id().equals(userId) ? match.getUser2Id() : match.getUser1Id();
            User companion = usersMap.get(companionId);
            Event event = eventsMap.get(match.getEventId());

            return new MatchResponse(
                    match.getId(),
                    companion != null ? companion.getFirstName() : null,
                    companion != null ? companion.getPhoto() : null,
                    event != null ? event.getTitle() : null,
                    event != null ? event.getEventDate().toString() : null,
                    match.getCreatedAt()
            );
        }).collect(Collectors.toList());
    }

    @Transactional(transactionManager = "transactionManager")
    public void deleteMatchesForUserAndEvent(Long userId, Long eventId) {
        List<Match> matches = matchRepository.findByUserIdAndEventId(userId, eventId);
        if (!matches.isEmpty()) {
            List<Long> matchIds = matches.stream().map(Match::getId).collect(Collectors.toList());
            List<Chat> chats = chatRepository.findByMatchIdIn(matchIds);
            if (!chats.isEmpty()) {
                List<Long> chatIds = chats.stream().map(Chat::getId).collect(Collectors.toList());
                chatMessageRepository.deleteByChatIdIn(chatIds);
                chatRepository.deleteAll(chats);
            }
            matchRepository.deleteAll(matches);
        }
    }

    @Transactional(readOnly = true, transactionManager = "transactionManager")
    public boolean hasMatchesForEvent(Long userId, Long eventId) {
        return !matchRepository.findByUserIdAndEventId(userId, eventId).isEmpty();
    }

    boolean isMutualCriteriaMatch(User userA, User userB) {
        return fitsUserCriteria(userA, userB) && fitsUserCriteria(userB, userA);
    }

    private boolean fitsUserCriteria(User candidate, User criteriaOwner) {
        if (criteriaOwner.getPreferredGender() != null
                && candidate.getGender() != null
                && criteriaOwner.getPreferredGender() != candidate.getGender()) {
            return false;
        }

        if (candidate.getAge() != null) {
            if (criteriaOwner.getPreferredAgeMin() != null
                    && candidate.getAge() < criteriaOwner.getPreferredAgeMin()) {
                return false;
            }
            if (criteriaOwner.getPreferredAgeMax() != null
                    && candidate.getAge() > criteriaOwner.getPreferredAgeMax()) {
                return false;
            }
        }

        if (criteriaOwner.getPreferredUniversityId() != null
                && candidate.getUniversity() != null
                && !criteriaOwner.getPreferredUniversityId().equals(candidate.getUniversity().getId())) {
            return false;
        }

        return true;
    }
}
