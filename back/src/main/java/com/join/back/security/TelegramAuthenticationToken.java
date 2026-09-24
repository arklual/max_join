package com.join.back.security;

/** Authenticated via the Telegram mini app. */
public class TelegramAuthenticationToken extends MessengerAuthenticationToken {

    public TelegramAuthenticationToken(Long telegramId, String firstName, String lastName, String username) {
        super(Messenger.TELEGRAM, telegramId, firstName, lastName, username);
    }

    public Long getTelegramId() {
        return getExternalId();
    }
}
