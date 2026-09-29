package com.join.back.model.dto.auth;

import com.join.back.model.dto.UserResponse;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Токен и профиль")
public record AuthResponse(
        @Schema(description = "JWT для заголовка `Authorization: Bearer <token>`", example = "eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiI0MiJ9.sig")
        String token,
        @Schema(description = "Профиль пользователя")
        UserResponse user
) {
}
