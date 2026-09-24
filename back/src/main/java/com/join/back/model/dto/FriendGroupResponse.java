package com.join.back.model.dto;

import java.time.LocalDateTime;
import java.util.List;

public record FriendGroupResponse(
        Long id,
        String name,
        String inviteCode,
        Integer maxSize,
        Integer currentSize,
        FriendGroupMemberResponse creator,
        List<FriendGroupMemberResponse> members,
        LocalDateTime createdAt
) {
}
