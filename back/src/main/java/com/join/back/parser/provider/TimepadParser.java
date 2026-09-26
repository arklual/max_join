package com.join.back.parser.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.join.back.model.entity.EventSource;
import com.join.back.parser.config.ParserProperties;
import com.join.back.parser.dto.RawExternalEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Timepad afisha (afisha.timepad.ru): the public JSON API behind the site,
 * {@code GET https://ontp.timepad.ru/api/events?cityId=…&limit=…&registrationOpened=1&offset[]=…}.
 * No token is needed, unlike the developer API at api.timepad.ru.
 * <p>
 * Timepad is full of business meetups and courses; only leisure categories are kept, except for pinned
 * organizers ({@code parser.sources.timepad.organizations}) whose events are all taken.
 * Each event becomes one card dated by its nearest upcoming session.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TimepadParser implements EventProvider {

    private static final String USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
    private static final long RUSSIA_COUNTRY_ID = 3159;
    private static final ZoneId ZONE = ZoneId.of("Europe/Moscow");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_DESCRIPTION = 1000;

    /** Afisha categories worth finding a companion for (lower case). */
    static final Set<String> LEISURE_CATEGORIES = Set.of(
            "концерты", "вечеринки", "театры", "кино", "выставки", "искусство и культура",
            "экскурсии и путешествия", "хобби и творчество", "спорт", "интеллектуальные игры",
            "другие развлечения", "еда", "наука");
    /** Categories that rule an event out even next to a leisure one. */
    static final Set<String> EXCLUDED_CATEGORIES = Set.of("для детей", "бизнес");

    private final ParserProperties parserProperties;
    private final ObjectMapper objectMapper;

    @Override
    public EventSource getSource() {
        return EventSource.TIMEPAD;
    }

    @Override
    public List<RawExternalEvent> fetchEvents() {
        ParserProperties.TimepadConfig config = parserProperties.getSources().getTimepad();
        if (!config.isEnabled()) {
            log.info("Timepad parser is disabled, skipping");
            return List.of();
        }

        RestClient restClient = RestClient.builder()
                .baseUrl(config.getBaseUrl())
                .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
                .defaultHeader(HttpHeaders.ORIGIN, config.getAfishaUrl())
                .defaultHeader(HttpHeaders.REFERER, config.getAfishaUrl() + "/")
                .build();
        LocalDate today = LocalDate.now(ZONE);
        Map<String, RawExternalEvent> result = new LinkedHashMap<>();

        // Pinned organizers first: all their events, whatever the category.
        for (Long orgId : config.getOrganizations()) {
            int before = result.size();
            if (!fetchPages(restClient, config, "orgIds[]", orgId, false, today, result)) {
                return new ArrayList<>(result.values());
            }
            log.info("Timepad: {} events of organizer {}", result.size() - before, orgId);
        }

        for (String city : config.getCities()) {
            Long cityId = resolveCityId(restClient, city);
            if (cityId == null) {
                log.warn("Timepad: unknown city '{}', skipping", city);
                continue;
            }
            int before = result.size();
            if (!fetchPages(restClient, config, "cityId", cityId, true, today, result)) {
                break;
            }
            log.info("Timepad: {} leisure events for {}", result.size() - before, city);
        }
        return new ArrayList<>(result.values());
    }

    /** Pages through /events filtered by one parameter; returns false if interrupted. */
    private boolean fetchPages(RestClient restClient, ParserProperties.TimepadConfig config, String filter,
                               Object filterValue, boolean leisureOnly, LocalDate today,
                               Map<String, RawExternalEvent> result) {
        List<String> cursor = List.of();
        for (int page = 0; page < config.getMaxPages(); page++) {
            final List<String> offset = cursor;
            String body;
            try {
                body = restClient.get()
                        .uri(uriBuilder -> {
                            uriBuilder.path("/events")
                                    .queryParam(filter, filterValue)
                                    .queryParam("limit", config.getLimit())
                                    .queryParam("registrationOpened", 1);
                            offset.forEach(o -> uriBuilder.queryParam("offset[]", o));
                            return uriBuilder.build();
                        })
                        .retrieve()
                        .body(String.class);
            } catch (RestClientException e) {
                log.warn("Timepad request failed for {}={} (page {}): {}", filter, filterValue, page, e.getMessage());
                return true;
            }
            Page parsed = parse(body, config.getAfishaUrl(), today, leisureOnly);
            parsed.events().forEach(e -> result.putIfAbsent(e.getExternalId(), e));
            if (parsed.fetched() < config.getLimit() || parsed.nextOffset().isEmpty()) {
                return true;
            }
            cursor = parsed.nextOffset();
            if (!sleep(config.getRequestDelayMs())) {
                return false;
            }
        }
        return true;
    }

    record Page(List<RawExternalEvent> events, int fetched, List<String> nextOffset) {
    }

    private Long resolveCityId(RestClient restClient, String city) {
        try {
            JsonNode cities = objectMapper.readTree(restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/dictionaries/filters-cities")
                            .queryParam("countryId", RUSSIA_COUNTRY_ID)
                            .queryParam("name", city)
                            .build())
                    .retrieve()
                    .body(String.class)).path("cities");
            for (JsonNode node : cities) {
                if ("city".equals(node.path("type").asText()) && city.equalsIgnoreCase(node.path("title").asText())) {
                    return node.path("id").asLong();
                }
            }
        } catch (Exception e) {
            log.warn("Timepad: cannot resolve city '{}': {}", city, e.getMessage());
        }
        return null;
    }

    /** Parses one page of {@code {"data":{"events":[…],"offset":[…]}}}; {@code leisureOnly} applies the category filter. */
    Page parse(String body, String afishaUrl, LocalDate today, boolean leisureOnly) {
        List<RawExternalEvent> out = new ArrayList<>();
        JsonNode data;
        try {
            data = objectMapper.readTree(body).path("data");
        } catch (Exception e) {
            log.warn("Timepad: cannot parse response: {}", e.getMessage());
            return new Page(out, 0, List.of());
        }
        JsonNode events = data.path("events");
        for (JsonNode event : events) {
            try {
                RawExternalEvent raw = parseEvent(event, afishaUrl, today, leisureOnly);
                if (raw != null) {
                    out.add(raw);
                }
            } catch (Exception e) {
                log.debug("Timepad: skipping event {}: {}", event.path("id").asText(), e.getMessage());
            }
        }
        List<String> nextOffset = new ArrayList<>();
        data.path("offset").forEach(o -> nextOffset.add(o.asText()));
        return new Page(out, events.size(), nextOffset);
    }

    private RawExternalEvent parseEvent(JsonNode event, String afishaUrl, LocalDate today, boolean leisureOnly) {
        String id = event.path("id").asText("");
        String title = event.path("name").asText("").replaceAll("\\s+", " ").trim();
        if (id.isEmpty() || title.isEmpty() || event.path("online").asBoolean(false)) {
            return null;
        }

        List<String> categories = new ArrayList<>();
        boolean leisure = false;
        for (JsonNode category : event.path("categories")) {
            String name = category.path("name").asText("").trim();
            if (name.isEmpty()) continue;
            String key = name.toLowerCase();
            if (leisureOnly && EXCLUDED_CATEGORIES.contains(key)) {
                return null;
            }
            leisure |= LEISURE_CATEGORIES.contains(key);
            categories.add(name);
        }
        if (leisureOnly && !leisure) {
            return null;
        }

        // Nearest session that hasn't started before today; long-running events without one are skipped.
        JsonNode session = null;
        LocalDateTime start = null;
        for (JsonNode candidate : event.path("sessions")) {
            LocalDateTime candidateStart = parseDateTime(candidate.path("startDate").asText(null));
            if (candidateStart != null && !candidateStart.toLocalDate().isBefore(today)
                    && (start == null || candidateStart.isBefore(start))) {
                start = candidateStart;
                session = candidate;
            }
        }
        if (start == null) {
            session = event.path("computed");
            start = parseDateTime(session.path("startDate").asText(null));
            if (start == null || start.toLocalDate().isBefore(today)) {
                return null;
            }
        }

        JsonNode address = event.path("address");
        String city = textOrNull(address.path("city"));
        String cityAlias = textOrNull(address.path("cityAlias"));
        String slug = textOrNull(event.path("slug"));
        String ticketUrl = cityAlias != null && slug != null
                ? afishaUrl + "/" + cityAlias + "/events/" + slug
                : textOrNull(event.path("shareLink"));

        String fullDescription = stripTags(event.path("description").asText(""));
        String description = textOrNull(event.path("shortDescription"));
        if (description == null) {
            description = fullDescription;
        }
        String titleContext = String.join("\n",
                description != null ? description : "", fullDescription != null ? fullDescription : "");
        if (description != null && description.length() > MAX_DESCRIPTION) {
            description = description.substring(0, MAX_DESCRIPTION - 1).trim() + "…";
        }

        JsonNode minPrice = session.path("minPrice");
        return RawExternalEvent.builder()
                .externalId(id)
                .source(EventSource.TIMEPAD)
                .title(title.length() > 255 ? title.substring(0, 255) : title)
                .description(description)
                .titleContext(titleContext)
                .rawCategories(categories)
                .imageUrl(textOrNull(event.path("poster")))
                .minPrice(minPrice.isNumber() ? minPrice.decimalValue().max(BigDecimal.ZERO) : null)
                .eventDate(start.toLocalDate())
                .eventTime(start.toLocalTime())
                .ticketUrl(ticketUrl)
                .city(city)
                .build();
    }

    private static LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim(), DATE_TIME);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String textOrNull(JsonNode node) {
        if (!node.isTextual() || node.asText().isBlank()) {
            return null;
        }
        return node.asText().trim();
    }

    private static String stripTags(String html) {
        String text = org.jsoup.Jsoup.parse(html).text().replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
        return text.isEmpty() ? null : text;
    }

    private static boolean sleep(long ms) {
        if (ms <= 0) return true;
        try {
            Thread.sleep(ms);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
