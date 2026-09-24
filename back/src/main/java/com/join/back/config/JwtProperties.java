package com.join.back.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "join.jwt")
public record JwtProperties(
        String secret,
        @DefaultValue("30") int ttlDays
) {
}
