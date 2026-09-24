package com.join.back.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.join.back.util.MessengerHtml;

import java.util.List;

/** Sends notifications to users in their private chat with the Telegram bot. */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramNotificationService {

    private final TelegramBotApiClient telegramBotApiClient;

    @Value("${telegram.webapp-url:}")
    private String webappUrl;

    public void sendMatchNotification(Long telegramId, String companionName, String eventTitle) {
        send(telegramId, String.format("""
                🎉 Напарник найден!

                Тебе подобрали напарника на мероприятие «%s»!
                Твой напарник — <b>%s</b>

                Заходи в чат в приложении, чтобы узнать подробности и начать общение 👇""",
                esc(eventTitle), esc(companionName)));
    }

    public void sendChatMessageNotification(Long telegramId, String senderName, String messageText) {
        send(telegramId, String.format("💬 Новое сообщение от <b>%s</b>\n\n%s",
                esc(senderName != null ? senderName : "Пользователь"), esc(preview(messageText))));
    }

    public void sendGroupMessageNotification(Long telegramId, String senderName, String groupEventTitle, String messageText) {
        send(telegramId, String.format("👥 Новое сообщение в группе «%s»\n\n<b>%s</b>: %s",
                esc(groupEventTitle != null ? groupEventTitle : "Группа"),
                esc(senderName != null ? senderName : "Пользователь"), esc(preview(messageText))));
    }

    private void send(Long telegramId, String text) {
        if (telegramId == null || !telegramBotApiClient.isConfigured()) return;
        try {
            List<List<java.util.Map<String, Object>>> keyboard = webappUrl == null || webappUrl.isBlank()
                    ? null
                    : List.of(List.of(TelegramBotApiClient.webAppButton("💬 Открыть JOIN", webappUrl)));
            telegramBotApiClient.sendMessage(telegramId, text, "HTML", keyboard);
            log.info("Sent notification to Telegram user {}", telegramId);
        } catch (Exception e) {
            log.error("Failed to send notification to Telegram user {}: {}", telegramId, e.getMessage());
        }
    }

    private static String preview(String text) {
        if (text == null) return "";
        return text.length() > 100 ? text.substring(0, 100) + "…" : text;
    }

    private static String esc(String text) {
        return text == null ? "" : MessengerHtml.escape(text);
    }
}
