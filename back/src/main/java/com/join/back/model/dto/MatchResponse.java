package com.join.back.model.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A companion found for an event, before the chat: {@code status} NEW (nobody asked yet),
 * REQUESTED ({@code requestedByMe} tells who asked) or ACCEPTED ({@code chatId} is set).
 */
public record MatchResponse(
        Long id,
        Long companionId,
        String companionName,
        String companionPhoto,
        Integer companionAge,
        Long eventId,
        String eventTitle,
        LocalDate eventDate,
        String status,
        boolean requestedByMe,
        Long chatId,
        LocalDateTime createdAt
) {
}
