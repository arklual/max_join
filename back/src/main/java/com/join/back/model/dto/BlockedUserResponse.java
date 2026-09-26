package com.join.back.model.dto;

import java.time.LocalDateTime;

public record BlockedUserResponse(
        Long userId,
        String firstName,
        String photo,
        LocalDateTime blockedAt
) {
}
