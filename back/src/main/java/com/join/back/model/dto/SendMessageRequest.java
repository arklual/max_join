package com.join.back.model.dto;

import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Новое сообщение")
public record SendMessageRequest(
        @Schema(description = "Текст сообщения", example = "Привет! Идём вместе?")
        @NotBlank String text
) {
}
