package com.join.back.model.dto;

/** Black-list relation between the current user and another one. */
public record BlockStatusResponse(
        Long userId,
        boolean blockedByMe,
        boolean blockedMe
) {
}
