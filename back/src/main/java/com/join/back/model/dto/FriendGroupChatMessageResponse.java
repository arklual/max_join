package com.join.back.model.dto;

import java.time.LocalDateTime;

public record FriendGroupChatMessageResponse(
        Long id,
        Long senderId,
        String senderName,
        String senderPhoto,
        String text,
        LocalDateTime createdAt
) {
}
