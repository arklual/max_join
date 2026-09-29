package com.join.back.model.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A companion found for an event, before the chat: {@code status} NEW (nobody asked yet),
 * REQUESTED ({@code requestedByMe} tells who asked) or ACCEPTED ({@code chatId} is set).
 */
@Schema(description = "Найденный напарник по событию")
public record MatchResponse(
        @Schema(description = "Id совпадения", example = "30")
        Long id,
        @Schema(description = "Id напарника", example = "40")
        Long companionId,
        @Schema(description = "Имя напарника", example = "Боря")
        String companionName,
        @Schema(description = "Фото напарника")
        String companionPhoto,
        @Schema(description = "Возраст напарника", example = "21")
        Integer companionAge,
        @Schema(description = "Id события", example = "9835")
        Long eventId,
        @Schema(description = "Событие", example = "Щелкунчик")
        String eventTitle,
        @Schema(description = "Дата события", example = "2026-10-12")
        LocalDate eventDate,
        @Schema(description = "NEW — никто не позвал, REQUESTED — приглашение отправлено, ACCEPTED — договорились, чат открыт, DECLINED — отказ", example = "REQUESTED")
        String status,
        @Schema(description = "Приглашение отправил текущий пользователь")
        boolean requestedByMe,
        @Schema(description = "Id чата — после ACCEPTED")
        Long chatId,
        @Schema(description = "Когда нашлось совпадение")
        LocalDateTime createdAt
) {
}
