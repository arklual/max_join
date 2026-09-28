package com.join.back.parser.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.join.back.model.entity.EventSource;
import com.join.back.parser.dto.RawExternalEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KassirParserTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 27);

    private final KassirParser parser = new KassirParser(null, new ObjectMapper());

    private static final String BODY = """
            {"kit":{"searchResult":{"pagination":{"pagesCount":3,"currentPage":1},"items":[
              {"type":"event","object":{"id":4018306,"title":"Сольный концерт Олег Аккуратов",
                "url":"https://ekb.kassir.ru/koncert/solnyiy-kontsert-oleg-akkuratov#4018306",
                "beginsAt":"2026-10-15T19:00:00+00:00","priceRange":{"min":1200.0,"max":3000.0},
                "posterImage":"https://cdn.kassir.ru/ekb/poster/3a/3a.jpg","isPushkin":true,
                "venueName":"Синара Центр","venue":{"name":"Синара Центр","address":{"addressString":"г.Екатеринбург, Верх-Исетский бульвар 15/4"}}}},
              {"type":"activity","object":{"id":261363,"title":"Елена Ваенга. Сольный концерт",
                "url":"https://ekb.kassir.ru/koncert/elena-vaenga","dateRange":{"beginsAt":"2026-12-15T19:00:00+00:00","endsAt":"2026-12-16T22:00:00+00:00"},
                "priceRange":{"min":null},"isPushkin":false}},
              {"type":"activity","object":{"id":9,"title":"Екатеринбургский зоопарк","url":"https://ekb.kassir.ru/zoo",
                "dateRange":{"beginsAt":"2022-04-26T10:00:00+00:00","endsAt":"2026-12-31T21:00:00+00:00"}}},
              {"type":"event","object":{"id":10,"title":"Без даты","url":"https://ekb.kassir.ru/x"}}
            ]}}}""";

    @Test
    void parsesSessionsAndShowsWithLocalTimeAndPushkinFlag() {
        KassirParser.Page page = parser.parse(BODY, "Екатеринбург", "Концерт", TODAY);

        assertEquals(3, page.pagesCount());
        List<RawExternalEvent> events = page.events();
        assertEquals(2, events.size());

        RawExternalEvent concert = events.get(0);
        assertEquals(EventSource.KASSIR, concert.getSource());
        assertEquals("event-4018306", concert.getExternalId());
        assertEquals(LocalDate.of(2026, 10, 15), concert.getEventDate());
        assertEquals(LocalTime.of(19, 0), concert.getEventTime());
        assertEquals(0, new BigDecimal("1200").compareTo(concert.getMinPrice()));
        assertTrue(concert.isPushkinCard());
        assertEquals("Екатеринбург", concert.getCity());
        assertEquals(List.of("Концерт"), concert.getRawCategories());
        assertEquals("Синара Центр — г.Екатеринбург, Верх-Исетский бульвар 15/4", concert.getDescription());

        RawExternalEvent show = events.get(1);
        assertEquals("activity-261363", show.getExternalId());
        assertEquals(LocalDate.of(2026, 12, 15), show.getEventDate());
        assertNull(show.getMinPrice());
        assertFalse(show.isPushkinCard());
    }
}
