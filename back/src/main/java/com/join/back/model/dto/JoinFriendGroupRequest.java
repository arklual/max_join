package com.join.back.model.dto;

import jakarta.validation.constraints.NotBlank;

public record JoinFriendGroupRequest(
        @NotBlank String inviteCode
) {
}
