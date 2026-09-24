package com.join.back.model.dto;

import com.join.back.model.entity.EventType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

public record EventCardResponse(
        Long id,
        String title,
        EventType type,
        String imageUrl,
        BigDecimal price,
        BigDecimal originalPrice,
        String studentPromoCode,
        String studentPromoNote,
        LocalDate eventDate,
        LocalTime eventTime,
        String city,
        boolean liked,
        boolean hasMatch,
        boolean pushkinCard,
        /** Other users who liked the event — "ещё N хотят пойти". */
        long interestedCount
) {

    public EventCardResponse withLiked(boolean liked) {
        return new EventCardResponse(id, title, type, imageUrl, price, originalPrice, studentPromoCode, studentPromoNote, eventDate, eventTime, city, liked, hasMatch, pushkinCard, interestedCount);
    }

    public EventCardResponse withInterestedCount(long interestedCount) {
        return new EventCardResponse(id, title, type, imageUrl, price, originalPrice, studentPromoCode, studentPromoNote, eventDate, eventTime, city, liked, hasMatch, pushkinCard, interestedCount);
    }

    public EventCardResponse withHasMatch(boolean hasMatch) {
        return new EventCardResponse(id, title, type, imageUrl, price, originalPrice, studentPromoCode, studentPromoNote, eventDate, eventTime, city, liked, hasMatch, pushkinCard, interestedCount);
    }
}
