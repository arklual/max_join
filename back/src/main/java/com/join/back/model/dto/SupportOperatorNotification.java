package com.join.back.model.dto;

public record SupportOperatorNotification(
        Long ticketId,
        Long userId,
        String userFirstName,
        SupportMessageResponse message
) {
}
