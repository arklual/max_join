package com.join.back.web.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.join.back.service.TelegramBotApiClient;
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
                String text = message.path("text").asText("").trim();
                if (text.equals("/start") || text.startsWith("/start ")) {
                    handleStart(chatId, text.substring("/start".length()).trim());
                } else if (text.equals("/help")) {
                    sendHelpMessage(chatId);
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

    private void handleStart(long chatId, String payload) {
        if (payload.startsWith(INVITE_PREFIX)) {
            String code = payload.substring(INVITE_PREFIX.length()).trim();
            if (code.matches("[A-Za-z0-9_-]{1,64}")) {
                sendInvite(chatId, code.toLowerCase());
                return;
            }
        }
        sendStartMessage(chatId);
    }

    private void sendStartMessage(long chatId) {
        String text = """
                👋 Привет! Добро пожаловать в JOIN!

                JOIN — это приложение для поиска мероприятий и знакомств. Здесь ты можешь находить интересные события, ставить лайки и общаться с людьми рядом.

                ⚙️Как открыть приложение:

                1. Нажми кнопку «Присоединиться к JOIN» ниже
                2. Или нажми кнопку «Открыть JOIN» в меню бота (слева от поля ввода)

                Удачных знакомств! 🎉""";
        telegramBotApiClient.sendMessage(chatId, text, null, keyboard("🚀 Присоединиться к JOIN", webappUrl));
        log.info("Sent Telegram welcome to chat {}", chatId);
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
                ℹ️ <b>JOIN — Помощь</b>

                JOIN — приложение для поиска мероприятий и знакомств.

                <b>Как открыть приложение:</b>
                • Нажми кнопку <b>«Открыть JOIN»</b> в меню бота (слева от поля ввода)
                • Или отправь /start и нажми <b>«Присоединиться к JOIN»</b>

                <b>Что можно делать в приложении:</b>
                • 🔍 Искать мероприятия в своём городе
                • ❤️ Ставить лайки и находить пары
                • 💬 Общаться в чатах
                • 👥 Вступать в группы по интересам

                По вопросам и предложениям — пиши в поддержку внутри приложения.""";
        telegramBotApiClient.sendMessage(chatId, text, "HTML", null);
    }

    private List<List<java.util.Map<String, Object>>> keyboard(String label, String url) {
        if (url == null || url.isBlank()) return null;
        return List.of(List.of(TelegramBotApiClient.webAppButton(label, url)));
    }
}
