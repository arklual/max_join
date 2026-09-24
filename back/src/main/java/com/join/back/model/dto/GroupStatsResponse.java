package com.join.back.model.dto;

public record GroupStatsResponse(
        Long eventId,
        long openGroupsCount,
        long totalActiveMembers
) {
}
