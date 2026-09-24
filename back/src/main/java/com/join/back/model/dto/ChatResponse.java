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
        long unreadCount
) {
}
