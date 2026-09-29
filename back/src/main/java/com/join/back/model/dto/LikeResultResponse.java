package com.join.back.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Result of liking an event: the companions found right away (a match to invite — no chat yet)
 * and how many other people are interested in the event.
 */
@Schema(description = "Результат сохранения события")
public record LikeResultResponse(
        @Schema(description = "Совпадения, найденные сразу: событие уже сохранили подходящие люди. Чата ещё нет")
        List<NewMatch> matches,
        @Schema(description = "Сколько ещё человек сохранили событие", example = "1")
        long othersInterested
) {

    @Schema(description = "Новое совпадение")
    public record NewMatch(
            @Schema(description = "Id совпадения — для POST /api/matches/{id}/request", example = "30")
            Long matchId,
            @Schema(description = "Id напарника", example = "39")
            Long companionId,
            @Schema(description = "Имя напарника", example = "Аня")
            String companionName
    ) {
    }
}
