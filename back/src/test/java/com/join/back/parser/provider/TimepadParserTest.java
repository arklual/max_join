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
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimepadParserTest {

    private static final String AFISHA = "https://afisha.timepad.ru";
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 26);

    private final TimepadParser parser = new TimepadParser(null, new ObjectMapper());

    private static String event(long id, String name, String categories, String sessions, boolean online) {
        return """
                {"id":%d,"name":"%s  ","shortDescription":"Кратко","description":"<p>Длинно</p>","online":%s,
                 "address":{"city":"Москва","cityAlias":"moscow"},
                 "categories":[%s],"sessions":[%s],
                 "computed":{"startDate":"2026-02-23 12:00:00","minPrice":0},
                 "poster":"https://ucare.timepad.ru/p.jpg","slug":"slug-%d"}
                """.formatted(id, name, online, categories, sessions, id);
    }

    private static String cat(String name) {
        return "{\"id\":1,\"name\":\"" + name + "\"}";
    }

    @Test
    void keepsLeisureEventsDatedByNearestUpcomingSession() {
        String sessions = """
                {"startDate":"2026-09-20 19:00:00","minPrice":500},
                {"startDate":"2026-10-04 19:30:00","minPrice":1200},
                {"startDate":"2026-09-28 18:00:00","minPrice":1000}""";
        String body = "{\"result\":\"ok\",\"data\":{\"total\":1,\"offset\":[1790344800000,\"42\"],\"events\":["
                + event(42, "Джаз в саду", cat("Концерты"), sessions, false) + "]}}";

        TimepadParser.Page page = parser.parse(body, AFISHA, TODAY, true);

        assertEquals(List.of("1790344800000", "42"), page.nextOffset());
        RawExternalEvent raw = page.events().get(0);
        assertEquals(EventSource.TIMEPAD, raw.getSource());
        assertEquals("42", raw.getExternalId());
        assertEquals("Джаз в саду", raw.getTitle());
        assertEquals(LocalDate.of(2026, 9, 28), raw.getEventDate());
        assertEquals(LocalTime.of(18, 0), raw.getEventTime());
        assertEquals(0, new BigDecimal("1000").compareTo(raw.getMinPrice()));
        assertEquals("https://afisha.timepad.ru/moscow/events/slug-42", raw.getTicketUrl());
        assertEquals("Москва", raw.getCity());
        assertEquals("Кратко", raw.getDescription());
    }

    @Test
    void dropsBusinessKidsOnlineAndFinishedEvents() {
        String future = "{\"startDate\":\"2026-10-01 19:00:00\",\"minPrice\":0}";
        String body = "{\"data\":{\"events\":["
                + event(1, "Нетворкинг", cat("Бизнес") + "," + cat("Концерты"), future, false) + ","
                + event(2, "Для малышей", cat("Для детей") + "," + cat("Театры"), future, false) + ","
                + event(3, "Курс английского", cat("Иностранные языки"), future, false) + ","
                + event(4, "Онлайн-концерт", cat("Концерты"), future, true) + ","
                + event(5, "Прошедшая выставка", cat("Выставки"), "{\"startDate\":\"2026-09-01 10:00:00\"}", false) + ","
                + event(6, "Спектакль", cat("Театры"), future, false)
                + "]}}";

        List<RawExternalEvent> events = parser.parse(body, AFISHA, TODAY, true).events();

        assertEquals(1, events.size());
        assertEquals("6", events.get(0).getExternalId());
        assertTrue(events.get(0).getMinPrice().signum() == 0);
    }

    @Test
    void pinnedOrganizerKeepsEventsOutsideLeisureCategories() {
        String future = "{\"startDate\":\"2026-10-01 19:00:00\",\"minPrice\":1950}";
        String body = "{\"data\":{\"events\":["
                + event(7, "ДВОРЕЦ РАЗУМОВСКОГО", cat("Для детей"), future, false) + "]}}";

        assertEquals(0, parser.parse(body, AFISHA, TODAY, true).events().size());
        assertEquals(1, parser.parse(body, AFISHA, TODAY, false).events().size());
    }
}
