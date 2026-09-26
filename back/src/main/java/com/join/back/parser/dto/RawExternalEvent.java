package com.join.back.parser.dto;

import com.join.back.model.entity.EventSource;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Data
@Builder
public class RawExternalEvent {

    private String externalId;
    private EventSource source;
    private String title;
    private String description;
    private List<String> rawCategories;
    private String imageUrl;
    private String rawPrice;
    private BigDecimal minPrice;
    private BigDecimal originalPrice;
    private String studentPromoCode;
    private String studentPromoNote;
    private LocalDate eventDate;
    private LocalTime eventTime;
    private String ticketUrl;
    private String city;
    /** Text the organizer wrote in normal case (full description) — used to fix all-caps titles. */
    private String titleContext;
    /** Listed in a Pushkin-card selection of the source. */
    private boolean pushkinCard;
}
