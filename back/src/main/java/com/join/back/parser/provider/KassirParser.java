package com.join.back.parser.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.join.back.model.entity.EventSource;
import com.join.back.parser.config.CityCodes;
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
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Kassir.ru — the biggest regional ticket seller, one host per city (ekb.kassir.ru, nsk.kassir.ru…).
 * Uses the JSON API of its own site, no token:
 * {@code GET https://api.kassir.ru/api/page-kit?domain=<host>&slug=/<category>&pageSize=200&currentPage=N&date_from=&date_to=}.
 * <p>
 * Items are either a single session ({@code event}, {@code beginsAt}) or a show with several sessions
 * ({@code activity}, {@code dateRange.beginsAt} = its first session). Times are local wall-clock times
 * with a meaningless {@code +00:00} suffix. {@code isPushkin} marks the Pushkin card — for every city.
 * Shows that started before today (permanent exhibitions, long runs) have no known next date and are skipped.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KassirParser implements EventProvider {

    private static final String USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    /** Category page → the name shown as a tag (and mapped to a type in CategoryMapper). */
    static final Map<String, String> CATEGORY_NAMES = Map.of(
            "bilety-na-koncert", "Концерт",
            "bilety-v-teatr", "Театр",
            "bilety-na-shou", "Шоу",
            "bilety-na-standup", "Стендап",
            "bilety-na-festival", "Фестивали",
            "bilety-na-sportivnye-meropriyatiya", "Спорт",
            "bilety-na-vystavki", "Выставки",
            "bilety-na-ekskursii", "Экскурсии");

    private final ParserProperties parserProperties;
    private final ObjectMapper objectMapper;

    @Override
    public EventSource getSource() {
        return EventSource.KASSIR;
    }

    @Override
    public List<RawExternalEvent> fetchEvents() {
        ParserProperties.KassirConfig config = parserProperties.getSources().getKassir();
        if (!config.isEnabled()) {
            log.info("Kassir parser is disabled, skipping");
            return List.of();
        }
        RestClient restClient = RestClient.builder()
                .baseUrl(config.getBaseUrl())
                .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
                .build();
        List<RawExternalEvent> result = new ArrayList<>();

        for (String city : parserProperties.getCities()) {
            String host = CityCodes.kassir(city);
            if (host == null) {
                log.info("Kassir doesn't cover {}, skipping", city);
                continue;
            }
            LocalDate today = LocalDate.now(CityCodes.zone(city));
            // Items repeat across pages and categories: one card per item.
            Map<String, RawExternalEvent> cityEvents = new LinkedHashMap<>();
            for (String category : config.getCategories()) {
                for (int page = 1; page <= config.getMaxPages(); page++) {
                    final int currentPage = page;
                    String body;
                    try {
                        body = restClient.get()
                                .uri(uriBuilder -> uriBuilder.path("/page-kit")
                                        .queryParam("domain", host)
                                        .queryParam("slug", "/" + category)
                                        .queryParam("pageSize", config.getPageSize())
                                        .queryParam("currentPage", currentPage)
                                        .queryParam("date_from", today)
                                        .queryParam("date_to", today.plusDays(config.getDaysAhead()))
                                        .build())
                                .retrieve()
                                .body(String.class);
                    } catch (RestClientException e) {
                        log.warn("Kassir request failed for {} {} page {}: {}", city, category, page, e.getMessage());
                        break;
                    }
                    Page parsed = parse(body, city, CATEGORY_NAMES.getOrDefault(category, category), today);
                    parsed.events().forEach(e -> cityEvents.putIfAbsent(e.getExternalId(), e));
                    if (!sleep(config.getRequestDelayMs())) {
                        return result;
                    }
                    if (page >= parsed.pagesCount()) {
                        break;
                    }
                }
            }
            log.info("Kassir: {} events for {}", cityEvents.size(), city);
            result.addAll(cityEvents.values());
        }
        return result;
    }

    record Page(List<RawExternalEvent> events, int pagesCount) {
    }

    /** Parses one {@code page-kit} response. */
    Page parse(String body, String city, String categoryName, LocalDate today) {
        List<RawExternalEvent> out = new ArrayList<>();
        JsonNode search;
        try {
            JsonNode root = objectMapper.readTree(body);
            search = (root.has("kit") ? root.path("kit") : root).path("searchResult");
        } catch (Exception e) {
            log.warn("Kassir: cannot parse response: {}", e.getMessage());
            return new Page(out, 0);
        }
        for (JsonNode item : search.path("items")) {
            try {
                RawExternalEvent raw = parseItem(item.path("type").asText(""), item.path("object"), city, categoryName, today);
                if (raw != null) {
                    out.add(raw);
                }
            } catch (Exception e) {
                log.debug("Kassir: skipping item: {}", e.getMessage());
            }
        }
        return new Page(out, search.path("pagination").path("pagesCount").asInt(0));
    }

    private RawExternalEvent parseItem(String type, JsonNode object, String city, String categoryName, LocalDate today) {
        String id = object.path("id").asText("");
        String title = object.path("title").asText("").replaceAll("\\s+", " ").trim();
        String url = text(object.path("url"));
        if (id.isEmpty() || title.isEmpty() || url == null) {
            return null;
        }
        String begins = "activity".equals(type)
                ? text(object.path("dateRange").path("beginsAt"))
                : text(object.path("beginsAt"));
        LocalDateTime start = localDateTime(begins);
        if (start == null || start.toLocalDate().isBefore(today)) {
            return null;
        }

        JsonNode minPrice = object.path("priceRange").path("min");
        String venue = text(object.path("venueName"));
        if (venue == null) {
            venue = text(object.path("venue").path("name"));
        }
        String address = text(object.path("venue").path("address").path("addressString"));
        String description = venue == null ? null : address == null ? venue : venue + " — " + address;

        return RawExternalEvent.builder()
                .externalId(type + "-" + id)
                .source(EventSource.KASSIR)
                .title(title.length() > 255 ? title.substring(0, 255) : title)
                .description(description)
                .rawCategories(List.of(categoryName))
                .imageUrl(text(object.path("posterImage")))
                .minPrice(minPrice.isNumber() ? minPrice.decimalValue().max(BigDecimal.ZERO) : null)
                .eventDate(start.toLocalDate())
                .eventTime(start.toLocalTime())
                .ticketUrl(url)
                .city(city)
                .pushkinCard(object.path("isPushkin").asBoolean(false))
                .build();
    }

    /** "2026-10-15T19:00:00+00:00" is 19:00 local time: the offset is ignored. */
    private static LocalDateTime localDateTime(String value) {
        if (value == null || value.length() < 19) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.substring(0, 19));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String text(JsonNode node) {
        return node.isTextual() && !node.asText().isBlank() ? node.asText().trim() : null;
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
