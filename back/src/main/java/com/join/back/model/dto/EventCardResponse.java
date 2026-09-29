package com.join.back.model.dto;

import com.join.back.model.entity.EventType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Событие в ленте")
public record EventCardResponse(
        @Schema(description = "Id события", example = "9835")
        Long id,
        @Schema(description = "Название", example = "Щелкунчик")
        String title,
        @Schema(description = "Категория")
        EventType type,
        @Schema(description = "Картинка")
        String imageUrl,
        @Schema(description = "Цена от, ₽; 0 — бесплатно, null — неизвестна", example = "1200")
        BigDecimal price,
        @Schema(description = "Цена без скидки, если есть скидка")
        BigDecimal originalPrice,
        @Schema(description = "Промокод на студенческий билет")
        String studentPromoCode,
        @Schema(description = "Условия промокода")
        String studentPromoNote,
        @Schema(description = "Дата, местная", example = "2026-10-12")
        LocalDate eventDate,
        @Schema(description = "Время начала, местное", example = "19:00:00")
        LocalTime eventTime,
        @Schema(description = "Город", example = "Казань")
        String city,
        @Schema(description = "Сохранено текущим пользователем")
        boolean liked,
        @Schema(description = "У текущего пользователя уже есть совпадение по событию")
        boolean hasMatch,
        @Schema(description = "Можно оплатить Пушкинской картой", example = "true")
        boolean pushkinCard,
        /** Other users who liked the event — "ещё N хотят пойти". */
        @Schema(description = "Сколько других пользователей сохранили событие", example = "3")
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
