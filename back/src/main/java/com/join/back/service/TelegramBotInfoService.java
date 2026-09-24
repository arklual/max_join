package com.join.back.service;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Caches the Telegram bot username (via getMe at startup) for
 * {@code https://t.me/<bot>?start=...} invite links. Falls back to
 * {@code telegram.bot-username}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramBotInfoService {

    private final TelegramBotApiClient telegramBotApiClient;

    @Value("${telegram.bot-username:}")
    private String configuredUsername;

    private volatile String username;

    @PostConstruct
    void resolveUsername() {
        String fallback = configuredUsername == null ? "" : configuredUsername;
        if (!telegramBotApiClient.isConfigured()) {
            this.username = fallback;
            return;
        }
        try {
            JsonNode me = telegramBotApiClient.getMe();
            this.username = me.hasNonNull("username") ? me.get("username").asText() : fallback;
            log.info("Telegram bot info: username='{}'", username);
        } catch (Exception e) {
            this.username = fallback;
            log.warn("Telegram bot info: getMe failed ({}), fallback='{}'", e.getMessage(), username);
        }
    }

    public String getUsername() {
        return username == null ? "" : username;
    }
}
