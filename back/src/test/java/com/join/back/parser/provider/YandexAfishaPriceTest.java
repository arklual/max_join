package com.join.back.parser.provider;

import com.join.back.parser.dto.RawExternalEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YandexAfishaPriceTest {

    private static final String STATE = "{"
            + "\"items\":[{\"__typename\":\"ActualEvent\",\"event\":{\"__ref\":\"EventPreview:aa01\"},"
            + "\"scheduleInfo\":{\"__typename\":\"EventScheduleInfo\",\"dates\":[\"2026-10-05\"],"
            + "\"dateGroups({\\\"count\\\":4})\":[],"
            + "\"prices\":[{\"__typename\":\"Money\",\"currency\":\"rub\",\"value\":170000},"
            + "{\"__typename\":\"Money\",\"currency\":\"rub\",\"value\":150000}],\"pushkinCardAllowed\":true}},"
            + "{\"__typename\":\"ActualEvent\",\"event\":{\"__ref\":\"EventPreview:bb02\"},"
            + "\"scheduleInfo\":{\"__typename\":\"EventScheduleInfo\",\"dates\":[\"2026-10-06\"],"
            + "\"prices\":[],\"pushkinCardAllowed\":false}}],"
            + "\"EventPreview:aa01\":{\"__typename\":\"EventPreview\",\"id\":\"aa01\","
            + "\"url\":\"\\u002Fmoscow\\u002Ftheatre_show\\u002Fkrik\",\"title\":\"Крик лангусты\"},"
            + "\"EventPreview:bb02\":{\"__typename\":\"EventPreview\",\"id\":\"bb02\","
            + "\"url\":\"\\u002Fmoscow\\u002Fconcert\\u002Fx\",\"title\":\"Концерт\"}"
            + "}";

    @Test
    void takesLowestPriceAndPushkinFlagFromSchedule() {
        List<RawExternalEvent> events = new YandexAfishaParser(null)
                .parseApolloState(STATE, "moscow", "https://afisha.yandex.ru");

        RawExternalEvent theatre = events.stream().filter(e -> e.getExternalId().equals("aa01")).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("1500").compareTo(theatre.getMinPrice()));
        assertEquals("от 1500 руб.", theatre.getRawPrice());
        assertTrue(theatre.isPushkinCard());

        RawExternalEvent concert = events.stream().filter(e -> e.getExternalId().equals("bb02")).findFirst().orElseThrow();
        assertNull(concert.getMinPrice());
        assertFalse(concert.isPushkinCard());
    }
}
