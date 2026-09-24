package com.join.back.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.net.URI;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Cross-origin access is limited to the mini app's own domain (taken from the public URLs the bot
 * already knows), the Android app (Capacitor serves it from https://localhost) and extra origins
 * from {@code join.cors.allowed-origins}.
 */
@Configuration
public class CorsConfig {

    @Value("${join.cors.allowed-origins:https://localhost,capacitor://localhost,http://localhost:*}")
    private String allowedOrigins;

    @Value("${max.webhook-url:}")
    private String maxWebhookUrl;

    @Value("${telegram.webapp-url:}")
    private String telegramWebAppUrl;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.copyOf(originPatterns()));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    Set<String> originPatterns() {
        Set<String> origins = new LinkedHashSet<>();
        Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .forEach(origins::add);
        addOriginOf(maxWebhookUrl, origins);
        addOriginOf(telegramWebAppUrl, origins);
        return origins;
    }

    private static void addOriginOf(String url, Set<String> origins) {
        if (url == null || url.isBlank()) {
            return;
        }
        try {
            URI uri = URI.create(url.trim());
            if (uri.getScheme() != null && uri.getHost() != null) {
                origins.add(uri.getScheme() + "://" + uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : ""));
            }
        } catch (IllegalArgumentException ignored) {
            // not a URL — nothing to add
        }
    }
}
