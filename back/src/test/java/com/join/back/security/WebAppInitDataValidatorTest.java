package com.join.back.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class WebAppInitDataValidatorTest {

    private static final String BOT_TOKEN = "123456789:TEST_TOKEN_not_a_real_bot_token_000";

    private WebAppInitDataValidator validator;

    @BeforeEach
    void setUp() {
        validator = new WebAppInitDataValidator();
    }

    @Test
    void shouldValidateCorrectInitData() throws Exception {
        String userJson = "{\"id\":123456789,\"first_name\":\"John\",\"last_name\":\"Doe\",\"username\":\"johndoe\"}";
        String initData = buildInitData(userJson, BOT_TOKEN);

        boolean result = validator.validate(initData, BOT_TOKEN);

        assertTrue(result);
    }

    @Test
    void shouldRejectInvalidHash() {
        String initData = "user=%7B%22id%22%3A123456789%7D&auth_date=1234567890&hash=invalidhash";

        boolean result = validator.validate(initData, BOT_TOKEN);

        assertFalse(result);
    }

    @Test
    void shouldRejectMissingHash() {
        String initData = "user=%7B%22id%22%3A123456789%7D&auth_date=1234567890";

        boolean result = validator.validate(initData, BOT_TOKEN);

        assertFalse(result);
    }

    @Test
    void shouldRejectEmptyInitData() {
        boolean result = validator.validate("", BOT_TOKEN);

        assertFalse(result);
    }

    @Test
    void shouldRejectNullInitData() {
        boolean result = validator.validate(null, BOT_TOKEN);

        assertFalse(result);
    }

    @Test
    void shouldParseInitDataCorrectly() {
        String initData = "user=%7B%22id%22%3A123%7D&auth_date=1234567890&hash=abc123";

        Map<String, String> params = validator.parseInitData(initData);

        assertEquals("{\"id\":123}", params.get("user"));
        assertEquals("1234567890", params.get("auth_date"));
        assertEquals("abc123", params.get("hash"));
    }

    @Test
    void shouldRejectWrongBotToken() throws Exception {
        String userJson = "{\"id\":123456789,\"first_name\":\"John\"}";
        String initData = buildInitData(userJson, BOT_TOKEN);

        boolean result = validator.validate(initData, "wrong-bot-token");

        assertFalse(result);
    }

    private String buildInitData(String userJson, String botToken) throws Exception {
        String encodedUser = URLEncoder.encode(userJson, StandardCharsets.UTF_8);
        String authDate = "1234567890";

        TreeMap<String, String> params = new TreeMap<>();
        params.put("user", userJson);
        params.put("auth_date", authDate);

        StringBuilder dataCheckString = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!first) {
                dataCheckString.append("\n");
            }
            dataCheckString.append(entry.getKey()).append("=").append(entry.getValue());
            first = false;
        }

        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec("WebAppData".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKeySpec);
        byte[] secretKey = mac.doFinal(botToken.getBytes(StandardCharsets.UTF_8));

        Mac mac2 = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec2 = new SecretKeySpec(secretKey, "HmacSHA256");
        mac2.init(secretKeySpec2);
        byte[] hashBytes = mac2.doFinal(dataCheckString.toString().getBytes(StandardCharsets.UTF_8));

        StringBuilder hexString = new StringBuilder();
        for (byte b : hashBytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }

        return "user=" + encodedUser + "&auth_date=" + authDate + "&hash=" + hexString;
    }
}
