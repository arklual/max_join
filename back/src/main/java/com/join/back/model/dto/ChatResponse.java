package com.join.back.model.dto;

import java.time.LocalDateTime;

public record ChatResponse(
        Long id,
        Long companionId,
        String companionName,
        String companionPhoto,
        String eventTitle,
        Long eventId,
        String lastMessage,
        LocalDateTime lastMessageTime,
        long unreadCount,
        /** BLOCKED_BY_ME, BLOCKED_ME or null — personal black list between the two. */
        String blockStatus
) {
}
