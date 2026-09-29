package com.join.back.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Ошибка. Текст в `error` можно показывать пользователю")
public record ErrorResponse(
        @Schema(description = "Что пошло не так", example = "Сегодня можно лайкнуть не больше 10 событий — возвращайся завтра")
        String error,
        @Schema(description = "То же, что `error` (есть у ошибок действий пользователя, код 409)")
        String message,
        @Schema(description = "Машинный код ошибки, если есть", example = "VALIDATION_FAILED")
        String code
) {

    public static ErrorResponse of(String error) {
        return new ErrorResponse(error, null, null);
    }

    public static ErrorResponse withCode(String error, String code) {
        return new ErrorResponse(error, null, code);
    }
}
