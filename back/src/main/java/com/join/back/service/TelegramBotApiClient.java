package com.join.back.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Minimal client for the Telegram Bot API (https://core.telegram.org/bots/api). */
@Component
public class TelegramBotApiClient {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${telegram.bot-token:}")
    private String botToken;

    @Value("${telegram.api-url:https://api.telegram.org}")
    private String apiUrl;

    public boolean isConfigured() {
        return botToken != null && !botToken.isBlank();
    }

    /** getMe — returns the "result" object (id, username, …). */
    public JsonNode getMe() {
        return call("getMe", Map.of()).path("result");
    }

    /**
     * sendMessage to a chat (for private chats chat_id == user id).
     *
     * @param parseMode "HTML", "Markdown" or null
     * @param keyboard  rows of inline-keyboard buttons, or null
     */
    public void sendMessage(long chatId, String text, String parseMode, List<List<Map<String, Object>>> keyboard) {
        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text", text);
        if (parseMode != null) body.put("parse_mode", parseMode);
        if (keyboard != null && !keyboard.isEmpty()) body.put("reply_markup", Map.of("inline_keyboard", keyboard));
        call("sendMessage", body);
    }

    public void setMyCommands(List<Map<String, String>> commands) {
        call("setMyCommands", Map.of("commands", commands));
    }

    /** Menu button (left of the input field) that opens the mini app. */
    public void setWebAppMenuButton(String text, String webAppUrl) {
        call("setChatMenuButton", Map.of("menu_button", Map.of(
                "type", "web_app", "text", text, "web_app", Map.of("url", webAppUrl))));
    }

    public void setWebhook(String url, List<String> allowedUpdates, String secretToken) {
        Map<String, Object> body = new HashMap<>();
        body.put("url", url);
        body.put("allowed_updates", allowedUpdates);
        if (secretToken != null && !secretToken.isBlank()) body.put("secret_token", secretToken);
        call("setWebhook", body);
    }

    /** Inline button that opens the mini app at the given URL. */
    public static Map<String, Object> webAppButton(String text, String url) {
        return Map.of("text", text, "web_app", Map.of("url", url));
    }

    private JsonNode call(String method, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(apiUrl + "/bot" + botToken + "/" + method, HttpMethod.POST,
                new HttpEntity<>(body, headers), JsonNode.class).getBody();
    }
}
