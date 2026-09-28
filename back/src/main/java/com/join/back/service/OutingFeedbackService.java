package com.join.back.service;

import com.join.back.model.dto.OutingStateResponse;
import com.join.back.model.entity.Chat;
import com.join.back.model.entity.Event;
import com.join.back.model.entity.OutingConfirmation;
import com.join.back.model.entity.OutingResult;
import com.join.back.model.entity.User;
import com.join.back.repository.ChatRepository;
import com.join.back.repository.EventRepository;
import com.join.back.repository.OutingConfirmationRepository;
import com.join.back.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The end of the main scenario: the day after the event the bot asks each pair that accepted
 * "пойдём вместе?" whether they went. Answers are the pilot metric (match → invited → accepted → went).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutingFeedbackService {

    private static final ZoneId ZONE = ZoneId.of("Europe/Moscow");
    private static final Pattern BUTTON = Pattern.compile("^went_(\\d{1,18})_(yes|no)$");

    private final ChatRepository chatRepository;
    private final OutingConfirmationRepository confirmationRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final UserBlockService userBlockService;
    private final MessengerNotificationService messengerNotificationService;

    @Transactional(readOnly = true, transactionManager = "transactionManager")
    public OutingStateResponse getState(Long chatId, Long userId) {
        return state(requireParticipant(chatId, userId), userId);
    }

    /** "Сходили вместе?" — available from the day of the event. */
    @Transactional(transactionManager = "transactionManager")
    public OutingStateResponse answer(Long chatId, Long userId, boolean went) {
        Chat chat = requireParticipant(chatId, userId);
        Event event = eventRepository.findById(chat.getEventId())
                .orElseThrow(() -> new EntityNotFoundException("Event not found with id: " + chat.getEventId()));
        if (event.getEventDate() != null && event.getEventDate().isAfter(LocalDate.now(ZONE))) {
            throw new UserActionException("Событие ещё не прошло");
        }
        OutingConfirmation mine = getOrCreate(chat.getId(), userId);
        mine.setWent(went ? OutingResult.WENT : OutingResult.NOT_WENT);
        mine.setAnsweredAt(LocalDateTime.now());
        confirmationRepository.save(mine);
        return state(chat, userId);
    }

    @Scheduled(cron = "${outings.feedback-cron:0 0 12 * * *}", zone = "Europe/Moscow")
    public void askAboutYesterday() {
        int asked = askAbout(LocalDate.now(ZONE).minusDays(1));
        log.info("\"Did you go together?\" questions sent: {}", asked);
    }

    /** Asks both people of every pair with a chat (the invitation was accepted) for events on {@code date}; once each. */
    @Transactional(transactionManager = "transactionManager")
    public int askAbout(LocalDate date) {
        Map<Long, Event> events = eventRepository.findByEventDate(date).stream()
                .collect(Collectors.toMap(Event::getId, Function.identity()));
        if (events.isEmpty()) {
            return 0;
        }
        int asked = 0;
        for (Chat chat : chatRepository.findByEventIdIn(events.keySet())) {
            if (userBlockService.hasBlocked(chat.getUser1Id(), chat.getUser2Id())
                    || userBlockService.hasBlocked(chat.getUser2Id(), chat.getUser1Id())) {
                continue;
            }
            Map<Long, OutingConfirmation> byUser = confirmationRepository.findByChatId(chat.getId()).stream()
                    .collect(Collectors.toMap(OutingConfirmation::getUserId, Function.identity()));
            Event event = events.get(chat.getEventId());
            User user1 = userRepository.findById(chat.getUser1Id()).orElse(null);
            User user2 = userRepository.findById(chat.getUser2Id()).orElse(null);
            if (user1 == null || user2 == null) {
                continue;
            }
            if (chat.getUser1DeletedAt() == null && ask(chat, byUser, user1, user2, event)) asked++;
            if (chat.getUser2DeletedAt() == null && ask(chat, byUser, user2, user1, event)) asked++;
        }
        return asked;
    }

    /** Handles bot buttons {@code went_<chatId>_yes|no}; returns the reply text, or null for other buttons. */
    @Transactional(transactionManager = "transactionManager")
    public String handleButton(User user, String payload) {
        Matcher m = payload == null ? null : BUTTON.matcher(payload);
        if (m == null || !m.matches()) {
            return null;
        }
        if (user == null) {
            return "Сначала откройте JOIN и заполните профиль.";
        }
        boolean went = "yes".equals(m.group(2));
        try {
            answer(Long.parseLong(m.group(1)), user.getId(), went);
            return went
                    ? "🎉 Здорово! Спасибо, что рассказали — так JOIN подбирает компанию точнее."
                    : "Спасибо за ответ! В следующий раз обязательно получится.";
        } catch (UserActionException e) {
            return e.getMessage();
        } catch (AccessDeniedException | EntityNotFoundException e) {
            return "Этот чат недоступен.";
        }
    }

    private boolean ask(Chat chat, Map<Long, OutingConfirmation> byUser, User user, User companion, Event event) {
        OutingConfirmation confirmation = byUser.get(user.getId());
        if (confirmation == null) {
            confirmation = OutingConfirmation.builder().chatId(chat.getId()).userId(user.getId()).build();
        }
        if (confirmation.getAskedAt() != null || confirmation.getWent() != null) {
            return false;
        }
        confirmation.setAskedAt(LocalDateTime.now());
        confirmationRepository.save(confirmation);
        try {
            messengerNotificationService.sendOutingFeedback(user, nameOf(companion), event.getTitle(), chat.getId());
        } catch (Exception e) {
            log.warn("Failed to ask user {} about chat {}: {}", user.getId(), chat.getId(), e.getMessage());
        }
        return true;
    }

    private OutingStateResponse state(Chat chat, Long userId) {
        Long companionId = chat.getUser1Id().equals(userId) ? chat.getUser2Id() : chat.getUser1Id();
        OutingConfirmation mine = confirmationRepository.findByChatIdAndUserId(chat.getId(), userId).orElse(null);
        OutingConfirmation theirs = confirmationRepository.findByChatIdAndUserId(chat.getId(), companionId).orElse(null);
        Event event = eventRepository.findById(chat.getEventId()).orElse(null);
        boolean passed = event != null && event.getEventDate() != null && event.getEventDate().isBefore(LocalDate.now(ZONE));
        return new OutingStateResponse(chat.getId(), passed,
                mine != null && mine.getWent() != null ? mine.getWent().name() : null,
                theirs != null && theirs.getWent() != null ? theirs.getWent().name() : null);
    }

    private OutingConfirmation getOrCreate(Long chatId, Long userId) {
        return confirmationRepository.findByChatIdAndUserId(chatId, userId)
                .orElseGet(() -> OutingConfirmation.builder().chatId(chatId).userId(userId).build());
    }

    private Chat requireParticipant(Long chatId, Long userId) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new EntityNotFoundException("Chat not found with id: " + chatId));
        if (!chat.getUser1Id().equals(userId) && !chat.getUser2Id().equals(userId)) {
            throw new AccessDeniedException("User does not have access to this chat");
        }
        return chat;
    }

    private static String nameOf(User user) {
        return user != null && user.getFirstName() != null ? user.getFirstName() : "Собеседник";
    }
}
