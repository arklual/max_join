package com.join.back.model.dto;

import com.join.back.model.entity.NotificationType;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        NotificationType type,
        String title,
        String message,
        Long matchId,
        Long companionId,
        String companionFirstName,
        String companionPhoto,
        Long eventId,
        String eventTitle,
        Long chatId,
        Long groupId,
        boolean read,
        LocalDateTime createdAt
) {
}
