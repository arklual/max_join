package com.join.back.web.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.join.back.model.entity.Event;
import com.join.back.service.DeepLinks;
import com.join.back.service.MaxBotApiClient;
import com.join.back.service.MaxBotInfoService;
import com.join.back.service.MaxLinkService;
import com.join.back.service.MaxLoginService;
import com.join.back.service.PushkinPicksService;
import com.join.back.util.MessengerHtml;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Обработчик MAX Webhook — принимает входящие обновления от MAX
 * (bot_started, message_created) и отвечает на старт бота, /start, /pushkin и /help.
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
    private final PushkinPicksService pushkinPicksService;

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
                    } else if (text.equals("/pushkin")) {
                        sendPushkinPicks(chatId, userId);
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
        sendStartMessage(chatId, maxUserId == 0 || pushkinPicksService.appliesToMaxUser(maxUserId));
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

    private void sendStartMessage(long chatId, boolean showPushkin) {
        String welcomeText = """
                👋 Привет! Это JOIN — здесь находят, с кем сходить на концерт, спектакль или выставку.

                Как это работает:
                1. Открой афишу и лайкни события, на которые хочешь пойти.
                2. Когда на то же событие захочет кто-то ещё, я пришлю тебе напарника.
                3. Договоритесь в чате — а накануне я напомню о встрече.""";
        List<List<Map<String, Object>>> keyboard = new ArrayList<>();
        keyboard.add(List.of(MaxBotApiClient.openAppButton("🚀 Открыть афишу", maxBotInfoService.getUsername(), null)));
        if (showPushkin) {
            welcomeText += "\n\n🎭 Тебе 14–22? Отмечаем события, которые можно оплатить Пушкинской картой.";
            keyboard.add(List.of(MaxBotApiClient.openAppButton("🎭 По Пушкинской карте", maxBotInfoService.getUsername(),
                    DeepLinks.PUSHKIN)));
        }

        maxBotApiClient.sendMessageToChat(chatId, welcomeText, null, keyboard);
        log.info("Sent welcome message to chat {}", chatId);
    }

    static final String PUSHKIN_NOT_ELIGIBLE = "Пушкинская карта действует с 14 до 22 лет, поэтому подборку по ней не показываю. "
            + "Зато в афише полно всего остального 👇";

    private static final DateTimeFormatter PICK_DATE = DateTimeFormatter.ofPattern("d MMMM", Locale.forLanguageTag("ru"));
    private static final DateTimeFormatter PICK_TIME = DateTimeFormatter.ofPattern("HH:mm");

    private void sendPushkinPicks(long chatId, long maxUserId) {
        String bot = maxBotInfoService.getUsername();
        if (!pushkinPicksService.appliesToMaxUser(maxUserId)) {
            maxBotApiClient.sendMessageToChat(chatId, PUSHKIN_NOT_ELIGIBLE, null,
                    List.of(List.of(MaxBotApiClient.openAppButton("🚀 Открыть афишу", bot, null))));
            return;
        }
        PushkinPicksService.Picks picks = pushkinPicksService.forMaxUser(maxUserId);
        if (picks.events().isEmpty()) {
            maxBotApiClient.sendMessageToChat(chatId,
                    "Пока нет ближайших событий по Пушкинской карте — загляни в афишу чуть позже.", null,
                    List.of(List.of(MaxBotApiClient.openAppButton("🚀 Открыть афишу", bot, null))));
            return;
        }

        StringBuilder text = new StringBuilder("🎭 <b>Ближайшие события по Пушкинской карте")
                .append(picks.city() != null ? " — " + MessengerHtml.escape(picks.city()) : "")
                .append("</b>\n");
        List<List<Map<String, Object>>> keyboard = new ArrayList<>();
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
            keyboard.add(List.of(MaxBotApiClient.openAppButton(n + ". " + shorten(event.getTitle(), 40), bot,
                    DeepLinks.event(event.getId()))));
            n++;
        }
        text.append("\n\nЛайкни событие в приложении — я найду, с кем на него сходить.");
        keyboard.add(List.of(MaxBotApiClient.openAppButton("Все события по карте", bot, DeepLinks.PUSHKIN)));
        maxBotApiClient.sendMessageToChat(chatId, text.toString(), "html", keyboard);
    }

    private static String shorten(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
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
                ℹ️ <b>JOIN — помощь</b>

                /start — как работает JOIN и кнопка входа
                /pushkin — ближайшие события по Пушкинской карте

                Сюда же приходят уведомления: найден напарник, новые сообщения и напоминание накануне события. \
                Кнопка в уведомлении сразу открывает нужный чат.

                Вопрос или проблема — напиши в поддержку в профиле приложения.""";

        maxBotApiClient.sendMessageToChat(chatId, helpText, "html", List.of(List.of(
                MaxBotApiClient.openAppButton("🚀 Открыть JOIN", maxBotInfoService.getUsername(), null))));
    }
}
