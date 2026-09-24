package com.join.back.security;

import com.join.back.config.JwtProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceTest {

    private static final String SECRET_BASE64 =
            Base64.getEncoder().encodeToString("0123456789ABCDEF0123456789ABCDEF".getBytes());
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-05-15T12:00:00Z"), ZoneOffset.UTC);

    private JwtService service() {
        return new JwtService(new JwtProperties(SECRET_BASE64, 30), FIXED_CLOCK);
    }

    @Test
    void issuedTokenRoundtripsBackToUserId() {
        JwtService svc = service();
        String token = svc.issue(42L);
        assertEquals(42L, svc.verify(token));
    }

    @Test
    void expiredTokenIsRejected() {
        Clock past = Clock.fixed(Instant.parse("2025-01-01T00:00:00Z"), ZoneOffset.UTC);
        JwtService old = new JwtService(new JwtProperties(SECRET_BASE64, 30), past);
        String stale = old.issue(7L);
        assertThrows(RuntimeException.class, () -> service().verify(stale));
    }

    @Test
    void signatureMismatchIsRejected() {
        String otherSecret = Base64.getEncoder().encodeToString(
                "FEDCBA9876543210FEDCBA9876543210".getBytes());
        JwtService other = new JwtService(new JwtProperties(otherSecret, 30), FIXED_CLOCK);
        String foreign = other.issue(99L);
        assertThrows(RuntimeException.class, () -> service().verify(foreign));
    }

    @Test
    void shortSecretFailsAtConstruction() {
        String tooShort = Base64.getEncoder().encodeToString("short".getBytes());
        assertThrows(IllegalStateException.class,
                () -> new JwtService(new JwtProperties(tooShort, 30), FIXED_CLOCK));
    }
}
