package com.join.back.parser.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class KudaGoDatesTest {

    private static final ZoneId MSK = ZoneId.of("Europe/Moscow");
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 27);

    private static long epoch(int month, int day, int hour) {
        return LocalDateTime.of(2026, month, day, hour, 0).atZone(MSK).toEpochSecond();
    }

    private static JsonNode dates(String json) throws Exception {
        return new ObjectMapper().readTree(json);
    }

    @Test
    void picksNearestUpcomingSessionNotTheFirstOne() throws Exception {
        JsonNode node = dates("[{\"start\":" + epoch(3, 1, 19) + "},{\"start\":" + epoch(10, 20, 19)
                + "},{\"start\":" + epoch(10, 5, 18) + "}]");

        ZonedDateTime start = KudaGoParser.nearestUpcomingStart(node, TODAY, MSK);

        assertEquals(LocalDateTime.of(2026, 10, 5, 18, 0), start.toLocalDateTime());
    }

    @Test
    void skipsOngoingExhibitionsWithoutDatedSession() throws Exception {
        JsonNode node = dates("[{\"start\":-62135433000,\"end\":" + epoch(12, 31, 23) + "},{\"start\":"
                + epoch(6, 2, 10) + "}]");

        assertNull(KudaGoParser.nearestUpcomingStart(node, TODAY, MSK));
    }

    @Test
    void showsLocalTimeOfTheCity() throws Exception {
        JsonNode node = dates("[{\"start\":" + epoch(10, 5, 15) + "}]");

        ZonedDateTime start = KudaGoParser.nearestUpcomingStart(node, TODAY, ZoneId.of("Asia/Yekaterinburg"));

        assertEquals(LocalDateTime.of(2026, 10, 5, 17, 0), start.toLocalDateTime());
    }
}
