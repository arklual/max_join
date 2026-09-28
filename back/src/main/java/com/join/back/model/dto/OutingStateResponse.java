package com.join.back.model.dto;

/**
 * "Сходили вместе?" state of a pair's chat, as seen by the current user.
 *
 * @param eventPassed the event date is before today — time to answer
 * @param went my answer: WENT, NOT_WENT or null
 * @param companionWent the companion's answer, or null
 */
public record OutingStateResponse(
        Long chatId,
        boolean eventPassed,
        String went,
        String companionWent
) {
}
