package com.join.back.service;

import com.join.back.model.dto.NotificationResponse;
import com.join.back.model.entity.Chat;
import com.join.back.model.entity.Event;
import com.join.back.model.entity.Match;
import com.join.back.model.entity.Notification;
import com.join.back.model.entity.NotificationType;
import com.join.back.model.entity.User;
import com.join.back.repository.ChatRepository;
import com.join.back.repository.EventRepository;
import com.join.back.repository.MatchRepository;
import com.join.back.repository.NotificationRepository;
import com.join.back.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final MatchRepository matchRepository;
    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final ChatRepository chatRepository;

    /**
     * Creates a MATCH notification for a user.
     *
     * @param userId       recipient user id
     * @param matchId      id of the created match
     * @param companionName name of the matched companion
     * @param eventTitle   title of the event
     */
    @Transactional(transactionManager = "transactionManager")
    public Notification createMatchNotification(Long userId, Long matchId,
                                                String companionName, String eventTitle) {
        Notification notification = Notification.builder()
                .userId(userId)
                .type(NotificationType.MATCH)
                .title("Нашлась компания")
                .message(companionName + " тоже хочет пойти на «" + eventTitle + "» — позовите пойти вместе")
                .matchId(matchId)
                .read(false)
                .createdAt(LocalDateTime.now())
                .build();
        return notificationRepository.save(notification);
    }

    @Transactional(transactionManager = "transactionManager")
    public Notification createGroupInviteNotification(Long userId, Long groupId, String inviterName, String eventTitle) {
        Notification notification = Notification.builder()
                .userId(userId)
                .type(NotificationType.GROUP_INVITE)
                .title("Тебя зовут в компанию")
                .message((inviterName != null ? inviterName : "Друг") + " зовёт тебя на «" + eventTitle + "»")
                .groupId(groupId)
                .read(false)
                .createdAt(LocalDateTime.now())
                .build();
        return notificationRepository.save(notification);
    }

    /**
     * Returns paginated notifications for a user, newest first.
     * Enriches MATCH notifications with companion + event + chat details.
     */
    @Transactional(readOnly = true, transactionManager = "transactionManager")
    public Page<NotificationResponse> getNotifications(Long userId, Pageable pageable) {
        Page<Notification> page = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);

        Set<Long> matchIds = page.getContent().stream()
                .map(Notification::getMatchId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        if (matchIds.isEmpty()) {
            return page.map(n -> toResponse(n, null, null, null, null));
        }

        List<Match> matches = matchRepository.findAllById(matchIds);
        Map<Long, Match> matchById = matches.stream()
                .collect(Collectors.toMap(Match::getId, m -> m));

        Set<Long> companionIds = new HashSet<>();
        Set<Long> eventIds = new HashSet<>();
        for (Match m : matches) {
            companionIds.add(m.getUser1Id().equals(userId) ? m.getUser2Id() : m.getUser1Id());
            eventIds.add(m.getEventId());
        }

        Map<Long, User> companionById = userRepository.findAllById(companionIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        Map<Long, Event> eventById = eventRepository.findAllById(eventIds).stream()
                .collect(Collectors.toMap(Event::getId, e -> e));

        Map<Long, Long> chatIdByMatchId = new HashMap<>();
        for (Long mid : matchIds) {
            chatRepository.findByMatchId(mid).ifPresent(c -> chatIdByMatchId.put(mid, c.getId()));
        }

        return page.map(n -> {
            if (n.getMatchId() == null) {
                return toResponse(n, null, null, null, null);
            }
            Match m = matchById.get(n.getMatchId());
            if (m == null) {
                return toResponse(n, null, null, null, null);
            }
            Long companionId = m.getUser1Id().equals(userId) ? m.getUser2Id() : m.getUser1Id();
            User companion = companionById.get(companionId);
            Event event = eventById.get(m.getEventId());
            Long chatId = chatIdByMatchId.get(m.getId());
            return toResponse(n, companion, event, chatId, m);
        });
    }

    /**
     * Returns count of unread notifications for a user.
     */
    @Transactional(readOnly = true, transactionManager = "transactionManager")
    public long countUnread(Long userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    /**
     * Marks all unread notifications for a user as read.
     */
    @Transactional(transactionManager = "transactionManager")
    public void markAllRead(Long userId) {
        notificationRepository.markAllReadByUserId(userId);
    }

    /**
     * Marks a single notification as read (only if it belongs to the given user).
     */
    @Transactional(transactionManager = "transactionManager")
    public void markRead(Long userId, Long notificationId) {
        notificationRepository.findById(notificationId).ifPresent(n -> {
            if (n.getUserId().equals(userId) && !n.isRead()) {
                n.setRead(true);
                notificationRepository.save(n);
            }
        });
    }

    private NotificationResponse toResponse(Notification n, User companion, Event event, Long chatId, Match match) {
        return new NotificationResponse(
                n.getId(),
                n.getType(),
                n.getTitle(),
                n.getMessage(),
                n.getMatchId(),
                companion != null ? companion.getId() : null,
                companion != null ? companion.getFirstName() : null,
                companion != null ? companion.getPhoto() : null,
                match != null ? match.getEventId() : (event != null ? event.getId() : null),
                event != null ? event.getTitle() : null,
                chatId,
                n.getGroupId(),
                n.isRead(),
                n.getCreatedAt()
        );
    }
}
