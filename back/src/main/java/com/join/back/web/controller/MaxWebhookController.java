package com.join.back.web.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.join.back.service.MaxBotApiClient;
import com.join.back.service.MaxBotInfoService;
import com.join.back.service.MaxLinkService;
import com.join.back.service.MaxLoginService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Обработчик MAX Webhook — принимает входящие обновления от MAX
 * (bot_started, message_created) и отвечает на старт бота, /start и /help.
 */
@RestController
@RequestMapping("/api/max")
@RequiredArgsConstructor
public class MaxWebhookController {

    private static final Logger log = LoggerFactory.getLogger(MaxWebhookController.class);
    private static final String SECRET_HEADER = "X-Max-Bot-Api-Secret";
    private static final String INVITE_PREFIX = "join_";

    private final MaxBotApiClient maxBotApiClient;
    private final MaxBotInfoService maxBotInfoService;
    private final MaxLinkService maxLinkService;
    private final MaxLoginService maxLoginService;

    @Value("${max.webhook-secret:}")
    private String webhookSecret;

    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestHeader(value = SECRET_HEADER, required = false) String secret,
            @RequestBody JsonNode update) {
        if (!isSecretValid(secret)) {
            log.warn("MAX webhook: rejected update with invalid secret");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("invalid secret");
        }
        try {
            String updateType = update.path("update_type").asText("");
            switch (updateType) {
                case "bot_started" -> {
                    long chatId = update.path("chat_id").asLong();
                    long userId = update.path("user").path("user_id").asLong();
                    handleStart(chatId, userId, update.path("payload").asText(""));
                }
                case "message_created" -> {
                    JsonNode message = update.path("message");
                    long chatId = message.path("recipient").path("chat_id").asLong();
                    long userId = message.path("sender").path("user_id").asLong();
                    String text = message.path("body").path("text").asText("").trim();
                    if (text.equals("/start") || text.startsWith("/start ")) {
                        handleStart(chatId, userId, text.substring("/start".length()).trim());
                    } else if (text.equals("/help")) {
                        sendHelpMessage(chatId);
                    }
                }
                default -> log.debug("MAX webhook: ignoring update_type={}", updateType);
            }
        } catch (Exception e) {
            log.error("Error processing MAX webhook: {}", e.getMessage(), e);
        }

        return ResponseEntity.ok("ok");
    }

    private boolean isSecretValid(String received) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            return true;
        }
        return received != null && MessageDigest.isEqual(
                webhookSecret.getBytes(StandardCharsets.UTF_8),
                received.getBytes(StandardCharsets.UTF_8));
    }

    private void handleStart(long chatId, long maxUserId, String payload) {
        if (payload.startsWith(MaxLoginService.PAYLOAD_PREFIX) && maxUserId != 0) {
            handleLogin(chatId, maxUserId, payload.substring(MaxLoginService.PAYLOAD_PREFIX.length()).trim());
            return;
        }
        if (payload.startsWith(MaxLinkService.PAYLOAD_PREFIX) && maxUserId != 0) {
            handleLink(chatId, maxUserId, payload.substring(MaxLinkService.PAYLOAD_PREFIX.length()).trim());
            return;
        }
        if (payload.startsWith(INVITE_PREFIX)) {
            String inviteCode = payload.substring(INVITE_PREFIX.length()).trim();
            if (inviteCode.matches("[A-Za-z0-9_-]{1,64}")) {
                sendFriendGroupInviteMessage(chatId, inviteCode.toLowerCase());
                return;
            }
        }
        sendStartMessage(chatId);
    }

    private void handleLogin(long chatId, long maxUserId, String token) {
        var user = maxLoginService.confirm(token, maxUserId);
        String text;
        if (user == null) {
            text = "Ссылка для входа устарела или уже использована. Нажмите «Войти через MAX» в приложении ещё раз.";
        } else if (user.isPresent()) {
            String name = user.get().getFirstName() != null ? ", " + user.get().getFirstName() : "";
            text = "✅ Вход выполнен" + name + "! Вернитесь в приложение JOIN — оно уже открывает ваш профиль.";
        } else {
            text = "👋 Почти готово! Вернитесь в приложение JOIN и заполните профиль — он будет привязан к этому аккаунту MAX, пароль не понадобится.";
        }
        maxBotApiClient.sendMessageToChat(chatId, text, null, null);
    }

    private void handleLink(long chatId, long maxUserId, String token) {
        MaxLinkService.Outcome outcome = maxLinkService.link(token, maxUserId);
        String name = outcome.user() != null && outcome.user().getFirstName() != null
                ? outcome.user().getFirstName() : "";
        String text = switch (outcome.result()) {
            case LINKED, ALREADY_LINKED -> "✅ Готово" + (name.isEmpty() ? "" : ", " + name) + "! MAX привязан к вашему аккаунту JOIN.\n\n"
                    + "Теперь уведомления о новых метчах и сообщениях будут приходить сюда, "
                    + "а JOIN можно открывать прямо в MAX — с тем же профилем, чатами и группами.";
            case TAKEN_BY_ANOTHER -> "Этот аккаунт MAX уже привязан к другому профилю JOIN, поэтому привязать его ещё раз нельзя.\n\n"
                    + "Если это ошибка — напишите в поддержку внутри приложения.";
            case EXPIRED -> "Ссылка для привязки устарела или уже использована. "
                    + "Откройте профиль в JOIN и нажмите «Привязать MAX» ещё раз.";
        };
        maxBotApiClient.sendMessageToChat(chatId, text, null, List.of(List.of(
                MaxBotApiClient.openAppButton("Открыть JOIN", maxBotInfoService.getUsername(), null))));
        log.info("MAX link attempt for MAX user {}: {}", maxUserId, outcome.result());
    }

    private void sendStartMessage(long chatId) {
        String welcomeText = """
                👋 Привет! Добро пожаловать в JOIN!

                JOIN — это приложение для поиска мероприятий и знакомств. Здесь ты можешь находить интересные события, ставить лайки и общаться с людьми рядом.

                ⚙️Как открыть приложение:

                1. Нажми кнопку «Присоединиться к JOIN» ниже
                2. Или нажми кнопку «Открыть» мини-приложения в профиле бота

                Удачных знакомств! 🎉""";

        maxBotApiClient.sendMessageToChat(chatId, welcomeText, null, List.of(List.of(
                MaxBotApiClient.openAppButton("🚀 Присоединиться к JOIN", maxBotInfoService.getUsername(), null)
        )));
        log.info("Sent welcome message to chat {}", chatId);
    }

    private void sendFriendGroupInviteMessage(long chatId, String inviteCode) {
        String text = "Тебя пригласили в группу друзей в JOIN!\n\n"
                + "Нажми кнопку ниже — мы автоматически добавим тебя в группу.";

        // The payload arrives in the mini app as initDataUnsafe.start_param,
        // which SplashScreen turns into the auto-join dialog.
        maxBotApiClient.sendMessageToChat(chatId, text, null, List.of(List.of(
                MaxBotApiClient.openAppButton("Вступить в группу", maxBotInfoService.getUsername(),
                        INVITE_PREFIX + inviteCode)
        )));
        log.info("Sent friend-group invite button to chat {} for code {}", chatId, inviteCode);
    }

    private void sendHelpMessage(long chatId) {
        String helpText = """
                ℹ️ **JOIN — Помощь**

                JOIN — приложение для поиска мероприятий и знакомств.

                **Как открыть приложение:**
                • Нажми кнопку **«Открыть»** мини-приложения в профиле бота
                • Или отправь /start и нажми кнопку **«Присоединиться к JOIN»**

                **Что можно делать в приложении:**
                • 🔍 Искать мероприятия в своём городе
                • ❤️ Ставить лайки и находить пары
                • 💬 Общаться в чатах
                • 👥 Вступать в группы по интересам

                По вопросам и предложениям — пиши в поддержку внутри приложения.""";

        maxBotApiClient.sendMessageToChat(chatId, helpText, "markdown", null);
    }
}
