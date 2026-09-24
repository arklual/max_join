package com.join.back.model.dto;

public record JoinFriendGroupResponse(
        Long friendGroupId,
        String name,
        String message
) {
}
