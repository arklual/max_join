package com.join.back.model.dto;

import java.util.List;

/**
 * Result of liking an event: the companions found right away (a match to invite — no chat yet)
 * and how many other people are interested in the event.
 */
public record LikeResultResponse(List<NewMatch> matches, long othersInterested) {

    public record NewMatch(Long matchId, Long companionId, String companionName) {
    }
}
