package com.join.back.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginAttemptServiceTest {

    private static final Instant T0 = Instant.parse("2026-10-01T10:00:00Z");

    @Test
    void blocksEmailAfterTooManyFailures() {
        LoginAttemptService service = new LoginAttemptService(Clock.fixed(T0, ZoneOffset.UTC));
        for (int i = 0; i < LoginAttemptService.MAX_FAILURES; i++) {
            assertFalse(service.isBlocked("a@b.ru", "10.0.0." + i));
            service.recordFailure("a@b.ru", "10.0.0." + i);
        }
        assertTrue(service.isBlocked("A@B.RU", "10.0.0.99"));
        assertFalse(service.isBlocked("other@b.ru", "10.0.0.99"));
    }

    @Test
    void blocksAddressAcrossEmails() {
        LoginAttemptService service = new LoginAttemptService(Clock.fixed(T0, ZoneOffset.UTC));
        for (int i = 0; i < LoginAttemptService.MAX_FAILURES; i++) {
            service.recordFailure("user" + i + "@b.ru", "1.2.3.4");
        }
        assertTrue(service.isBlocked("fresh@b.ru", "1.2.3.4"));
    }

    @Test
    void successClearsAndWindowExpires() {
        MutableClock clock = new MutableClock(T0);
        LoginAttemptService service = new LoginAttemptService(clock);
        for (int i = 0; i < LoginAttemptService.MAX_FAILURES; i++) {
            service.recordFailure("a@b.ru", "1.2.3.4");
        }
        assertTrue(service.isBlocked("a@b.ru", "1.2.3.4"));
        clock.now = T0.plus(LoginAttemptService.WINDOW).plus(Duration.ofSeconds(1));
        assertFalse(service.isBlocked("a@b.ru", "1.2.3.4"));

        service.recordFailure("a@b.ru", "1.2.3.4");
        service.recordSuccess("a@b.ru", "1.2.3.4");
        assertFalse(service.isBlocked("a@b.ru", "1.2.3.4"));
    }

    private static class MutableClock extends Clock {
        Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
