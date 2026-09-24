package com.join.back.model.dto;

public record JoinGroupResponse(
        Long groupId,
        Long groupChatId,
        String message
) {
}
