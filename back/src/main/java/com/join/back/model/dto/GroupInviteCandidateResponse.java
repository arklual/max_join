package com.join.back.model.dto;

/**
 * Someone the user already knows (a match chat or a friend group) who can be invited
 * into an event group. {@code status}: AVAILABLE, INVITED, IN_GROUP, BUSY (in another group on this event).
 */
public record GroupInviteCandidateResponse(
        Long userId,
        String firstName,
        String photo,
        String status
) {
}
