package com.join.back.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "icebreaker")
@Getter
@Setter
public class IceBreakerProperties {

    private boolean enabled = true;
    private String apiKey;
    /** Base URL of an OpenAI-compatible API (the /chat/completions path is appended). */
    private String baseUrl = "https://api.aiproductiv.ru/v1";
    private String model = "gpt-6-luna";
    private int suggestionsCount = 3;
}
