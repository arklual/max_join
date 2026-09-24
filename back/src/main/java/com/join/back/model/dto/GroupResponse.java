package com.join.back.model.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record GroupResponse(
        Long id,
        Long eventId,
        String eventTitle,
        LocalDate eventDate,
        String title,
        String description,
        Integer maxSize,
        Integer currentSize,
        String status,
        Long groupChatId,
        GroupMemberResponse creator,
        List<GroupMemberResponse> members,
        LocalDateTime createdAt
) {
}
