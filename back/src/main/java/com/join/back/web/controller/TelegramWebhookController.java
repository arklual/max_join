package com.join.back.web.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.join.back.model.entity.Event;
import com.join.back.service.DeepLinks;
import com.join.back.service.PushkinPicksService;
import com.join.back.service.TelegramBotApiClient;
import com.join.back.util.MessengerHtml;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Telegram webhook: answers /start (optionally with a friend-group invite
 * payload "join_<code>") and /help with a button that opens the mini app.
 */
@RestController
@RequestMapping("/api/telegram")
@RequiredArgsConstructor
public class TelegramWebhookController {

    private static final Logger log = LoggerFactory.getLogger(TelegramWebhookController.class);
    private static final String SECRET_HEADER = "X-Telegram-Bot-Api-Secret-Token";
    private static final String INVITE_PREFIX = "join_";

    private final TelegramBotApiClient telegramBotApiClient;
    private final PushkinPicksService pushkinPicksService;

    @Value("${telegram.webapp-url:}")
    private String webappUrl;

    @Value("${telegram.webhook-secret:}")
    private String webhookSecret;

    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestHeader(value = SECRET_HEADER, required = false) String secret,
            @RequestBody JsonNode update) {
        if (!isSecretValid(secret)) {
            log.warn("Telegram webhook: rejected update with invalid secret");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("invalid secret");
        }
        try {
            JsonNode message = update.path("message");
            if (!message.isMissingNode()) {
                long chatId = message.path("chat").path("id").asLong();
                long userId = message.path("from").path("id").asLong();
                String text = message.path("text").asText("").trim();
                if (text.equals("/start") || text.startsWith("/start ")) {
                    handleStart(chatId, userId, text.substring("/start".length()).trim());
                } else if (text.equals("/help")) {
                    sendHelpMessage(chatId);
                } else if (text.equals("/pushkin")) {
                    sendPushkinPicks(chatId, userId);
                }
            }
        } catch (Exception e) {
            log.error("Error processing Telegram webhook: {}", e.getMessage(), e);
        }
        return ResponseEntity.ok("ok");
    }

    private boolean isSecretValid(String received) {
        if (webhookSecret == null || webhookSecret.isBlank()) return true;
        return received != null && MessageDigest.isEqual(
                webhookSecret.getBytes(StandardCharsets.UTF_8), received.getBytes(StandardCharsets.UTF_8));
    }

    private void handleStart(long chatId, long telegramUserId, String payload) {
        if (payload.startsWith(INVITE_PREFIX)) {
            String code = payload.substring(INVITE_PREFIX.length()).trim();
            if (code.matches("[A-Za-z0-9_-]{1,64}")) {
                sendInvite(chatId, code.toLowerCase());
                return;
            }
        }
        sendStartMessage(chatId, telegramUserId == 0 || pushkinPicksService.appliesToTelegramUser(telegramUserId));
    }

    private void sendStartMessage(long chatId, boolean showPushkin) {
        String text = """
                👋 Привет! Это JOIN — здесь находят, с кем сходить на концерт, спектакль или выставку.

                Как это работает:
                1. Открой афишу и лайкни события, на которые хочешь пойти.
                2. Когда на то же событие захочет кто-то ещё, я пришлю тебе напарника.
                3. Договоритесь в чате — а накануне я напомню о встрече.""";
        if (showPushkin) {
            text += "\n\n🎭 Тебе 14–22? Отмечаем события, которые можно оплатить Пушкинской картой.";
        }
        List<List<java.util.Map<String, Object>>> keyboard = null;
        if (webappUrl != null && !webappUrl.isBlank()) {
            keyboard = new java.util.ArrayList<>();
            keyboard.add(List.of(TelegramBotApiClient.webAppButton("🚀 Открыть афишу", webappUrl)));
            if (showPushkin) {
                keyboard.add(List.of(TelegramBotApiClient.webAppButton("🎭 По Пушкинской карте", deepLink(DeepLinks.PUSHKIN))));
            }
        }
        telegramBotApiClient.sendMessage(chatId, text, null, keyboard);
        log.info("Sent Telegram welcome to chat {}", chatId);
    }

    private void sendPushkinPicks(long chatId, long telegramUserId) {
        if (!pushkinPicksService.appliesToTelegramUser(telegramUserId)) {
            telegramBotApiClient.sendMessage(chatId, MaxWebhookController.PUSHKIN_NOT_ELIGIBLE, null,
                    keyboard("🚀 Открыть афишу", webappUrl));
            return;
        }
        PushkinPicksService.Picks picks = pushkinPicksService.forTelegramUser(telegramUserId);
        if (picks.events().isEmpty()) {
            telegramBotApiClient.sendMessage(chatId,
                    "Пока нет ближайших событий по Пушкинской карте — загляни в афишу чуть позже.", null,
                    keyboard("🚀 Открыть афишу", webappUrl));
            return;
        }
        StringBuilder text = new StringBuilder("🎭 <b>Ближайшие события по Пушкинской карте")
                .append(picks.city() != null ? " — " + MessengerHtml.escape(picks.city()) : "")
                .append("</b>\n");
        List<List<java.util.Map<String, Object>>> keyboard = new java.util.ArrayList<>();
        int n = 1;
        for (Event event : picks.events()) {
            text.append("\n").append(n).append(". ").append(MessengerHtml.escape(event.getTitle()))
                    .append(" — ").append(event.getEventDate().format(PICK_DATE));
            if (event.getEventTime() != null) {
                text.append(", ").append(event.getEventTime().format(PICK_TIME));
            }
            if (picks.city() == null && event.getCity() != null) {
                text.append(" (").append(MessengerHtml.escape(event.getCity())).append(")");
            }
            String price = PushkinPicksService.priceLabel(event);
            if (price != null) {
                text.append(" · ").append(price);
            }
            String title = event.getTitle().length() > 40 ? event.getTitle().substring(0, 39) + "…" : event.getTitle();
            keyboard.add(List.of(TelegramBotApiClient.webAppButton(n + ". " + title, deepLink(DeepLinks.event(event.getId())))));
            n++;
        }
        text.append("\n\nЛайкни событие в приложении — я найду, с кем на него сходить.");
        keyboard.add(List.of(TelegramBotApiClient.webAppButton("Все события по карте", deepLink(DeepLinks.PUSHKIN))));
        telegramBotApiClient.sendMessage(chatId, text.toString(), "HTML", keyboard);
    }

    private static final java.time.format.DateTimeFormatter PICK_DATE =
            java.time.format.DateTimeFormatter.ofPattern("d MMMM", java.util.Locale.forLanguageTag("ru"));
    private static final java.time.format.DateTimeFormatter PICK_TIME = java.time.format.DateTimeFormatter.ofPattern("HH:mm");

    /** The mini app reads {@code ?startapp=} the same way as MAX start_param. */
    private String deepLink(String payload) {
        String base = webappUrl.endsWith("/") ? webappUrl : webappUrl + "/";
        return base + "?startapp=" + payload;
    }

    private void sendInvite(long chatId, String code) {
        String separator = webappUrl.contains("?") ? "&" : "?";
        String text = "Тебя пригласили в группу друзей в JOIN!\n\nНажми кнопку ниже — мы автоматически добавим тебя в группу.";
        telegramBotApiClient.sendMessage(chatId, text, null,
                keyboard("Вступить в группу", webappUrl + separator + "invite=" + code));
        log.info("Sent Telegram friend-group invite to chat {} for code {}", chatId, code);
    }

    private void sendHelpMessage(long chatId) {
        String text = """
                ℹ️ <b>JOIN — помощь</b>

                /start — как работает JOIN и кнопка входа
                /pushkin — ближайшие события по Пушкинской карте

                Сюда же приходят уведомления: найден напарник, новые сообщения и напоминание накануне события. \
                Кнопка в уведомлении сразу открывает нужный чат.

                Вопрос или проблема — напиши в поддержку в профиле приложения.""";
        telegramBotApiClient.sendMessage(chatId, text, "HTML", keyboard("🚀 Открыть JOIN", webappUrl));
    }

    private List<List<java.util.Map<String, Object>>> keyboard(String label, String url) {
        if (url == null || url.isBlank()) return null;
        return List.of(List.of(TelegramBotApiClient.webAppButton(label, url)));
    }
}
