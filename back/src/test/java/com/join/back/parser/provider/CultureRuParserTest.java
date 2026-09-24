package com.join.back.parser.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.join.back.model.entity.EventSource;
import com.join.back.parser.config.ParserProperties;
import com.join.back.parser.dto.RawExternalEvent;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CultureRuParserTest {

    private final CultureRuParser parser = new CultureRuParser(new ParserProperties(), new ObjectMapper());

    @Test
    void parsesEventWithSeancesIntoOneEventPerDate() {
        // 2026-12-25 19:00 and 2026-12-26 19:00 Moscow time
        String body = """
                {"events":[{"_id":123,"name":"Щелкунчик","shortDescription":"<p>Балет в двух актах</p>",
                  "category":{"name":"Спектакли","sysName":"spektakli"},"tags":[{"name":"Балет"}],
                  "image":{"name":"https://cdn.culture.ru/images/1.jpg"},
                  "places":[{"locale":{"name":"Москва"},"address":{"fullAddress":"Театральная пл., 1"}}],
                  "seances":[{"start":1798214400000,"end":1798225200000},{"start":1798300800000,"end":1798311600000}],
                  "price":1500,"isFree":false,"saleLink":"https://tickets.example/123"}]}""";

        List<RawExternalEvent> events = parser.parse(body, "Санкт-Петербург");

        assertEquals(2, events.size());
        RawExternalEvent first = events.get(0);
        assertEquals(EventSource.CULTURE_RU, first.getSource());
        assertEquals("123#2026-12-25", first.getExternalId());
        assertEquals("Щелкунчик", first.getTitle());
        assertEquals("Балет в двух актах", first.getDescription());
        assertEquals("Москва", first.getCity());
        assertEquals(LocalDate.of(2026, 12, 25), first.getEventDate());
        assertEquals(LocalTime.of(19, 0), first.getEventTime());
        assertEquals("https://tickets.example/123", first.getTicketUrl());
        assertEquals("от 1500 руб.", first.getRawPrice());
        assertEquals(List.of("Спектакли", "Балет"), first.getRawCategories());
        assertTrue(first.isPushkinCard());
        assertEquals(LocalDate.of(2026, 12, 26), events.get(1).getEventDate());
    }

    @Test
    void fallsBackToRequestedCityAndSkipsBrokenEntries() {
        String body = """
                {"events":[{"_id":1,"name":"","start":1798214400000},
                           {"_id":2,"name":"Лекция","start":1798214400000,"isFree":true}]}""";

        List<RawExternalEvent> events = parser.parse(body, "Казань");

        assertEquals(1, events.size());
        assertEquals("Казань", events.get(0).getCity());
        assertEquals("бесплатно", events.get(0).getRawPrice());
    }

    @Test
    void doesNothingWithoutApiKey() {
        assertTrue(parser.fetchEvents().isEmpty());
    }
}
