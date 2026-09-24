package com.join.back.security;

import com.join.back.config.JwtProperties;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class JwtAuthFilterTest {

    private static final String SECRET_BASE64 =
            Base64.getEncoder().encodeToString("0123456789ABCDEF0123456789ABCDEF".getBytes());
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-05-15T12:00:00Z"), ZoneOffset.UTC);

    private JwtService jwt;
    private JwtAuthFilter filter;
    private MockHttpServletRequest req;
    private MockHttpServletResponse res;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        jwt = new JwtService(new JwtProperties(SECRET_BASE64, 30), FIXED_CLOCK);
        filter = new JwtAuthFilter(jwt);
        req = new MockHttpServletRequest();
        res = new MockHttpServletResponse();
        chain = mock(FilterChain.class);
        SecurityContextHolder.clearContext();
    }

    @Test
    void setsAuthenticationOnValidBearer() throws Exception {
        String token = jwt.issue(123L);
        req.addHeader("Authorization", "Bearer " + token);
        filter.doFilter(req, res, chain);
        UserIdAuthenticationToken auth =
                assertInstanceOf(UserIdAuthenticationToken.class, SecurityContextHolder.getContext().getAuthentication());
        assertEquals(123L, auth.getUserId());
        verify(chain).doFilter(req, res);
    }

    @Test
    void leavesContextEmptyWithoutHeader() throws Exception {
        filter.doFilter(req, res, chain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(req, res);
    }

    @Test
    void leavesContextEmptyForNonBearer() throws Exception {
        req.addHeader("Authorization", "Basic abc==");
        filter.doFilter(req, res, chain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(req, res);
    }

    @Test
    void leavesContextEmptyForMalformedToken() throws Exception {
        req.addHeader("Authorization", "Bearer not-a-jwt");
        filter.doFilter(req, res, chain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(req, res);
    }
}
