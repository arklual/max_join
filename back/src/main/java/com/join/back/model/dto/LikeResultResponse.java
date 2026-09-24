package com.join.back.model.dto;

import java.util.List;

/**
 * Result of liking an event: the companions found right away (each with a ready chat)
 * and how many other people are interested in the event.
 */
public record LikeResultResponse(List<NewMatch> matches, long othersInterested) {

    public record NewMatch(Long chatId, Long companionId, String companionName) {
    }
}
