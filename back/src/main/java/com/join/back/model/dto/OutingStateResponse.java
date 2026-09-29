package com.join.back.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * "Сходили вместе?" state of a pair's chat, as seen by the current user.
 *
 * @param eventPassed the event date is before today — time to answer
 * @param went my answer: WENT, NOT_WENT or null
 * @param companionWent the companion's answer, or null
 */
@Schema(description = "«Сходили вместе?» по чату")
public record OutingStateResponse(
        @Schema(description = "Id чата", example = "77")
        Long chatId,
        @Schema(description = "Событие прошло — можно ответить")
        boolean eventPassed,
        @Schema(description = "Мой ответ: WENT, NOT_WENT или null")
        String went,
        @Schema(description = "Ответ собеседника: WENT, NOT_WENT или null")
        String companionWent
) {
}
