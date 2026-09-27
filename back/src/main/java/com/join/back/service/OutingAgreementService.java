package com.join.back.service;

import com.join.back.model.dto.OutingStateResponse;
import com.join.back.model.entity.Chat;
import com.join.back.model.entity.Event;
import com.join.back.model.entity.OutingConfirmation;
import com.join.back.model.entity.OutingResult;
import com.join.back.model.entity.User;
import com.join.back.repository.ChatMessageRepository;
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
 * The end of the main scenario for a matched pair: a contact is not yet a plan, so both confirm
 * "we agreed to go together"; the day after the event the bot asks "did you go together?".
 * Answers are the pilot metrics (match → agreed → went).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutingAgreementService {

    private static final ZoneId ZONE = ZoneId.of("Europe/Moscow");
    private static final Pattern CALLBACK = Pattern.compile("^(agree|went)_(\\d{1,18})(?:_(yes|no))?$");

    public static final String NONE = "NONE";
    public static final String PROPOSED_BY_ME = "PROPOSED_BY_ME";
    public static final String PROPOSED_BY_COMPANION = "PROPOSED_BY_COMPANION";
    public static final String AGREED = "AGREED";

    private final ChatRepository chatRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final OutingConfirmationRepository confirmationRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final UserBlockService userBlockService;
    private final MessengerNotificationService messengerNotificationService;

    @Transactional(readOnly = true, transactionManager = "transactionManager")
    public OutingStateResponse getState(Long chatId, Long userId) {
        Chat chat = requireParticipant(chatId, userId);
        return state(chat, userId);
    }

    /** "Договорились пойти вместе": the first one proposes, the second one confirms. */
    @Transactional(transactionManager = "transactionManager")
    public OutingStateResponse agree(Long chatId, Long userId) {
        Chat chat = requireParticipant(chatId, userId);
        Long companionId = companionOf(chat, userId);
        if (userBlockService.hasBlocked(userId, companionId) || userBlockService.hasBlocked(companionId, userId)) {
            throw new UserActionException("С этим человеком переписка ограничена");
        }
        Event event = requireEvent(chat);
        if (isPassed(event)) {
            throw new UserActionException("Событие уже прошло");
        }
        OutingConfirmation mine = getOrCreate(chat.getId(), userId);
        boolean newlyAgreed = mine.getAgreedAt() == null;
        if (newlyAgreed) {
            mine.setAgreedAt(LocalDateTime.now());
            confirmationRepository.save(mine);
        }
        if (newlyAgreed) {
            boolean companionAgreed = confirmationRepository.findByChatIdAndUserId(chat.getId(), companionId)
                    .map(c -> c.getAgreedAt() != null).orElse(false);
            notifyCompanion(companionId, userId, event, chat.getId(), companionAgreed);
        }
        return state(chat, userId);
    }

    @Transactional(transactionManager = "transactionManager")
    public OutingStateResponse cancelAgreement(Long chatId, Long userId) {
        Chat chat = requireParticipant(chatId, userId);
        confirmationRepository.findByChatIdAndUserId(chat.getId(), userId).ifPresent(mine -> {
            mine.setAgreedAt(null);
            confirmationRepository.save(mine);
        });
        return state(chat, userId);
    }

    /** "Сходили вместе?" — available from the day of the event. */
    @Transactional(transactionManager = "transactionManager")
    public OutingStateResponse answer(Long chatId, Long userId, boolean went) {
        Chat chat = requireParticipant(chatId, userId);
        Event event = requireEvent(chat);
        if (event.getEventDate() != null && event.getEventDate().isAfter(LocalDate.now(ZONE))) {
            throw new UserActionException("Событие ещё не прошло");
        }
        OutingConfirmation mine = getOrCreate(chat.getId(), userId);
        mine.setWent(went ? OutingResult.WENT : OutingResult.NOT_WENT);
        mine.setAnsweredAt(LocalDateTime.now());
        if (went && mine.getAgreedAt() == null) {
            mine.setAgreedAt(LocalDateTime.now());
        }
        confirmationRepository.save(mine);
        return state(chat, userId);
    }

    @Scheduled(cron = "${outings.feedback-cron:0 0 12 * * *}", zone = "Europe/Moscow")
    public void askAboutYesterday() {
        int asked = askAbout(LocalDate.now(ZONE).minusDays(1));
        log.info("\"Did you go together?\" questions sent: {}", asked);
    }

    /**
     * Asks both participants of pairs that planned to go on {@code date}: someone pressed "договорились",
     * or both wrote in the chat. Each person is asked once.
     */
    @Transactional(transactionManager = "transactionManager")
    public int askAbout(LocalDate date) {
        Map<Long, Event> events = eventRepository.findByEventDate(date).stream()
                .collect(Collectors.toMap(Event::getId, Function.identity()));
        if (events.isEmpty()) {
            return 0;
        }
        int asked = 0;
        for (Chat chat : chatRepository.findByEventIdIn(events.keySet())) {
            Map<Long, OutingConfirmation> byUser = confirmationRepository.findByChatId(chat.getId()).stream()
                    .collect(Collectors.toMap(OutingConfirmation::getUserId, Function.identity()));
            boolean agreed = byUser.values().stream().anyMatch(c -> c.getAgreedAt() != null);
            boolean bothWrote = chatMessageRepository.existsByChatIdAndSenderId(chat.getId(), chat.getUser1Id())
                    && chatMessageRepository.existsByChatIdAndSenderId(chat.getId(), chat.getUser2Id());
            if (!agreed && !bothWrote) {
                continue;
            }
            if (userBlockService.hasBlocked(chat.getUser1Id(), chat.getUser2Id())
                    || userBlockService.hasBlocked(chat.getUser2Id(), chat.getUser1Id())) {
                continue;
            }
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

    /** Handles bot buttons {@code agree_<chatId>} and {@code went_<chatId>_yes|no}; returns the reply text. */
    @Transactional(transactionManager = "transactionManager")
    public String handleButton(User user, String payload) {
        Matcher m = payload == null ? null : CALLBACK.matcher(payload);
        if (m == null || !m.matches()) {
            return null;
        }
        if (user == null) {
            return "Сначала откройте JOIN и заполните профиль.";
        }
        Long chatId = Long.parseLong(m.group(2));
        try {
            if ("agree".equals(m.group(1))) {
                OutingStateResponse state = agree(chatId, user.getId());
                return AGREED.equals(state.agreement())
                        ? "✅ Договорились! Накануне напомню о встрече."
                        : "Отметили. Ждём подтверждения собеседника.";
            }
            boolean went = "yes".equals(m.group(3));
            answer(chatId, user.getId(), went);
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

    private void notifyCompanion(Long companionId, Long userId, Event event, Long chatId, boolean companionAgreed) {
        try {
            User companion = userRepository.findById(companionId).orElse(null);
            User me = userRepository.findById(userId).orElse(null);
            if (companionAgreed) {
                messengerNotificationService.sendOutingAgreed(companion, nameOf(me), event.getTitle(), chatId);
            } else {
                messengerNotificationService.sendOutingProposal(companion, nameOf(me), event.getTitle(), chatId);
            }
        } catch (Exception e) {
            log.warn("Failed to notify user {} about agreement in chat {}: {}", companionId, chatId, e.getMessage());
        }
    }

    private OutingStateResponse state(Chat chat, Long userId) {
        Long companionId = companionOf(chat, userId);
        OutingConfirmation mine = confirmationRepository.findByChatIdAndUserId(chat.getId(), userId).orElse(null);
        OutingConfirmation theirs = confirmationRepository.findByChatIdAndUserId(chat.getId(), companionId).orElse(null);
        boolean iAgreed = mine != null && mine.getAgreedAt() != null;
        boolean theyAgreed = theirs != null && theirs.getAgreedAt() != null;
        String agreement = iAgreed && theyAgreed ? AGREED
                : iAgreed ? PROPOSED_BY_ME
                : theyAgreed ? PROPOSED_BY_COMPANION
                : NONE;
        Event event = eventRepository.findById(chat.getEventId()).orElse(null);
        return new OutingStateResponse(chat.getId(), agreement, event != null && isPassed(event),
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

    private Event requireEvent(Chat chat) {
        return eventRepository.findById(chat.getEventId())
                .orElseThrow(() -> new EntityNotFoundException("Event not found with id: " + chat.getEventId()));
    }

    private static boolean isPassed(Event event) {
        return event.getEventDate() != null && event.getEventDate().isBefore(LocalDate.now(ZONE));
    }

    private static Long companionOf(Chat chat, Long userId) {
        return chat.getUser1Id().equals(userId) ? chat.getUser2Id() : chat.getUser1Id();
    }

    private static String nameOf(User user) {
        return user != null && user.getFirstName() != null ? user.getFirstName() : "Собеседник";
    }
}
