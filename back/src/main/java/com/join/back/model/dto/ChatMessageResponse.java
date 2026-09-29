package com.join.back.model.dto;

import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Сообщение")
public record ChatMessageResponse(
        @Schema(description = "Id сообщения")
        Long id,
        @Schema(description = "Id чата", example = "77")
        Long chatId,
        @Schema(description = "Id отправителя", example = "39")
        Long senderId,
        @Schema(description = "Текст", example = "Привет! Идём вместе?")
        String text,
        @Schema(description = "Когда отправлено")
        LocalDateTime createdAt,
        @Schema(description = "Прочитано собеседником")
        boolean isRead
) {
}
