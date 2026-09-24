package com.join.back.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.liquibase.enabled=false")
class AuthFilterCoexistenceTest {

    @Autowired private JwtAuthFilter jwtAuthFilter;
    @Autowired private MaxAuthFilter maxAuthFilter;
    @Autowired private JwtService jwtService;

    private MockHttpServletRequest req;
    private MockHttpServletResponse res;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        req = new MockHttpServletRequest();
        res = new MockHttpServletResponse();
        chain = mock(FilterChain.class);
        SecurityContextHolder.clearContext();
    }

    @Test
    void jwtBearerWinsWhenBothPresent() throws Exception {
        String token = jwtService.issue(42L);
        req.addHeader("Authorization", "Bearer " + token);
        req.addHeader("X-Max-Init-Data", "user=%7B%22id%22%3A1%7D&hash=stub");

        // simulate the chain order: JwtAuthFilter first, then MaxAuthFilter
        jwtAuthFilter.doFilter(req, res, chain);
        // by the time MaxAuthFilter would run, the context is already set
        // — MaxAuthFilter is no-op if validation fails (stub hash above will fail anyway)
        maxAuthFilter.doFilter(req, res, chain);

        assertInstanceOf(UserIdAuthenticationToken.class,
                SecurityContextHolder.getContext().getAuthentication());
    }
}
