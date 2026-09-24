package com.join.back.model.dto;

import java.util.List;

public record IceBreakerResponse(
        List<String> suggestions,
        EventInfoDto event
) {
    public record EventInfoDto(
            Long id,
            String title,
            String eventDate,
            String eventTime,
            String price,
            String ticketUrl
    ) {
    }
}
