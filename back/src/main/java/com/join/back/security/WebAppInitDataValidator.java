package com.join.back.security;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

@Component
/**
 * Validates mini-app init data signed by a bot (same HMAC-SHA256 "WebAppData"
 * scheme in MAX and Telegram) — pass the respective bot token.
 */
public class WebAppInitDataValidator {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String WEB_APP_DATA = "WebAppData";

    public boolean validate(String initData, String botToken) {
        Map<String, String> params = parseInitData(initData);

        String receivedHash = params.remove("hash");
        if (receivedHash == null || receivedHash.isEmpty()) {
            return false;
        }

        TreeMap<String, String> sortedParams = new TreeMap<>(params);
        StringBuilder dataCheckString = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : sortedParams.entrySet()) {
            if (!first) {
                dataCheckString.append("\n");
            }
            dataCheckString.append(entry.getKey()).append("=").append(entry.getValue());
            first = false;
        }

        try {
            byte[] secretKey = hmacSha256(botToken.getBytes(StandardCharsets.UTF_8), WEB_APP_DATA.getBytes(StandardCharsets.UTF_8));
            byte[] computedHash = hmacSha256(dataCheckString.toString().getBytes(StandardCharsets.UTF_8), secretKey);
            String computedHashHex = bytesToHex(computedHash);

            return computedHashHex.equals(receivedHash);
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            return false;
        }
    }

    public Map<String, String> parseInitData(String initData) {
        Map<String, String> params = new LinkedHashMap<>();
        if (initData == null || initData.isEmpty()) {
            return params;
        }

        String[] pairs = initData.split("&");
        for (String pair : pairs) {
            int equalsIndex = pair.indexOf('=');
            if (equalsIndex > 0) {
                String key = URLDecoder.decode(pair.substring(0, equalsIndex), StandardCharsets.UTF_8);
                String value = URLDecoder.decode(pair.substring(equalsIndex + 1), StandardCharsets.UTF_8);
                params.put(key, value);
            }
        }
        return params;
    }

    private byte[] hmacSha256(byte[] data, byte[] key) throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance(HMAC_SHA256);
        SecretKeySpec secretKeySpec = new SecretKeySpec(key, HMAC_SHA256);
        mac.init(secretKeySpec);
        return mac.doFinal(data);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
