package com.join.back.model.dto;

import java.time.LocalDateTime;

public record FriendGroupMemberResponse(
        Long userId,
        String firstName,
        String photo,
        String role,
        LocalDateTime joinedAt
) {
}
