package com.join.back.parser.provider;

import com.join.back.model.entity.EventSource;
import com.join.back.parser.dto.RawExternalEvent;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineerHistoryParserTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 27);

    private final EngineerHistoryParser parser = new EngineerHistoryParser(null);

    private static String page() throws IOException {
        try (InputStream in = EngineerHistoryParserTest.class.getResourceAsStream("/parser/engineer-history-day.html")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void parsesSessionsFromRealDayPage() throws IOException {
        List<RawExternalEvent> events = parser.parseDay(page(), "https://engineer-history.ru", "Москва", DAY);

        // 17 cards on the saved page: 5 sold out and 1 family tour are skipped.
        assertEquals(11, events.size());
        RawExternalEvent first = events.get(0);
        assertEquals(EventSource.ENGINEER_HISTORY, first.getSource());
        assertEquals("34988", first.getExternalId());
        assertTrue(first.getTitle().startsWith("Три дома, два музея и одна улица"));
        assertEquals(DAY, first.getEventDate());
        assertEquals(LocalTime.of(11, 30), first.getEventTime());
        assertEquals(0, new BigDecimal("3600").compareTo(first.getMinPrice()));
        assertEquals("https://engineer-history.ru/tour/threestreets?startDate=2026-09-27&event_id=34988", first.getTicketUrl());
        assertEquals("Москва", first.getCity());
        assertTrue(first.getImageUrl().startsWith("https://img.engineer-history.ru/"));
        assertTrue(first.getRawCategories().contains("Модерн"));
        assertTrue(events.stream().noneMatch(e -> e.getTitle().startsWith("Дом переехал. Семейная")));
        assertTrue(events.stream().noneMatch(e -> e.getTitle().startsWith("Город и река")));
    }

    @Test
    void takesDateFromLinkWhenSiteFallsBackToNextDay() {
        String html = """
                <div class="card lubacard"><div class="card"><div class="lubacard-date">6 Октября 19:00</div></div>
                <div class="card-body"><h5 class="lubacard-title">Лекция + дегустация<br></h5>
                <div class="mt-auto lubacard-price"><span>6 700 <i class="icon-rouble"></i></div></div>
                <a class="image-card__btn" href="/tour/vino?startDate=2026-10-06&amp;event_id=35397"></a></div>""";

        RawExternalEvent event = parser.parseDay(html, "https://engineer-history.ru", "Москва",
                LocalDate.of(2026, 10, 5)).get(0);

        assertEquals(LocalDate.of(2026, 10, 6), event.getEventDate());
        assertEquals("35397", event.getExternalId());
    }
}
