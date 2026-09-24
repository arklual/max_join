package com.join.back.model.dto;

import java.time.LocalDateTime;

public record GroupMemberResponse(
        Long userId,
        String firstName,
        String photo,
        String role,
        LocalDateTime joinedAt
) {
}
