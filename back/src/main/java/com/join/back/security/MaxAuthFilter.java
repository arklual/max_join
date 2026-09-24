package com.join.back.security;

import com.fasterxml.jackson.databind.ObjectMapper;

/** MAX mini app: signed init data in {@code X-Max-Init-Data}. */
public class MaxAuthFilter extends MessengerAuthFilter {

    public static final String HEADER = "X-Max-Init-Data";

    public MaxAuthFilter(String botToken, WebAppInitDataValidator initDataValidator, ObjectMapper objectMapper) {
        super(Messenger.MAX, HEADER, botToken, initDataValidator, objectMapper);
    }

    @Override
    protected MessengerAuthenticationToken createToken(Long externalId, String firstName, String lastName, String username) {
        return new MaxAuthenticationToken(externalId, firstName, lastName, username);
    }
}
