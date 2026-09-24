package com.join.back.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaxAuthFilterTest {

    private static final String BOT_TOKEN = "test-bot-token";
    private static final String HEADER_NAME = "X-Max-Init-Data";

    @Mock
    private WebAppInitDataValidator initDataValidator;

    @Mock
    private FilterChain filterChain;

    private MaxAuthFilter maxAuthFilter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        maxAuthFilter = new MaxAuthFilter(BOT_TOKEN, initDataValidator, objectMapper);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldAuthenticateWithValidInitData() throws ServletException, IOException {
        String userJson = "{\"id\":123456789,\"first_name\":\"John\",\"last_name\":\"Doe\",\"username\":\"johndoe\"}";
        String encodedUser = URLEncoder.encode(userJson, StandardCharsets.UTF_8);
        String initData = "user=" + encodedUser + "&auth_date=1234567890&hash=validhash";

        request.addHeader(HEADER_NAME, initData);

        when(initDataValidator.validate(initData, BOT_TOKEN)).thenReturn(true);
        when(initDataValidator.parseInitData(initData)).thenReturn(new java.util.LinkedHashMap<>() {{
            put("user", userJson);
            put("auth_date", "1234567890");
        }});

        maxAuthFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertInstanceOf(MaxAuthenticationToken.class, SecurityContextHolder.getContext().getAuthentication());

        MaxAuthenticationToken authentication =
                (MaxAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
        assertEquals(123456789L, authentication.getMaxId());
        assertEquals("John", authentication.getFirstName());
        assertEquals("Doe", authentication.getLastName());
        assertEquals("johndoe", authentication.getUsername());
    }

    @Test
    void shouldNotAuthenticateWithInvalidInitData() throws ServletException, IOException {
        String initData = "user=%7B%22id%22%3A123%7D&auth_date=1234567890&hash=invalidhash";

        request.addHeader(HEADER_NAME, initData);
        when(initDataValidator.validate(initData, BOT_TOKEN)).thenReturn(false);

        maxAuthFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void shouldNotAuthenticateWithMissingHeader() throws ServletException, IOException {
        maxAuthFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void shouldContinueFilterChainEvenWithoutAuth() throws ServletException, IOException {
        request.addHeader(HEADER_NAME, "");

        maxAuthFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
