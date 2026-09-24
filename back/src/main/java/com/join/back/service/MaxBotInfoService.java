package com.join.back.service;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Caches metadata about the running MAX bot (currently just its username)
 * by calling {@code GET /me} once at startup. Used by clients that need to build
 * {@code https://max.ru/<bot>?startapp=...} deep links — e.g. friend-group QR
 * codes that must be scannable from any camera app — and by open_app buttons.
 *
 * <p>If the call fails (e.g. test profile, no network) we fall back to the
 * {@code max.bot-username} property so dev/CI keep working.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MaxBotInfoService {

    private final MaxBotApiClient maxBotApiClient;

    @Value("${max.bot-username:}")
    private String configuredUsername;

    private volatile String username;

    @PostConstruct
    void resolveUsername() {
        String fallback = configuredUsername == null ? "" : configuredUsername;
        if (!maxBotApiClient.isConfigured()) {
            this.username = fallback;
            log.info("Bot info: token absent, using configured fallback username='{}'", username);
            return;
        }
        try {
            JsonNode me = maxBotApiClient.getMe();
            if (me == null || !me.hasNonNull("username")) {
                this.username = fallback;
                log.warn("Bot info: /me returned no username, fallback='{}'", username);
                return;
            }
            this.username = me.get("username").asText();
            log.info("Bot info: resolved username='{}' via /me", username);
        } catch (Exception e) {
            this.username = fallback;
            log.warn("Bot info: /me failed ({}), fallback='{}'", e.getMessage(), username);
        }
    }

    /**
     * @return bot username without the leading {@code @}, or empty string if
     *         neither {@code /me} succeeded nor {@code max.bot-username} is set.
     */
    public String getUsername() {
        return username == null ? "" : username;
    }
}
