package com.join.back.model.dto;

import java.time.LocalDateTime;

public record ChatMessageResponse(
        Long id,
        Long chatId,
        Long senderId,
        String text,
        LocalDateTime createdAt,
        boolean isRead
) {
}
