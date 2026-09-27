package com.join.back.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal client for the MAX Bot API (https://dev.max.ru/docs-api).
 * Authorisation is the raw bot token in the {@code Authorization} header.
 */
@Slf4j
@Component
public class MaxBotApiClient {

    // JDK HttpClient factory: the default HttpURLConnection one can't send PATCH.
    private final RestTemplate restTemplate = new RestTemplate(new JdkClientHttpRequestFactory());

    @Value("${max.bot-token}")
    private String botToken;

    @Value("${max.api-url:https://platform-api.max.ru}")
    private String apiUrl;

    public boolean isConfigured() {
        return botToken != null && !botToken.isBlank() && !"test-bot-token".equals(botToken);
    }

    /** GET /me — bot info (user_id, username, name…). */
    public JsonNode getMe() {
        return exchange(HttpMethod.GET, "/me", null);
    }

    /**
     * POST /messages?user_id=… — sends a private message to a user.
     *
     * @param format   "markdown", "html" or null for plain text
     * @param keyboard rows of inline-keyboard buttons, or null
     */
    public void sendMessageToUser(long userId, String text, String format, List<List<Map<String, Object>>> keyboard) {
        sendMessage("user_id", userId, text, format, keyboard);
    }

    /** POST /messages?chat_id=… — sends a message to a chat (dialog or group). */
    public void sendMessageToChat(long chatId, String text, String format, List<List<Map<String, Object>>> keyboard) {
        sendMessage("chat_id", chatId, text, format, keyboard);
    }

    /** PATCH /me/commands — updates the bot's command list shown in the client. */
    public void setCommands(List<Map<String, String>> commands) {
        exchange(HttpMethod.PATCH, "/me/commands", Map.of("commands", commands));
    }

    /** POST /subscriptions — registers a webhook for the given update types. */
    public void subscribeWebhook(String url, List<String> updateTypes, String secret) {
        Map<String, Object> body = new HashMap<>();
        body.put("url", url);
        body.put("update_types", updateTypes);
        if (secret != null && !secret.isBlank()) {
            body.put("secret", secret);
        }
        exchange(HttpMethod.POST, "/subscriptions", body);
    }

    /** Inline button that opens the bot's mini app, optionally passing a start payload. */
    public static Map<String, Object> openAppButton(String text, String botUsername, String payload) {
        Map<String, Object> button = new HashMap<>();
        button.put("type", "open_app");
        button.put("text", text);
        if (botUsername != null && !botUsername.isBlank()) {
            button.put("web_app", botUsername);
        }
        if (payload != null && !payload.isBlank()) {
            button.put("payload", payload);
        }
        return button;
    }

    /** Inline button handled by the bot itself: a {@code message_callback} update with this payload. */
    public static Map<String, Object> callbackButton(String text, String payload) {
        return Map.of("type", "callback", "text", text, "payload", payload);
    }

    /**
     * POST /answers?callback_id=… — answers a callback button: replaces the message the button was in
     * with {@code text} and {@code keyboard} (null — no buttons).
     */
    public void answerCallback(String callbackId, String text, List<List<Map<String, Object>>> keyboard) {
        Map<String, Object> message = new HashMap<>();
        message.put("text", text);
        message.put("attachments", keyboard == null || keyboard.isEmpty() ? List.of() : List.of(Map.of(
                "type", "inline_keyboard",
                "payload", Map.of("buttons", keyboard))));
        exchange(HttpMethod.POST, "/answers?callback_id=" + java.net.URLEncoder.encode(callbackId,
                java.nio.charset.StandardCharsets.UTF_8), Map.of("message", message));
    }

    /** Inline button that opens an external URL. */
    public static Map<String, Object> linkButton(String text, String url) {
        return Map.of("type", "link", "text", text, "url", url);
    }

    private void sendMessage(String recipientParam, long recipientId, String text, String format,
                             List<List<Map<String, Object>>> keyboard) {
        Map<String, Object> body = new HashMap<>();
        body.put("text", text);
        if (format != null) {
            body.put("format", format);
        }
        if (keyboard != null && !keyboard.isEmpty()) {
            body.put("attachments", List.of(Map.of(
                    "type", "inline_keyboard",
                    "payload", Map.of("buttons", keyboard)
            )));
        }
        exchange(HttpMethod.POST, "/messages?" + recipientParam + "=" + recipientId, body);
    }

    private JsonNode exchange(HttpMethod method, String path, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, botToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(apiUrl + path, method, new HttpEntity<>(body, headers), JsonNode.class)
                .getBody();
    }
}
