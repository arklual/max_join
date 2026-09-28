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

    /** Mini app screen with found companions and requests (the chats list). */
    public static final String MATCHES = "matches";

    /** Bot button: ask a found companion "пойдём вместе?". */
    public static String invite(Long matchId) {
        return "invite_" + matchId;
    }

    /** Bot buttons: answer "пойдём вместе?". */
    public static String acceptContact(Long matchId) {
        return "accept_" + matchId;
    }

    public static String declineContact(Long matchId) {
        return "decline_" + matchId;
    }

    /** Bot button: answer "did you go together?" for a match chat. */
    public static String went(Long chatId, boolean went) {
        return "went_" + chatId + (went ? "_yes" : "_no");
    }

    public static String group(Long groupId) {
        return groupId == null ? null : "group_" + groupId;
    }

    public static String event(Long eventId) {
        return eventId == null ? null : "event_" + eventId;
    }
}
