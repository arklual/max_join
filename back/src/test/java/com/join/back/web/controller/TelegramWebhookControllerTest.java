package com.join.back.web.controller;

import com.join.back.service.TelegramBotApiClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TelegramWebhookController.class,
        excludeAutoConfiguration = {org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class})
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@TestPropertySource(properties = {"telegram.webhook-secret=tg-secret", "telegram.webapp-url=https://join.example"})
class TelegramWebhookControllerTest {

    private static final String SECRET_HEADER = "X-Telegram-Bot-Api-Secret-Token";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TelegramBotApiClient telegramBotApiClient;

    @Test
    @SuppressWarnings("unchecked")
    void startWithInvitePayloadSendsWebAppButtonWithInviteUrl() throws Exception {
        mockMvc.perform(post("/api/telegram/webhook")
                        .header(SECRET_HEADER, "tg-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"update_id\":1,\"message\":{\"chat\":{\"id\":42},\"text\":\"/start join_AbC123\"}}"))
                .andExpect(status().isOk());

        ArgumentCaptor<List<List<Map<String, Object>>>> keyboard = ArgumentCaptor.forClass(List.class);
        verify(telegramBotApiClient).sendMessage(eq(42L), any(), isNull(), keyboard.capture());
        Map<String, Object> button = keyboard.getValue().get(0).get(0);
        assertThat(button.get("web_app")).isEqualTo(Map.of("url", "https://join.example?invite=abc123"));
    }

    @Test
    void rejectsWrongSecret() throws Exception {
        mockMvc.perform(post("/api/telegram/webhook")
                        .header(SECRET_HEADER, "nope")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":{\"chat\":{\"id\":1},\"text\":\"/start\"}}"))
                .andExpect(status().isUnauthorized());

        verify(telegramBotApiClient, never()).sendMessage(anyLong(), any(), any(), any());
    }
}
