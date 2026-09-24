package com.join.back.model.dto;

import com.join.back.model.entity.EventType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record EventDetailResponse(
        Long id,
        String title,
        String description,
        EventType type,
        String imageUrl,
        BigDecimal price,
        BigDecimal originalPrice,
        String studentPromoCode,
        String studentPromoNote,
        LocalDate eventDate,
        LocalTime eventTime,
        String ticketUrl,
        String city,
        LocalDateTime createdAt,
        boolean liked,
        boolean hasMatch,
        boolean pushkinCard
) {

    public EventDetailResponse withLiked(boolean liked) {
        return new EventDetailResponse(id, title, description, type, imageUrl, price, originalPrice, studentPromoCode, studentPromoNote, eventDate, eventTime, ticketUrl, city, createdAt, liked, hasMatch, pushkinCard);
    }

    public EventDetailResponse withHasMatch(boolean hasMatch) {
        return new EventDetailResponse(id, title, description, type, imageUrl, price, originalPrice, studentPromoCode, studentPromoNote, eventDate, eventTime, ticketUrl, city, createdAt, liked, hasMatch, pushkinCard);
    }
}
