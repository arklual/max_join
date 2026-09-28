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

    public void sendMatchNotification(Long telegramId, String companionName, String eventTitle, Long matchId) {
        send(telegramId, String.format("""
                🎉 Нашлась компания!

                На «%s» тоже хочет пойти <b>%s</b>.

                Позовите пойти вместе — чат откроется, когда придёт согласие 👇""",
                esc(eventTitle), esc(companionName)), "🤝 Позвать пойти вместе", DeepLinks.MATCHES);
    }

    public void sendContactRequest(Long telegramId, String requesterName, String eventTitle, Long matchId) {
        send(telegramId, String.format("🤝 <b>%s</b> зовёт вас пойти на «%s» вместе.",
                esc(requesterName), esc(eventTitle)), "Ответить", DeepLinks.MATCHES);
    }

    public void sendContactAccepted(Long telegramId, String companionName, String eventTitle, Long chatId) {
        send(telegramId, String.format("✅ <b>%s</b> — «пойдём!» на «%s». Чат открыт: договоритесь, где встретиться.",
                esc(companionName), esc(eventTitle)), "💬 Написать", DeepLinks.chat(chatId));
    }

    public void sendChatMessageNotification(Long telegramId, String senderName, String messageText, Long chatId) {
        send(telegramId, String.format("💬 Новое сообщение от <b>%s</b>\n\n%s",
                esc(senderName != null ? senderName : "собеседника"), esc(preview(messageText))),
                "💬 Ответить", DeepLinks.chat(chatId));
    }

    public void sendGroupMessageNotification(Long telegramId, String senderName, String groupEventTitle,
                                             String messageText, Long groupChatId) {
        send(telegramId, String.format("👥 Новое сообщение в группе «%s»\n\n<b>%s</b>: %s",
                esc(groupEventTitle != null ? groupEventTitle : "Группа"),
                esc(senderName != null ? senderName : "Участник"), esc(preview(messageText))),
                "👥 Открыть чат группы", DeepLinks.groupChat(groupChatId));
    }

    public void sendGroupInvite(Long telegramId, String inviterName, String eventTitle, String when, Long groupId) {
        send(telegramId, String.format("""
                👋 <b>%s</b> зовёт тебя в компанию на «%s»%s

                Загляни — там уже видно, кто ещё идёт 👇""",
                esc(inviterName != null ? inviterName : "Друг"), esc(eventTitle), when != null ? " — " + esc(when) : ""),
                "👥 Посмотреть компанию", DeepLinks.group(groupId));
    }

    public void sendEventReminder(Long telegramId, String companionName, String eventTitle, String when, Long chatId) {
        send(telegramId, String.format("""
                ⏰ Уже %s — «%s»

                Ты идёшь вместе с <b>%s</b>. Договоритесь, где встретиться 👇""",
                esc(when), esc(eventTitle), esc(companionName)), "💬 Написать", DeepLinks.chat(chatId));
    }

    public void sendOutingFeedback(Long telegramId, String companionName, String eventTitle, Long chatId) {
        send(telegramId, String.format("👋 Вчера было «%s». Сходили вместе с <b>%s</b>?",
                esc(eventTitle), esc(companionName)), "Ответить", DeepLinks.chat(chatId));
    }

    private void send(Long telegramId, String text, String buttonText, String payload) {
        if (telegramId == null || !telegramBotApiClient.isConfigured()) return;
        try {
            List<List<java.util.Map<String, Object>>> keyboard = webappUrl == null || webappUrl.isBlank()
                    ? null
                    : List.of(List.of(TelegramBotApiClient.webAppButton(buttonText, deepLinkUrl(payload))));
            telegramBotApiClient.sendMessage(telegramId, text, "HTML", keyboard);
            log.info("Sent notification to Telegram user {}", telegramId);
        } catch (Exception e) {
            log.error("Failed to send notification to Telegram user {}: {}", telegramId, e.getMessage());
        }
    }

    /** The mini app reads {@code ?startapp=} the same way as MAX start_param. */
    private String deepLinkUrl(String payload) {
        if (payload == null) return webappUrl;
        String base = webappUrl.endsWith("/") ? webappUrl : webappUrl + "/";
        return base + "?startapp=" + payload;
    }

    private static String preview(String text) {
        if (text == null) return "";
        return text.length() > 100 ? text.substring(0, 100) + "…" : text;
    }

    private static String esc(String text) {
        return text == null ? "" : MessengerHtml.escape(text);
    }
}
