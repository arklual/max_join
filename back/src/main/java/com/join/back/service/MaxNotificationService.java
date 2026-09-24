package com.join.back.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.join.back.util.MessengerHtml;

import java.util.List;

/**
 * Отправляет уведомления пользователям в личный чат MAX от имени бота.
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
     */
    public void sendMatchNotification(Long maxId, String companionName, String eventTitle) {
        if (!maxBotApiClient.isConfigured()) {
            log.warn("MAX bot token not configured — skipping match notification");
            return;
        }

        String text = String.format(
                """
                🎉 Напарник найден!

                Тебе подобрали напарника на мероприятие «%s»!
                Твой напарник — <b>%s</b>

                Заходи в чат в приложении, чтобы узнать подробности и начать общение 👇""",
                escapeHtml(eventTitle),
                escapeHtml(companionName)
        );

        sendMaxMessage(maxId, text);
    }

    /**
     * Отправляет уведомление о новом сообщении в личном чате.
     */
    public void sendChatMessageNotification(Long maxId, String senderName, String messageText) {
        if (!maxBotApiClient.isConfigured()) {
            log.warn("MAX bot token not configured — skipping message notification");
            return;
        }

        String preview = messageText.length() > 100 ? messageText.substring(0, 100) + "…" : messageText;

        String text = String.format(
                """
                💬 Новое сообщение от <b>%s</b>

                %s""",
                escapeHtml(senderName != null ? senderName : "Пользователь"),
                escapeHtml(preview)
        );

        sendMaxMessage(maxId, text);
    }

    /**
     * Отправляет уведомление о новом сообщении в групповом чате.
     */
    public void sendGroupMessageNotification(Long maxId, String senderName, String groupEventTitle, String messageText) {
        if (!maxBotApiClient.isConfigured()) {
            log.warn("MAX bot token not configured — skipping group message notification");
            return;
        }

        String preview = messageText.length() > 100 ? messageText.substring(0, 100) + "…" : messageText;

        String text = String.format(
                """
                👥 Новое сообщение в группе «%s»

                <b>%s</b>: %s""",
                escapeHtml(groupEventTitle != null ? groupEventTitle : "Группа"),
                escapeHtml(senderName != null ? senderName : "Пользователь"),
                escapeHtml(preview)
        );

        sendMaxMessage(maxId, text);
    }

    private void sendMaxMessage(Long maxId, String text) {
        try {
            maxBotApiClient.sendMessageToUser(maxId, text, "html", List.of(List.of(
                    MaxBotApiClient.openAppButton("💬 Открыть JOIN", maxBotInfoService.getUsername(), null)
            )));
            log.info("Sent message notification to MAX user {}", maxId);
        } catch (Exception e) {
            log.error("Failed to send message notification to MAX user {}: {}", maxId, e.getMessage());
        }
    }

    private String escapeHtml(String text) {
        return text == null ? "" : MessengerHtml.escape(text);
    }
}
