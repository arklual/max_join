package com.join.back.model.dto;

import com.join.back.model.entity.SupportSenderType;

import java.time.LocalDateTime;

public record SupportMessageResponse(
        Long id,
        Long ticketId,
        SupportSenderType senderType,
        String text,
        LocalDateTime createdAt,
        boolean isRead
) {
}
