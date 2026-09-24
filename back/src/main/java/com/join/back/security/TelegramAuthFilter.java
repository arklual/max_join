package com.join.back.security;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Telegram mini app: signed init data in {@code X-Telegram-Init-Data}. */
public class TelegramAuthFilter extends MessengerAuthFilter {

    public static final String HEADER = "X-Telegram-Init-Data";

    public TelegramAuthFilter(String botToken, WebAppInitDataValidator initDataValidator, ObjectMapper objectMapper) {
        super(Messenger.TELEGRAM, HEADER, botToken, initDataValidator, objectMapper);
    }

    @Override
    protected MessengerAuthenticationToken createToken(Long externalId, String firstName, String lastName, String username) {
        return new TelegramAuthenticationToken(externalId, firstName, lastName, username);
    }
}
