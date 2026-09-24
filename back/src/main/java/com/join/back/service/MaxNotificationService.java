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
    public void sendMatchNotification(Long maxId, String companionName, String eventTitle, Long chatId) {
        String text = String.format(
                """
                🎉 Напарник найден!

                На «%s» с тобой хочет пойти <b>%s</b>.

                Напиши первым — договоритесь, когда и где встретиться 👇""",
                escapeHtml(eventTitle),
                escapeHtml(companionName)
        );

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

    private void send(Long maxId, String text, String buttonText, String payload) {
        if (maxId == null) {
            return;
        }
        if (!maxBotApiClient.isConfigured()) {
            log.warn("MAX bot token not configured — skipping notification");
            return;
        }
        try {
            List<List<Map<String, Object>>> keyboard = List.of(List.of(
                    MaxBotApiClient.openAppButton(buttonText, maxBotInfoService.getUsername(), payload)));
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
