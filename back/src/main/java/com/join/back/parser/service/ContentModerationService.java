package com.join.back.parser.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Service for detecting explicit/adult content in event titles and descriptions.
 * Used by the parser to filter out inappropriate events before saving.
 */
@Slf4j
@Service
public class ContentModerationService {

    /**
     * Stop-words list covering Russian and English explicit/adult content keywords.
     * All entries are lower-case; matching is case-insensitive.
     */
    private static final List<String> STOP_WORDS = List.of(
            // Russian — roots/stems to cover inflections
            "стриптиз",
            "эроти",         // catches: эротика, эротики, эротике, эротический, эротические, эротическое, эротизм
            "порно",         // catches: порно, порнография
            "секс",          // catches: секс, секси, сексуальный
            "интим услуги",
            "интим-услуги",
            "интимн",        // catches: интимный, интимные, интимная
            "интим салон",
            "нагот",         // catches: нагота, наготы
            "голый",
            "голая",
            "голые",
            "раздевани",     // catches: раздевание, раздевания
            "18+",
            "21+",
            "клуб для взрослых",
            "для взрослых 18",
            "стриптизёрш",
            "стриптизерш",
            "эскорт",
            // English
            "strip",         // catches: strip, striptease, stripper
            "erotic",        // catches: erotic, erotica, eroticism
            "porn",          // catches: porn, pornography, pornographic
            "nud",           // catches: nude, nudity, nudist
            "naked",
            "adult",         // catches: adult, adult content, adult show; also adultery (intentional — safety-first)
            "sex",           // catches: sex, sexual, sexy, sex show
            "intimate",
            "burlesque",
            "peep show",
            "lap dance",
            "gentlemen club",
            "escort"
    );

    /** "xxx" as a separate word only — Roman numerals like "XXXI фестиваль" stay clean. */
    private static final Pattern XXX = Pattern.compile("(?<![\\p{L}\\d])xxx(?![\\p{L}\\d])");

    /**
     * Checks whether the given title and/or description contain explicit content
     * by scanning for stop-words (case-insensitive).
     *
     * @param title       event title, may be null
     * @param description event description, may be null
     * @return {@code true} if explicit content detected, {@code false} otherwise
     */
    public boolean isExplicitContent(String title, String description) {
        String combined = buildSearchText(title, description);
        if (combined.isEmpty()) {
            return false;
        }

        for (String stopWord : STOP_WORDS) {
            if (combined.contains(stopWord)) {
                log.debug("Explicit content detected — matched stop-word '{}' in text: '{}'",
                        stopWord, truncate(combined, 120));
                return true;
            }
        }
        if (XXX.matcher(combined).find()) {
            log.debug("Explicit content detected — matched 'xxx' in text: '{}'", truncate(combined, 120));
            return true;
        }
        return false;
    }

    // ── private helpers ────────────────────────────────────────────────────────

    private String buildSearchText(String title, String description) {
        StringBuilder sb = new StringBuilder();
        if (title != null && !title.isBlank()) {
            sb.append(title.toLowerCase(Locale.ROOT));
        }
        if (description != null && !description.isBlank()) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(description.toLowerCase(Locale.ROOT));
        }
        return sb.toString();
    }

    private String truncate(String text, int maxLength) {
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "…";
    }
}
