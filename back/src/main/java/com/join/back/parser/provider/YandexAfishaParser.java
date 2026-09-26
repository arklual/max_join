package com.join.back.parser.provider;

import com.join.back.model.entity.EventSource;
import com.join.back.parser.config.ParserProperties;
import com.join.back.parser.dto.RawExternalEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for Yandex Afisha events (https://afisha.yandex.ru).
 * <p>
 * Yandex Afisha uses a React/Apollo GraphQL frontend with SSR.
 * The initial Apollo cache state is embedded in the page HTML
 * inside a {@code window['__APOLLO_STATE__'] = {...}} script block.
 * <p>
 * This parser:
 * 1. Downloads the city's main afisha page
 * 2. Extracts the Apollo state script
 * 3. Parses EventPreview and ActualEvent objects using regex
 * 4. Maps them to {@link RawExternalEvent}
 * <p>
 * Note: price values from Yandex Afisha are in kopecks (1 ruble = 100 kopecks).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class YandexAfishaParser implements EventProvider {

    private static final String USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    // Matches the Apollo state script tag content
    private static final Pattern APOLLO_STATE_PATTERN = Pattern.compile(
            "window\\['__APOLLO_STATE__'\\]\\s*=\\s*(\\{.*?)\\s*;\\s*(?=</script>)",
            Pattern.DOTALL
    );

    // Matches an ActualEvent block: contains event ref + scheduleInfo
    // "__typename":"ActualEvent","event":{"__ref":"EventPreview:<id>"},"scheduleInfo":{...dates...}
    private static final Pattern ACTUAL_EVENT_PATTERN = Pattern.compile(
            "\"__typename\":\"ActualEvent\",\"event\":\\{\"__ref\":\"EventPreview:([0-9a-f]+)\"\\}," +
            "\"scheduleInfo\":\\{\"__typename\":\"EventScheduleInfo\",\"dates\":\\[([^]]*)]"
    );

    // Matches EventPreview block: "EventPreview:<id>":{...,"title":"...","url":"...",...}
    // We extract individual fields by targeted patterns below
    private static final Pattern EVENT_PREVIEW_KEY_PATTERN = Pattern.compile(
            "\"EventPreview:([0-9a-f]+)\":\\{\"__typename\":\"EventPreview\""
    );

    // Field extractors within an EventPreview block
    private static final Pattern FIELD_TITLE = Pattern.compile("\"title\":\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern FIELD_URL = Pattern.compile("\"url\":\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern FIELD_ARGUMENT = Pattern.compile("\"argument\":\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern FIELD_IMAGE_URL = Pattern.compile(
            "\"image\\(\\{\\\\\"size\\\\\":\\\\\"s380x190_crop\\\\\"\\}\\)\":\\{\"__typename\":\"MediaImageSize\",\"url\":\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern FIELD_PRICES = Pattern.compile("\"prices\":\\[([^]]*)]");
    private static final Pattern FIELD_MONEY_VALUE = Pattern.compile(
            "\"__typename\":\"Money\",\"currency\":\"rub\",\"value\":(\\d+)"
    );
    private static final String ACTUAL_EVENT_MARKER = "\"__typename\":\"ActualEvent\"";
    private static final Pattern FIELD_TAG_CODE = Pattern.compile(
            "\"__typename\":\"Tag\",\"code\":\"([^\"]+)\""
    );

    // City name mapping from URL slug to Russian name
    private static final Map<String, String> CITY_MAP = Map.of(
            "moscow", "Москва",
            "spb", "Санкт-Петербург",
            "novosibirsk", "Новосибирск",
            "ekaterinburg", "Екатеринбург",
            "kazan", "Казань",
            "krasnodar", "Краснодар",
            "sochi", "Сочи"
    );

    // Config city codes whose Yandex Afisha URL slug differs ("spb" redirects away).
    private static final Map<String, String> URL_SLUG = Map.of(
            "spb", "saint-petersburg"
    );

    /** Yandex Afisha selection of events payable with the Pushkin card. */
    static final String PUSHKIN_SELECTION = "/selections/all-events-pushkin-card";

    private final ParserProperties parserProperties;

    @Override
    public EventSource getSource() {
        return EventSource.YANDEX_AFISHA;
    }

    @Override
    public List<RawExternalEvent> fetchEvents() {
        ParserProperties.YandexAfishaConfig config = parserProperties.getSources().getYandexAfisha();

        if (!config.isEnabled()) {
            log.info("YandexAfisha parser is disabled, skipping");
            return List.of();
        }

        List<RawExternalEvent> result = new ArrayList<>();
        RestClient restClient = buildRestClient();

        for (String city : config.getCities()) {
            log.info("Fetching Yandex Afisha events for city: {}", city);
            try {
                String html = fetchCityPage(restClient, config.getBaseUrl(), city);
                if (html == null || html.isBlank()) {
                    log.warn("Empty response from Yandex Afisha for city: {}", city);
                    continue;
                }

                String apolloScript = extractApolloStateScript(html);
                if (apolloScript == null) {
                    log.warn("Could not find __APOLLO_STATE__ script for city: {}", city);
                    continue;
                }

                List<RawExternalEvent> cityEvents = parseApolloState(apolloScript, city, config.getBaseUrl());
                log.info("Parsed {} events from Yandex Afisha for city: {}", cityEvents.size(), city);
                result.addAll(cityEvents);

                // Pushkin-card selection: same events, flagged as payable with the card.
                if (config.getRequestDelayMs() > 0) {
                    Thread.sleep(config.getRequestDelayMs());
                }
                List<RawExternalEvent> pushkinEvents = fetchPushkinSelection(restClient, config.getBaseUrl(), city);
                log.info("Parsed {} Pushkin-card events from Yandex Afisha for city: {}", pushkinEvents.size(), city);
                result.addAll(pushkinEvents);

                if (config.getRequestDelayMs() > 0) {
                    Thread.sleep(config.getRequestDelayMs());
                }

            } catch (RestClientException e) {
                log.error("HTTP error fetching Yandex Afisha for city {}: {}", city, e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("YandexAfisha parser interrupted");
                return result;
            } catch (Exception e) {
                log.error("Unexpected error fetching Yandex Afisha for city {}: {}", city, e.getMessage());
            }
        }

        log.info("Total Yandex Afisha events fetched: {}", result.size());
        return result;
    }

    // ── private helpers ────────────────────────────────────────────────────────

    private RestClient buildRestClient() {
        return RestClient.builder()
                .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
                .defaultHeader(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .defaultHeader(HttpHeaders.ACCEPT_LANGUAGE, "ru-RU,ru;q=0.9,en;q=0.8")
                .build();
    }

    private List<RawExternalEvent> fetchPushkinSelection(RestClient restClient, String baseUrl, String city) {
        try {
            String html = restClient.get()
                    .uri(baseUrl + "/" + urlSlug(city) + PUSHKIN_SELECTION)
                    .retrieve()
                    .body(String.class);
            String apolloScript = html == null ? null : extractApolloStateScript(html);
            if (apolloScript == null) {
                log.warn("No Pushkin-card selection state for city: {}", city);
                return List.of();
            }
            List<RawExternalEvent> events = parseApolloState(apolloScript, city, baseUrl);
            events.forEach(e -> e.setPushkinCard(true));
            return events;
        } catch (RestClientException e) {
            log.warn("HTTP error fetching Yandex Pushkin-card selection for {}: {}", city, e.getMessage());
            return List.of();
        }
    }

    /** Yandex Afisha URL rubric → a category code {@link com.join.back.parser.service.CategoryMapper} knows. */
    private static final Map<String, String> RUBRIC_CATEGORY = Map.ofEntries(
            Map.entry("concert", "concert"),
            Map.entry("theatre_show", "theater"),
            Map.entry("musical", "theater"),
            Map.entry("ballet", "балет"),
            Map.entry("opera", "опера"),
            Map.entry("excursions", "tour"),
            Map.entry("art", "exhibition"),
            Map.entry("exhibition", "exhibition"),
            Map.entry("cinema", "cinema"),
            Map.entry("festival", "festival"),
            Map.entry("standup", "party"),
            Map.entry("kids", "детям")
    );

    static String rubricFromUrl(String url) {
        if (url == null) return null;
        String[] parts = url.replaceFirst("^https?://[^/]+", "").split("/");
        // ["", city, rubric, slug]
        return parts.length >= 4 ? RUBRIC_CATEGORY.get(parts[2]) : null;
    }

    private static String urlSlug(String city) {
        return URL_SLUG.getOrDefault(city, city);
    }

    private String fetchCityPage(RestClient restClient, String baseUrl, String city) {
        return restClient.get()
                .uri(baseUrl + "/" + urlSlug(city))
                .retrieve()
                .body(String.class);
    }

    /**
     * Extracts the raw content of the {@code window['__APOLLO_STATE__'] = ...} script.
     */
    private String extractApolloStateScript(String html) {
        // Find the script tag index containing __APOLLO_STATE__
        int markerIdx = html.indexOf("window['__APOLLO_STATE__']");
        if (markerIdx < 0) {
            return null;
        }

        // Find script start
        int scriptStart = html.lastIndexOf("<script", markerIdx);
        if (scriptStart < 0) {
            return null;
        }

        // Find content start (after >)
        int contentStart = html.indexOf('>', scriptStart);
        if (contentStart < 0) {
            return null;
        }

        // Find script end
        int contentEnd = html.indexOf("</script>", contentStart);
        if (contentEnd < 0) {
            return null;
        }

        return html.substring(contentStart + 1, contentEnd);
    }

    /**
     * Parses Apollo cache state to extract events.
     * <p>
     * Strategy:
     * 1. Find all ActualEvent blocks → get EventPreview IDs and dates
     * 2. For each EventPreview ID, extract its block and parse fields
     */
    /** Lowest ticket price in rubles from a {@code "prices":[Money…]} list (values are in kopecks). */
    static BigDecimal extractMinPrice(String fragment) {
        Matcher listMatcher = FIELD_PRICES.matcher(fragment);
        if (!listMatcher.find()) {
            return null;
        }
        Matcher valueMatcher = FIELD_MONEY_VALUE.matcher(listMatcher.group(1));
        Long min = null;
        while (valueMatcher.find()) {
            long kopecks = Long.parseLong(valueMatcher.group(1));
            if (min == null || kopecks < min) {
                min = kopecks;
            }
        }
        return min == null ? null : BigDecimal.valueOf(min).divide(BigDecimal.valueOf(100));
    }

    List<RawExternalEvent> parseApolloState(String script, String citySlug, String baseUrl) {
        List<RawExternalEvent> events = new ArrayList<>();

        // Step 1: extract (eventId → firstDate, prices) from ActualEvent blocks.
        // Prices and the Pushkin-card flag live in scheduleInfo, not in EventPreview.
        Map<String, String> eventIdToFirstDate = new HashMap<>();
        Map<String, BigDecimal> eventIdToMinPrice = new HashMap<>();
        Set<String> pushkinEventIds = new HashSet<>();
        Matcher actualMatcher = ACTUAL_EVENT_PATTERN.matcher(script);
        while (actualMatcher.find()) {
            String eventId = actualMatcher.group(1);
            String datesRaw = actualMatcher.group(2); // e.g. "2026-03-22","2026-03-26",...
            String firstDate = extractFirstDate(datesRaw);
            if (firstDate != null) {
                eventIdToFirstDate.put(eventId, firstDate);
            }
            int scheduleEnd = script.indexOf(ACTUAL_EVENT_MARKER, actualMatcher.end());
            String schedule = script.substring(actualMatcher.end(), scheduleEnd < 0 ? script.length() : scheduleEnd);
            BigDecimal minPrice = extractMinPrice(schedule);
            if (minPrice != null) {
                eventIdToMinPrice.put(eventId, minPrice);
            }
            if (schedule.contains("\"pushkinCardAllowed\":true")) {
                pushkinEventIds.add(eventId);
            }
        }

        log.debug("Found {} ActualEvent entries for city {}", eventIdToFirstDate.size(), citySlug);

        // Step 2: for each event ID, find its EventPreview block and parse
        String cityName = CITY_MAP.getOrDefault(citySlug, toTitleCase(citySlug));

        Matcher previewKeyMatcher = EVENT_PREVIEW_KEY_PATTERN.matcher(script);
        while (previewKeyMatcher.find()) {
            String eventId = previewKeyMatcher.group(1);

            // Only process events that have schedule data
            String firstDate = eventIdToFirstDate.get(eventId);
            if (firstDate == null) {
                continue;
            }

            try {
                // Extract the full EventPreview block
                int blockStart = previewKeyMatcher.start();
                String block = extractBracedBlock(script, blockStart);
                if (block == null) {
                    continue;
                }

                RawExternalEvent event = parseEventPreviewBlock(eventId, block, firstDate, cityName, baseUrl);
                if (event != null) {
                    BigDecimal schedulePrice = eventIdToMinPrice.get(eventId);
                    if (event.getMinPrice() == null && schedulePrice != null) {
                        event.setMinPrice(schedulePrice);
                        event.setRawPrice("от " + schedulePrice.longValue() + " руб.");
                    }
                    if (pushkinEventIds.contains(eventId)) {
                        event.setPushkinCard(true);
                    }
                    events.add(event);
                }
            } catch (Exception e) {
                log.warn("Failed to parse EventPreview {}: {}", eventId, e.getMessage());
            }
        }

        return events;
    }

    /**
     * Extracts the first date string from a comma-separated quoted date list.
     * Input example: {@code "2026-03-22","2026-03-26","2026-03-27"}
     */
    private String extractFirstDate(String datesRaw) {
        if (datesRaw == null || datesRaw.isBlank()) {
            return null;
        }
        // Remove surrounding quotes and split
        String trimmed = datesRaw.trim();
        if (trimmed.startsWith("\"")) {
            // Extract first quoted value
            int end = trimmed.indexOf('"', 1);
            if (end > 1) {
                return trimmed.substring(1, end);
            }
        }
        return null;
    }

    /**
     * Extracts a JSON-like brace-balanced block starting at {@code fromIndex}.
     * Finds the first {@code {} after {@code fromIndex} and matches the closing one.
     */
    private String extractBracedBlock(String script, int fromIndex) {
        int braceStart = script.indexOf('{', fromIndex);
        if (braceStart < 0) {
            return null;
        }
        int depth = 0;
        for (int i = braceStart; i < script.length(); i++) {
            char c = script.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return script.substring(fromIndex, i + 1);
                }
            }
        }
        return null;
    }

    private RawExternalEvent parseEventPreviewBlock(
            String eventId, String block, String firstDateStr, String city, String baseUrl) {

        // Title
        String title = extractFirstMatch(FIELD_TITLE, block);
        if (title == null || title.isBlank()) {
            log.warn("Skipping YandexAfisha event {} — no title", eventId);
            return null;
        }
        title = unescapeUnicode(title);
        if (title.length() > 255) {
            title = title.substring(0, 255);
        }

        // Description from "argument" field (short promo text)
        String description = extractFirstMatch(FIELD_ARGUMENT, block);
        if (description != null) {
            description = unescapeUnicode(description);
        }

        // URL → ticketUrl
        String urlPath = extractFirstMatch(FIELD_URL, block);
        String ticketUrl = null;
        if (urlPath != null) {
            ticketUrl = baseUrl + unescapeUnicode(urlPath);
        }

        // Image URL
        String imageUrl = extractFirstMatch(FIELD_IMAGE_URL, block);
        if (imageUrl != null) {
            imageUrl = unescapeUnicode(imageUrl);
        }

        // Price: Yandex stores prices in kopecks, convert to rubles
        BigDecimal minPrice = extractMinPrice(block);
        String rawPrice = minPrice == null ? null : "от " + minPrice.longValue() + " руб.";

        // Categories: the rubric from the event URL (/city/<rubric>/slug) is the most
        // reliable signal, selection pages often carry no genre tags at all.
        List<String> categories = new ArrayList<>();
        String rubric = rubricFromUrl(ticketUrl);
        if (rubric != null) {
            categories.add(rubric);
        }
        Matcher tagMatcher = FIELD_TAG_CODE.matcher(block);
        while (tagMatcher.find()) {
            String code = tagMatcher.group(1);
            // Include only meaningful category codes (skip internal tags)
            if (isPublicCategoryCode(code)) {
                categories.add(code);
            }
        }

        // Date
        LocalDate eventDate;
        try {
            eventDate = LocalDate.parse(firstDateStr);
        } catch (DateTimeParseException e) {
            log.warn("Skipping YandexAfisha event {} — unparseable date: {}", eventId, firstDateStr);
            return null;
        }

        return RawExternalEvent.builder()
                .externalId(eventId)
                .source(EventSource.YANDEX_AFISHA)
                .title(title)
                .description(description)
                .rawCategories(categories)
                .rawPrice(rawPrice)
                .minPrice(minPrice)
                .eventDate(eventDate)
                .eventTime(null) // Yandex Afisha doesn't expose time in the main page state
                .imageUrl(imageUrl)
                .ticketUrl(ticketUrl)
                .city(city)
                .build();
    }

    /**
     * Returns true for tag codes that represent real event categories/genres
     * (as opposed to internal CMS tags like "vertical-photo", "high-rated", etc.)
     */
    private boolean isPublicCategoryCode(String code) {
        return switch (code) {
            case "concert", "theatre", "musical", "cinema", "exhibition",
                 "show", "standup", "comedy", "drama", "opera", "ballet",
                 "festival", "sport", "kids", "lecture", "party",
                 "circus", "online", "excursion" -> true;
            default -> false;
        };
    }

    private String extractFirstMatch(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    /**
     * Decodes Unicode escape sequences like {@code \u002F} → {@code /}.
     */
    private String unescapeUnicode(String input) {
        if (input == null || !input.contains("\\u")) {
            return input;
        }
        StringBuilder sb = new StringBuilder(input.length());
        int i = 0;
        while (i < input.length()) {
            if (i + 5 < input.length()
                    && input.charAt(i) == '\\'
                    && input.charAt(i + 1) == 'u') {
                try {
                    int codePoint = Integer.parseInt(input.substring(i + 2, i + 6), 16);
                    sb.appendCodePoint(codePoint);
                    i += 6;
                    continue;
                } catch (NumberFormatException ignored) {
                    // Not a valid escape, treat as literal
                }
            }
            sb.append(input.charAt(i));
            i++;
        }
        return sb.toString();
    }

    private String toTitleCase(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }
        return Character.toUpperCase(input.charAt(0)) + input.substring(1).toLowerCase();
    }
}
