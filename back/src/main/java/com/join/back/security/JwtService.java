package com.join.back.security;

import com.join.back.config.JwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

@Slf4j
@Service
public class JwtService {

    private static final int MIN_SECRET_BYTES = 32;

    private final JwtProperties properties;
    private final Clock clock;
    private final SecretKey key;

    public JwtService(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        if (properties.secret() == null || properties.secret().isBlank()) {
            // Local run without JWT_SECRET: an ephemeral key — sessions reset on restart.
            log.warn("join.jwt.secret is not set — using a random key; set JWT_SECRET in production");
            byte[] random = new byte[48];
            new SecureRandom().nextBytes(random);
            this.key = Keys.hmacShaKeyFor(random);
            return;
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(properties.secret());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("join.jwt.secret must be Base64-encoded", e);
        }
        if (decoded.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "join.jwt.secret must decode to at least " + MIN_SECRET_BYTES + " bytes (got " + decoded.length + ")");
        }
        this.key = Keys.hmacShaKeyFor(decoded);
    }

    public String issue(Long userId) {
        Instant now = clock.instant();
        Instant exp = now.plusSeconds((long) properties.ttlDays() * 24 * 3600);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key)
                .compact();
    }

    public Long verify(String token) {
        String sub = Jwts.parser()
                .verifyWith(key)
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
        return Long.parseLong(sub);
    }
}
