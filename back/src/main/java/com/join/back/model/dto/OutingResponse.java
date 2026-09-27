package com.join.back.model.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** An upcoming event the user is going to with someone: a match chat or a group. */
public record OutingResponse(
        Long eventId,
        String title,
        String imageUrl,
        LocalDate eventDate,
        LocalTime eventTime,
        String city,
        boolean pushkinCard,
        List<Companion> companions,
        /** Where to talk: /chats/{chatId} for a pair, /group-chats/{groupChatId} for a group. */
        Long chatId,
        Long groupChatId,
        /** Both confirmed "we agreed to go together" (pairs only). */
        boolean agreed
) {

    public record Companion(Long userId, String name, String photo) {
    }
}
