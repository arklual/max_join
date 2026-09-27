package com.join.back.model.dto;

/**
 * Outcome of a match chat as seen by the current user.
 *
 * @param agreement NONE, PROPOSED_BY_ME, PROPOSED_BY_COMPANION or AGREED
 * @param eventPassed the event date is before today — time to ask "did you go together?"
 * @param went my answer: WENT, NOT_WENT or null
 * @param companionWent the companion's answer, or null
 */
public record OutingStateResponse(
        Long chatId,
        String agreement,
        boolean eventPassed,
        String went,
        String companionWent
) {
}
