package com.join.back.model.dto;

import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Личный чат с напарником")
public record ChatResponse(
        @Schema(description = "Id чата", example = "77")
        Long id,
        @Schema(description = "Id собеседника", example = "40")
        Long companionId,
        @Schema(description = "Имя собеседника", example = "Боря")
        String companionName,
        @Schema(description = "Фото собеседника")
        String companionPhoto,
        @Schema(description = "Событие, на которое договорились", example = "Щелкунчик")
        String eventTitle,
        @Schema(description = "Id события", example = "9835")
        Long eventId,
        @Schema(description = "Последнее сообщение", example = "Встречаемся у входа?")
        String lastMessage,
        @Schema(description = "Время последнего сообщения")
        LocalDateTime lastMessageTime,
        @Schema(description = "Непрочитанных сообщений", example = "2")
        long unreadCount,
        /** BLOCKED_BY_ME, BLOCKED_ME or null — personal black list between the two. */
        @Schema(description = "BLOCKED_BY_ME, BLOCKED_ME или null — чёрный список между собеседниками")
        String blockStatus,
        @Schema(description = "Закреплён у текущего пользователя")
        boolean pinned
) {
}
