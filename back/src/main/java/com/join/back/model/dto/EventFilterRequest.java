package com.join.back.model.dto;

import com.join.back.model.entity.EventType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record EventFilterRequest(
        String search,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        LocalDate dateFrom,
        LocalDate dateTo,
        List<EventType> type,
        List<Long> tagIds,
        /** Only events payable with the Pushkin card. */
        Boolean pushkinCard
) {
}
