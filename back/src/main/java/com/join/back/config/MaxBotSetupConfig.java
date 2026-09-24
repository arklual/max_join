package com.join.back.config;

import com.join.back.service.MaxBotApiClient;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

/**
 * Автоматическая настройка MAX бота при старте приложения:
 * - Устанавливает команды бота (/start, /help)
 * - Подписывает бота на webhook
 *
 * Сам мини-апп (URL веб-приложения) привязывается к боту в кабинете
 * платформы MAX для партнёров — через Bot API это не настраивается.
 */
@Configuration
@RequiredArgsConstructor
public class MaxBotSetupConfig {

    private static final Logger log = LoggerFactory.getLogger(MaxBotSetupConfig.class);

    private final MaxBotApiClient maxBotApiClient;

    @Value("${max.webhook-url:}")
    private String webhookUrl;

    @Value("${max.webhook-secret:}")
    private String webhookSecret;

    @PostConstruct
    public void setupBot() {
        if (!maxBotApiClient.isConfigured()) {
            log.warn("MAX bot token not configured — skipping bot setup");
            return;
        }

        try {
            setupCommands();
            setupWebhook();
            log.info("✅ MAX bot configured successfully");
        } catch (Exception e) {
            log.error("Failed to configure MAX bot: {}", e.getMessage());
        }
    }

    private void setupCommands() {
        maxBotApiClient.setCommands(List.of(
                Map.of("name", "start", "description", "Как работает JOIN и вход в приложение"),
                Map.of("name", "pushkin", "description", "Ближайшие события по Пушкинской карте"),
                Map.of("name", "help", "description", "Помощь")
        ));
        log.info("MAX bot commands set: /start, /pushkin, /help");
    }

    private void setupWebhook() {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.info("max.webhook-url not set — skipping webhook setup");
            return;
        }

        String fullWebhookUrl = webhookUrl + "/api/max/webhook";
        maxBotApiClient.subscribeWebhook(fullWebhookUrl, List.of("bot_started", "message_created"), webhookSecret);
        log.info("MAX webhook set: {}", fullWebhookUrl);
    }
}
