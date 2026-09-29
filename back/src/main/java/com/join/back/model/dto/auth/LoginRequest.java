package com.join.back.model.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Вход по email")
public record LoginRequest(
        @Schema(description = "Email", example = "anya@example.com")
        @NotBlank @Email String email,
        @Schema(description = "Пароль", example = "password123")
        @NotBlank String password
) {
}
