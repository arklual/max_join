package com.join.back.config;

import com.join.back.service.TelegramBotApiClient;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

/**
 * Configures the Telegram bot on startup (only when a token is set): commands,
 * the "Открыть JOIN" menu button pointing at the mini app, and the webhook.
 */
@Configuration
@RequiredArgsConstructor
public class TelegramBotSetupConfig {

    private static final Logger log = LoggerFactory.getLogger(TelegramBotSetupConfig.class);

    private final TelegramBotApiClient telegramBotApiClient;

    @Value("${telegram.webapp-url:}")
    private String webappUrl;

    @Value("${telegram.webhook-url:}")
    private String webhookUrl;

    @Value("${telegram.webhook-secret:}")
    private String webhookSecret;

    @PostConstruct
    public void setupBot() {
        if (!telegramBotApiClient.isConfigured()) {
            log.info("Telegram bot token not configured — Telegram integration disabled");
            return;
        }
        try {
            telegramBotApiClient.setMyCommands(List.of(
                    Map.of("command", "start", "description", "Запустить приложение JOIN"),
                    Map.of("command", "help", "description", "Помощь и информация")));
            if (webappUrl != null && !webappUrl.isBlank()) {
                telegramBotApiClient.setWebAppMenuButton("🎉 Открыть JOIN", webappUrl);
            }
            if (webhookUrl != null && !webhookUrl.isBlank()) {
                telegramBotApiClient.setWebhook(webhookUrl + "/api/telegram/webhook", List.of("message"), webhookSecret);
                log.info("Telegram webhook set: {}/api/telegram/webhook", webhookUrl);
            }
            log.info("✅ Telegram bot configured (webapp: {})", webappUrl);
        } catch (Exception e) {
            log.error("Failed to configure Telegram bot: {}", e.getMessage());
        }
    }
}
