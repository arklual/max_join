package com.join.back.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.join.back.util.MessengerHtml;

import java.util.List;
import java.util.Map;

/**
 * Отправляет уведомления пользователям в личный чат MAX от имени бота.
 * Кнопки открывают мини-приложение сразу на нужном экране (см. {@link DeepLinks}).
 */
@Service
@RequiredArgsConstructor
public class MaxNotificationService {

    private static final Logger log = LoggerFactory.getLogger(MaxNotificationService.class);

    private final MaxBotApiClient maxBotApiClient;
    private final MaxBotInfoService maxBotInfoService;

    /**
     * Отправляет уведомление о найденном напарнике в личный чат MAX.
     *
     * @param maxId         MAX user_id пользователя
     * @param companionName имя найденного напарника
     * @param eventTitle    название мероприятия
     * @param chatId        id созданного чата — кнопка откроет его
     */
    /** A companion found — nobody can write yet: invite to go together right from the bot. */
    public void sendMatchNotification(Long maxId, Long companionId, String companionName, String eventTitle, Long matchId) {
        String text = String.format(
                """
                🎉 Нашлась компания!

                На «%s» тоже хочет пойти <b>%s</b>.

                Позовите пойти вместе — чат откроется, когда придёт согласие 👇""",
                escapeHtml(eventTitle),
                escapeHtml(companionName)
        );
        sendKeyboard(maxId, text, List.of(
                List.of(MaxBotApiClient.callbackButton("🤝 Позвать пойти вместе", DeepLinks.invite(matchId))),
                List.of(MaxBotApiClient.openAppButton("👤 Посмотреть профиль", maxBotInfoService.getUsername(), DeepLinks.profile(companionId)))));
    }

    /** "Пойдём вместе?" — answered with one tap. */
    public void sendContactRequest(Long maxId, Long requesterId, String requesterName, String eventTitle, Long matchId) {
        String text = String.format("🤝 <b>%s</b> зовёт вас пойти на «%s» вместе.",
                escapeHtml(requesterName), escapeHtml(eventTitle));
        sendKeyboard(maxId, text, List.of(
                List.of(MaxBotApiClient.callbackButton("✅ Пойдём", DeepLinks.acceptContact(matchId)),
                        MaxBotApiClient.callbackButton("Не в этот раз", DeepLinks.declineContact(matchId))),
                List.of(MaxBotApiClient.openAppButton("👤 Посмотреть профиль", maxBotInfoService.getUsername(), DeepLinks.profile(requesterId)))));
    }

    /** The invitation accepted — the chat is open. */
    public void sendContactAccepted(Long maxId, String companionName, String eventTitle, Long chatId) {
        String text = String.format("✅ <b>%s</b> — «пойдём!» на «%s». Чат открыт: договоритесь, где встретиться.",
                escapeHtml(companionName), escapeHtml(eventTitle));
        send(maxId, text, ("💬 Написать " + shortName(companionName)).trim(), DeepLinks.chat(chatId));
    }

    /**
     * Отправляет уведомление о новом сообщении в личном чате.
     */
    public void sendChatMessageNotification(Long maxId, String senderName, String messageText, Long chatId) {
        String text = String.format(
                """
                💬 Новое сообщение от <b>%s</b>

                %s""",
                escapeHtml(senderName != null ? senderName : "собеседника"),
                escapeHtml(preview(messageText))
        );

        send(maxId, text, "💬 Ответить", DeepLinks.chat(chatId));
    }

    /**
     * Отправляет уведомление о новом сообщении в групповом чате.
     */
    public void sendGroupMessageNotification(Long maxId, String senderName, String groupEventTitle,
                                             String messageText, Long groupChatId) {
        String text = String.format(
                """
                👥 Новое сообщение в группе «%s»

                <b>%s</b>: %s""",
                escapeHtml(groupEventTitle != null ? groupEventTitle : "Группа"),
                escapeHtml(senderName != null ? senderName : "Участник"),
                escapeHtml(preview(messageText))
        );

        send(maxId, text, "👥 Открыть чат группы", DeepLinks.groupChat(groupChatId));
    }

    /**
     * Приглашение от знакомого в компанию на событие.
     */
    public void sendGroupInvite(Long maxId, String inviterName, String eventTitle, String when, Long groupId) {
        String text = String.format(
                """
                👋 <b>%s</b> зовёт тебя в компанию на «%s»%s

                Загляни — там уже видно, кто ещё идёт 👇""",
                escapeHtml(inviterName != null ? inviterName : "Друг"),
                escapeHtml(eventTitle),
                when != null ? " — " + escapeHtml(when) : ""
        );

        send(maxId, text, "👥 Посмотреть компанию", DeepLinks.group(groupId));
    }

    /**
     * Напоминание накануне мероприятия, на которое пользователь идёт с напарником.
     */
    public void sendEventReminder(Long maxId, String companionName, String eventTitle, String when, Long chatId) {
        String text = String.format(
                """
                ⏰ Уже %s — «%s»

                Ты идёшь вместе с <b>%s</b>. Договоритесь, где встретиться 👇""",
                escapeHtml(when),
                escapeHtml(eventTitle),
                escapeHtml(companionName)
        );

        send(maxId, text, ("💬 Написать " + shortName(companionName)).trim(), DeepLinks.chat(chatId));
    }

    /** The day after the event: "did you go together?" answered with one tap. */
    public void sendOutingFeedback(Long maxId, String companionName, String eventTitle, Long chatId) {
        String text = String.format("👋 Вчера было «%s». Сходили вместе с <b>%s</b>?",
                escapeHtml(eventTitle), escapeHtml(companionName));
        sendKeyboard(maxId, text, List.of(List.of(
                MaxBotApiClient.callbackButton("🎉 Да, сходили", DeepLinks.went(chatId, true)),
                MaxBotApiClient.callbackButton("Не получилось", DeepLinks.went(chatId, false)))));
    }

    private void send(Long maxId, String text, String buttonText, String payload) {
        sendKeyboard(maxId, text, List.of(List.of(
                MaxBotApiClient.openAppButton(buttonText, maxBotInfoService.getUsername(), payload))));
    }

    private void sendKeyboard(Long maxId, String text, List<List<Map<String, Object>>> keyboard) {
        if (maxId == null) {
            return;
        }
        if (!maxBotApiClient.isConfigured()) {
            log.warn("MAX bot token not configured — skipping notification");
            return;
        }
        try {
            maxBotApiClient.sendMessageToUser(maxId, text, "html", keyboard);
            log.info("Sent notification to MAX user {}", maxId);
        } catch (Exception e) {
            log.error("Failed to send notification to MAX user {}: {}", maxId, e.getMessage());
        }
    }

    static String preview(String text) {
        if (text == null) return "";
        return text.length() > 100 ? text.substring(0, 100) + "…" : text;
    }

    /** Keeps button labels short: "Написать Анастасия" fits, a long full name does not. */
    static String shortName(String name) {
        if (name == null || name.isBlank()) return "";
        String first = name.trim().split("\\s+")[0];
        return first.length() > 20 ? first.substring(0, 20) : first;
    }

    private String escapeHtml(String text) {
        return text == null ? "" : MessengerHtml.escape(text);
    }
}
