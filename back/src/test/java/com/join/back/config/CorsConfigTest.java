package com.join.back.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class CorsConfigTest {

    @Test
    void allowsOwnDomainAndAndroidAppOnly() {
        CorsConfig config = new CorsConfig();
        ReflectionTestUtils.setField(config, "allowedOrigins", "https://localhost, capacitor://localhost");
        ReflectionTestUtils.setField(config, "maxWebhookUrl", "https://join.example.ru");
        ReflectionTestUtils.setField(config, "telegramWebAppUrl", "https://join.example.ru:8443/app");

        assertThat(config.originPatterns()).containsExactly(
                "https://localhost", "capacitor://localhost", "https://join.example.ru", "https://join.example.ru:8443");
    }

    @Test
    void ignoresMissingOrInvalidUrls() {
        CorsConfig config = new CorsConfig();
        ReflectionTestUtils.setField(config, "allowedOrigins", "https://localhost");
        ReflectionTestUtils.setField(config, "maxWebhookUrl", "");
        ReflectionTestUtils.setField(config, "telegramWebAppUrl", "not a url");

        assertThat(config.originPatterns()).containsExactly("https://localhost");
    }
}
