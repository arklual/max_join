package com.join.back.security;

/** Messengers whose mini apps can sign users in with signed init data. */
public enum Messenger {
    MAX("MAX"),
    TELEGRAM("Telegram");

    private final String displayName;

    Messenger(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
