package com.join.back.service;

import com.join.back.model.dto.MatchResponse;
import com.join.back.model.entity.Chat;
import com.join.back.model.entity.Event;
import com.join.back.model.entity.Match;
import com.join.back.model.entity.MatchStatus;
import com.join.back.model.entity.User;
import com.join.back.repository.ChatRepository;
import com.join.back.repository.EventRepository;
import com.join.back.repository.MatchRepository;
import com.join.back.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A match is a suggestion, not a conversation: one side asks "пойдём вместе?", and the chat opens
 * only when the other accepts. Nobody gets messages from a stranger they didn't agree to.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContactRequestService {

    private static final ZoneId ZONE = ZoneId.of("Europe/Moscow");
    private static final Pattern BUTTON = Pattern.compile("^(invite|accept|decline)_(\\d{1,18})$");

    private final MatchRepository matchRepository;
    private final ChatRepository chatRepository;
    private final ChatService chatService;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final UserBlockService userBlockService;
    private final MessengerNotificationService messengerNotificationService;

    /** Reply to a bot button: text to show and, once there is one, the chat to open. */
    public record ButtonReply(String text, Long chatId) {
    }

    /** Companions for upcoming events that are not a chat yet (new and pending requests). */
    @Transactional(readOnly = true, transactionManager = "transactionManager")
    public List<MatchResponse> getPending(Long userId) {
        LocalDate today = LocalDate.now(ZONE);
        List<Match> matches = matchRepository.findByUserId(userId).stream()
                .filter(m -> m.getStatus() == MatchStatus.NEW || m.getStatus() == MatchStatus.REQUESTED)
                .toList();
        if (matches.isEmpty()) {
            return List.of();
        }
        Map<Long, Event> events = eventRepository.findAllById(matches.stream().map(Match::getEventId).toList()).stream()
                .collect(Collectors.toMap(Event::getId, Function.identity()));
        Map<Long, User> users = userRepository.findAllById(matches.stream().map(m -> companionOf(m, userId)).toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        java.util.Set<Long> blocked = userBlockService.relatedUserIds(userId);
        return matches.stream()
                .filter(m -> !blocked.contains(companionOf(m, userId)))
                .filter(m -> {
                    Event e = events.get(m.getEventId());
                    return e != null && e.getEventDate() != null && !e.getEventDate().isBefore(today);
                })
                .map(m -> toResponse(m, userId, events.get(m.getEventId()), users.get(companionOf(m, userId)), null))
                // Requests to me first, then the rest by event date.
                .sorted(Comparator.comparing((MatchResponse r) -> !("REQUESTED".equals(r.status()) && !r.requestedByMe()))
                        .thenComparing(MatchResponse::eventDate))
                .toList();
    }

    /** "Пойдём вместе?" — if the companion already asked, this accepts right away. */
    @Transactional(transactionManager = "transactionManager")
    public MatchResponse request(Long matchId, Long userId) {
        Match match = requireParticipant(matchId, userId);
        Long companionId = companionOf(match, userId);
        requireNotBlocked(userId, companionId);
        if (match.getStatus() == MatchStatus.REQUESTED && companionId.equals(match.getRequestedBy())) {
            return accept(matchId, userId);
        }
        if (match.getStatus() == MatchStatus.ACCEPTED) {
            return response(match, userId);
        }
        if (match.getStatus() == MatchStatus.DECLINED) {
            throw new UserActionException("Этот человек уже ответил на приглашение");
        }
        if (match.getStatus() == MatchStatus.NEW) {
            match.setStatus(MatchStatus.REQUESTED);
            match.setRequestedBy(userId);
            match.setRequestedAt(LocalDateTime.now());
            matchRepository.save(match);
            notify(() -> messengerNotificationService.sendContactRequest(
                    user(companionId), nameOf(user(userId)), eventTitle(match), match.getId()));
        }
        return response(match, userId);
    }

    /** "Пойдём!" — opens the chat for both. */
    @Transactional(transactionManager = "transactionManager")
    public MatchResponse accept(Long matchId, Long userId) {
        Match match = requireParticipant(matchId, userId);
        Long companionId = companionOf(match, userId);
        requireNotBlocked(userId, companionId);
        if (match.getStatus() == MatchStatus.ACCEPTED) {
            return response(match, userId);
        }
        if (match.getStatus() != MatchStatus.REQUESTED || userId.equals(match.getRequestedBy())) {
            throw new UserActionException("Приглашения от этого человека нет");
        }
        match.setStatus(MatchStatus.ACCEPTED);
        match.setRespondedAt(LocalDateTime.now());
        matchRepository.save(match);
        Chat chat = chatRepository.findByMatchId(match.getId())
                .orElseGet(() -> chatService.createChat(match.getId(), match.getUser1Id(), match.getUser2Id(), match.getEventId()));
        notify(() -> messengerNotificationService.sendContactAccepted(
                user(companionId), nameOf(user(userId)), eventTitle(match), chat.getId()));
        return toResponse(match, userId, eventRepository.findById(match.getEventId()).orElse(null),
                user(companionId), chat.getId());
    }

    /** "Не в этот раз" — the suggestion disappears for both, without telling the other side. */
    @Transactional(transactionManager = "transactionManager")
    public MatchResponse decline(Long matchId, Long userId) {
        Match match = requireParticipant(matchId, userId);
        if (match.getStatus() == MatchStatus.ACCEPTED) {
            throw new UserActionException("Вы уже договорились общаться — чат можно удалить в списке чатов");
        }
        match.setStatus(MatchStatus.DECLINED);
        match.setRespondedAt(LocalDateTime.now());
        matchRepository.save(match);
        return response(match, userId);
    }

    /** Bot buttons {@code invite_<matchId>}, {@code accept_<matchId>}, {@code decline_<matchId>}. */
    @Transactional(transactionManager = "transactionManager")
    public ButtonReply handleButton(User user, String payload) {
        Matcher m = payload == null ? null : BUTTON.matcher(payload);
        if (m == null || !m.matches()) {
            return null;
        }
        if (user == null) {
            return new ButtonReply("Сначала откройте JOIN и заполните профиль.", null);
        }
        Long matchId = Long.parseLong(m.group(2));
        try {
            MatchResponse result = switch (m.group(1)) {
                case "invite" -> request(matchId, user.getId());
                case "accept" -> accept(matchId, user.getId());
                default -> decline(matchId, user.getId());
            };
            String name = result.companionName() != null ? result.companionName() : "собеседник";
            return switch (result.status()) {
                case "ACCEPTED" -> new ButtonReply("✅ Договорились общаться! Чат с " + name
                        + " открыт — решите, где встретиться.", result.chatId());
                case "REQUESTED" -> new ButtonReply("🤝 Позвали " + name
                        + ". Чат откроется, когда придёт ответ.", null);
                default -> new ButtonReply("Хорошо, не в этот раз. Поищем компанию на другие события.", null);
            };
        } catch (UserActionException e) {
            return new ButtonReply(e.getMessage(), null);
        } catch (AccessDeniedException | EntityNotFoundException e) {
            return new ButtonReply("Это приглашение недоступно.", null);
        }
    }

    private MatchResponse response(Match match, Long userId) {
        Event event = eventRepository.findById(match.getEventId()).orElse(null);
        User companion = userRepository.findById(companionOf(match, userId)).orElse(null);
        Long chatId = chatRepository.findByMatchId(match.getId()).map(Chat::getId).orElse(null);
        return toResponse(match, userId, event, companion, chatId);
    }

    private static MatchResponse toResponse(Match match, Long userId, Event event, User companion, Long chatId) {
        return new MatchResponse(
                match.getId(),
                companionOf(match, userId),
                companion != null ? companion.getFirstName() : null,
                companion != null ? companion.getPhoto() : null,
                companion != null ? companion.getAge() : null,
                match.getEventId(),
                event != null ? event.getTitle() : null,
                event != null ? event.getEventDate() : null,
                match.getStatus().name(),
                userId.equals(match.getRequestedBy()),
                chatId,
                match.getCreatedAt());
    }

    private Match requireParticipant(Long matchId, Long userId) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new EntityNotFoundException("Match not found with id: " + matchId));
        if (!match.getUser1Id().equals(userId) && !match.getUser2Id().equals(userId)) {
            throw new AccessDeniedException("User is not part of this match");
        }
        return match;
    }

    private void requireNotBlocked(Long userId, Long companionId) {
        if (userBlockService.hasBlocked(userId, companionId) || userBlockService.hasBlocked(companionId, userId)) {
            throw new UserActionException("С этим человеком общение ограничено");
        }
    }

    private String eventTitle(Match match) {
        return eventRepository.findById(match.getEventId()).map(Event::getTitle).orElse("событие");
    }

    private User user(Long id) {
        return userRepository.findById(id).orElse(null);
    }

    private static void notify(Runnable send) {
        try {
            send.run();
        } catch (Exception e) {
            log.warn("Failed to send contact request notification: {}", e.getMessage());
        }
    }

    private static Long companionOf(Match match, Long userId) {
        return match.getUser1Id().equals(userId) ? match.getUser2Id() : match.getUser1Id();
    }

    private static String nameOf(User user) {
        return user != null && user.getFirstName() != null ? user.getFirstName() : "Собеседник";
    }
}
