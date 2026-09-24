package com.join.back.parser.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses price strings from external sources.
 * Handles formats like "от 500 руб.", "бесплатно", "500–1500 руб.", etc.
 */
@Component
public class PriceParser {

    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d[\\d\\s]*\\d|\\d)");

    /**
     * Parses a raw price string to BigDecimal.
     *
     * @param rawPrice raw price string (e.g., "от 500 руб.", "бесплатно", null)
     * @return BigDecimal price value, 0 for free events, null if cannot parse
     */
    public BigDecimal parsePrice(String rawPrice) {
        if (rawPrice == null || rawPrice.isBlank()) {
            return null;
        }

        String lower = rawPrice.toLowerCase().trim();

        if (lower.contains("бесплатно") || lower.equals("free") || lower.equals("0")) {
            return BigDecimal.ZERO;
        }

        Matcher m = NUMBER_PATTERN.matcher(lower);
        if (m.find()) {
            String digits = m.group(1).replaceAll("\\s+", "");
            try {
                return new BigDecimal(digits);
            } catch (NumberFormatException e) {
                return null;
            }
        }

        return null;
    }
}
