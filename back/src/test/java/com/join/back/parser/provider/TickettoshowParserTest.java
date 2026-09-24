package com.join.back.parser.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.join.back.model.entity.EventSource;
import com.join.back.parser.config.ParserProperties;
import com.join.back.parser.dto.RawExternalEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TickettoshowParserTest {

    private static final String REF_CODE = "gAAAAABp_MhvvV3sf7YjfILZk4uXfZu_5pIozTIc0Z7JuqPs8ZRZ9yjuRI1bmS_wl9-RA_tUpj8OzLB-pyk-BSlPZ7qf5S93ZTjCfND_S2BWq_cD5_L4tak=";

    private TickettoshowParser parser;

    @BeforeEach
    void setUp() {
        ParserProperties properties = new ParserProperties();
        properties.getSources().getTickettoshow().setRefCode(REF_CODE);
        parser = new TickettoshowParser(properties, new ObjectMapper());
    }

    @Test
    void shouldParseListResponseIntoUniqueEvents() throws Exception {
        List<TickettoshowParser.TickettoshowListEvent> events =
                parser.parseEventsFromListResponse(listResponse());

        assertEquals(1, events.size());

        TickettoshowParser.TickettoshowListEvent event = events.get(0);
        assertEquals("3127", event.performanceId());
        assertEquals("Константин Райкин \"Живой Пушкин\"", event.title());
        assertEquals("Театр «Маска»", event.theatreName());
        assertEquals("Комсомольский проспект, 28", event.address());
        assertEquals(new BigDecimal("5000"), event.minPrice());
        assertEquals(List.of("Спектакли"), event.categories());
    }

    @Test
    void shouldMapDetailResponseToRawExternalEvent() throws Exception {
        TickettoshowParser.TickettoshowListEvent listEvent =
                parser.parseEventsFromListResponse(listResponse()).get(0);

        RawExternalEvent raw = parser.toRawExternalEvent(listEvent, detailResponse());

        assertNotNull(raw);
        assertEquals("3127", raw.getExternalId());
        assertEquals(EventSource.TICKETTOSHOW, raw.getSource());
        assertEquals("Константин Райкин \"Живой Пушкин\"", raw.getTitle());
        assertEquals(LocalDate.of(2026, 5, 16), raw.getEventDate());
        assertEquals(LocalTime.of(20, 0), raw.getEventTime());
        assertEquals(new BigDecimal("5000"), raw.getMinPrice());
        assertEquals("Москва", raw.getCity());
        assertEquals("https://moscowshow.com/upload/iblock/d83/mop7r5kzfc7n4occd6dpq8r1ixkpumot/rajkin.jpg", raw.getImageUrl());
        assertEquals("https://tickettoshow.ru/concert?concert_id=3127&ref=" + REF_CODE, raw.getTicketUrl());
        assertTrue(raw.getRawCategories().contains("Спектакли"));
        assertTrue(raw.getRawCategories().contains("Спектакль"));
        assertTrue(raw.getDescription().contains("Поэтический моноспектакль"));
        assertTrue(raw.getDescription().contains("Место: Театр «Маска», Основная сцена, Комсомольский проспект, 28"));
    }

    private String listResponse() {
        return """
                {
                  "message": "OK",
                  "data": {
                    "Спектакли": [
                      {
                        "performance_id": "3127",
                        "date_time": "2026-05-16T20-00-00",
                        "img": "moscowshow.com/upload/iblock/d83/mop7r5kzfc7n4occd6dpq8r1ixkpumot/rajkin.jpg",
                        "show_name": "Константин Райкин \\"Живой Пушкин\\"",
                        "theatre_name": "Театр «Маска»",
                        "cens": 12,
                        "min_price": 5000,
                        "address": "Комсомольский проспект, 28"
                      }
                    ],
                    "Театры": [
                      {
                        "theatre_id": "739e5d033a59ac60a30804840ae6c98e",
                        "theatre_name": "Театр «Маска»"
                      }
                    ]
                  }
                }
                """;
    }

    private String detailResponse() {
        return """
                {
                  "message": "OK",
                  "data": {
                    "performance_id": "3127",
                    "img": "moscowshow.com/upload/iblock/d83/mop7r5kzfc7n4occd6dpq8r1ixkpumot/rajkin.jpg",
                    "show_name": "Константин Райкин \\"Живой Пушкин\\"",
                    "theatre_name": "Театр «Маска»",
                    "type_name": "Спектакль",
                    "cens": 12,
                    "show_description": "Поэтический моноспектакль",
                    "hall_name": "Основная сцена",
                    "theatre_address": "Комсомольский проспект, 28",
                    "date_time": [
                      {
                        "begin_time": "2026-05-16T20-00-00",
                        "performance_id": "3127",
                        "performance_status": "active",
                        "min_price": 5000,
                        "theatre_name": "Театр «Маска»",
                        "hall_name": "Основная сцена",
                        "named_tickets": false
                      }
                    ],
                    "min_price": 5000,
                    "geocode": "55.726882,37.579654"
                  }
                }
                """;
    }
}
