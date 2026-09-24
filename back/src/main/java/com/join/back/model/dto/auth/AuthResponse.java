package com.join.back.model.dto.auth;

import com.join.back.model.dto.UserResponse;

public record AuthResponse(
        String token,
        UserResponse user
) {
}
