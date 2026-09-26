package com.join.back.service;

/**
 * Start payloads that open a specific screen of the mini app
 * (MAX {@code open_app} button payload / {@code ?startapp=}); parsed by the front's startTarget.ts.
 */
public final class DeepLinks {

    public static final String PUSHKIN = "pushkin";

    private DeepLinks() {
    }

    public static String chat(Long chatId) {
        return chatId == null ? null : "chat_" + chatId;
    }

    public static String groupChat(Long groupChatId) {
        return groupChatId == null ? null : "gchat_" + groupChatId;
    }

    public static String group(Long groupId) {
        return groupId == null ? null : "group_" + groupId;
    }

    public static String event(Long eventId) {
        return eventId == null ? null : "event_" + eventId;
    }
}
