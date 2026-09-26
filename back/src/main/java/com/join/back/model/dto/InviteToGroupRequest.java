package com.join.back.model.dto;

import jakarta.validation.constraints.NotNull;

public record InviteToGroupRequest(@NotNull Long userId) {
}
