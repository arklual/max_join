package com.join.back.parser.provider;

import com.join.back.parser.config.ParserProperties;
import com.join.back.parser.dto.RawExternalEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Network-touching smoke test for the Novaya Opera Telegram parser.
 * Disabled by default in CI — set {@code NOVAYA_OPERA_LIVE_SMOKE=1}
 * to run it locally to confirm the live channel still matches our shape.
 *
 * <p>Run with: {@code NOVAYA_OPERA_LIVE_SMOKE=1 ./gradlew test --tests "*NovayaOperaLiveSmokeTest*"}
 */
@EnabledIfEnvironmentVariable(named = "NOVAYA_OPERA_LIVE_SMOKE", matches = "1")
class NovayaOperaLiveSmokeTest {

    @Test
    void fetchesAndParsesLiveChannel() {
        ParserProperties props = new ParserProperties();
        props.getSources().getNovayaOpera().setEnabled(true);
        props.getSources().getNovayaOpera().setChannel("novayaopera");
        props.getSources().getNovayaOpera().setPromoCode("GAUDEAMUS");
        props.getSources().getNovayaOpera().setStudentPrice(600);
        props.getSources().getNovayaOpera().setIndicativeFullPrice(5000);

        NovayaOperaTelegramParser parser = new NovayaOperaTelegramParser(props);
        List<RawExternalEvent> events = parser.fetchEvents();

        // We don't assert a specific count — the channel may or may not have an
        // active promo at the moment of running. We just assert the call shape
        // doesn't blow up.
        assertNotNull(events);
        System.out.println("[Novaya Opera smoke] got " + events.size() + " events");
        events.forEach(e -> System.out.println("  - " + e.getEventDate() + " | " + e.getTitle()
                + " | " + e.getTicketUrl()));
    }
}
