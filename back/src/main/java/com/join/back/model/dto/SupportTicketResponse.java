package com.join.back.model.dto;

import com.join.back.model.entity.SupportTicketStatus;

import java.time.LocalDateTime;

public record SupportTicketResponse(
        Long id,
        Long userId,
        String userFirstName,
        Long userMaxId,
        SupportTicketStatus status,
        LocalDateTime createdAt,
        LocalDateTime lastMessageAt,
        String lastMessagePreview,
        long unreadCount
) {
}
