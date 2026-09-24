package com.join.back.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

/** Uses the real validator with a genuinely signed Telegram init data string. */
class TelegramAuthFilterTest {

    private static final String BOT_TOKEN = "123456:telegram-test-token";

    private TelegramAuthFilter filter;

    @BeforeEach
    void setUp() {
        filter = new TelegramAuthFilter(BOT_TOKEN, new WebAppInitDataValidator(), new ObjectMapper());
        SecurityContextHolder.clearContext();
    }

    private static String sign(String userJson, String token) throws Exception {
        String dataCheck = "auth_date=1700000000\nuser=" + userJson;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("WebAppData".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] secret = mac.doFinal(token.getBytes(StandardCharsets.UTF_8));
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        String hash = HexFormat.of().formatHex(mac.doFinal(dataCheck.getBytes(StandardCharsets.UTF_8)));
        return "auth_date=1700000000&user=" + URLEncoder.encode(userJson, StandardCharsets.UTF_8) + "&hash=" + hash;
    }

    @Test
    void authenticatesSignedTelegramInitData() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TelegramAuthFilter.HEADER, sign("{\"id\":777,\"first_name\":\"Ann\",\"username\":\"ann\"}", BOT_TOKEN));

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        var auth = SecurityContextHolder.getContext().getAuthentication();
        TelegramAuthenticationToken token = assertInstanceOf(TelegramAuthenticationToken.class, auth);
        assertEquals(777L, token.getTelegramId());
        assertEquals(Messenger.TELEGRAM, token.getMessenger());
        assertEquals("Ann", token.getFirstName());
    }

    @Test
    void rejectsInitDataSignedWithAnotherBot() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TelegramAuthFilter.HEADER, sign("{\"id\":777}", "999:other-bot"));

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void ignoresMaxHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(MaxAuthFilter.HEADER, sign("{\"id\":777}", BOT_TOKEN));

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
