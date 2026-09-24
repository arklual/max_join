package com.join.back.model.dto;

import java.time.LocalDateTime;

public record MatchResponse(
        Long id,
        String companionName,
        String companionPhoto,
        String eventTitle,
        String eventDate,
        LocalDateTime createdAt
) {
}
