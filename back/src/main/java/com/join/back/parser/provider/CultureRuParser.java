package com.join.back.parser.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.join.back.model.entity.EventSource;
import com.join.back.parser.config.ParserProperties;
import com.join.back.parser.dto.RawExternalEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Official Pushkin card events from PRO.Культура.РФ (Минкультуры России):
 * {@code GET /api/2.5/pushkinsCardEvents?apiKey=…&status=accepted&limit=…&offset=…}.
 * <p>
 * The API is available to partners by key. Without a key the provider does nothing —
 * the integration is never imitated with made-up data.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CultureRuParser implements EventProvider {

    private static final ZoneId ZONE = ZoneId.of("Europe/Moscow");

    private final ParserProperties parserProperties;
    private final ObjectMapper objectMapper;

    @Override
    public EventSource getSource() {
        return EventSource.CULTURE_RU;
    }

    @Override
    public List<RawExternalEvent> fetchEvents() {
        ParserProperties.CultureRuConfig config = parserProperties.getSources().getCultureRu();
        if (!config.isEnabled()) {
            log.info("PRO.Культура.РФ parser is disabled, skipping");
            return List.of();
        }
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            log.info("PRO.Культура.РФ parser: no API key (CULTURE_RU_API_KEY) — skipping the official Pushkin card registry");
            return List.of();
        }

        RestClient restClient = RestClient.builder().baseUrl(config.getBaseUrl()).build();
        List<RawExternalEvent> result = new ArrayList<>();
        long now = Instant.now().toEpochMilli();

        for (String city : config.getCities()) {
            int offset = 0;
            for (int page = 0; page < config.getMaxPages(); page++) {
                final int currentOffset = offset;
                String body;
                try {
                    body = restClient.get()
                            .uri(uriBuilder -> uriBuilder
                                    .path("/pushkinsCardEvents")
                                    .queryParam("apiKey", config.getApiKey())
                                    .queryParam("status", "accepted")
                                    .queryParam("cityName", city)
                                    .queryParam("start", now)
                                    .queryParam("sort", "start")
                                    .queryParam("limit", config.getPageSize())
                                    .queryParam("offset", currentOffset)
                                    .build())
                            .retrieve()
                            .body(String.class);
                } catch (RestClientException e) {
                    log.warn("PRO.Культура.РФ request failed for {} (offset {}): {}", city, currentOffset, e.getMessage());
                    break;
                }
                List<RawExternalEvent> pageEvents = parse(body, city);
                result.addAll(pageEvents);
                if (pageEvents.size() < config.getPageSize()) {
                    break;
                }
                offset += config.getPageSize();
                sleep(config.getRequestDelayMs());
            }
        }
        log.info("PRO.Культура.РФ: fetched {} Pushkin card events", result.size());
        return result;
    }

    /** Parses one page ({@code {"events":[…]}}); each seance becomes its own event. */
    List<RawExternalEvent> parse(String body, String fallbackCity) {
        List<RawExternalEvent> out = new ArrayList<>();
        if (body == null || body.isBlank()) {
            return out;
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (Exception e) {
            log.warn("PRO.Культура.РФ: cannot parse response: {}", e.getMessage());
            return out;
        }
        JsonNode events = root.path("events");
        if (!events.isArray()) {
            events = root.path("data");
        }
        for (JsonNode event : events) {
            String id = event.path("_id").asText("");
            String title = event.path("name").asText("").trim();
            if (id.isEmpty() || title.isEmpty()) {
                continue;
            }
            JsonNode place = event.path("places").isArray() && !event.path("places").isEmpty()
                    ? event.path("places").get(0) : event.path("place");
            String city = firstText(place.path("locale").path("name"), place.path("address").path("locale"),
                    place.path("city"));
            if (city == null) {
                city = fallbackCity;
            }
            String imageUrl = firstText(event.path("image").path("name"), event.path("image").path("url"));
            String ticketUrl = firstText(event.path("saleLink"), event.path("tickets").path("url"),
                    event.path("externalInfo").path("url"));
            List<String> categories = new ArrayList<>();
            addText(categories, event.path("category").path("name"));
            for (JsonNode tag : event.path("tags")) {
                addText(categories, tag.path("name"));
            }
            String description = stripTags(event.path("shortDescription").asText(event.path("description").asText("")));

            List<LocalDateTime> starts = new ArrayList<>();
            for (JsonNode seance : event.path("seances")) {
                addStart(starts, seance.path("start"));
            }
            if (starts.isEmpty()) {
                addStart(starts, event.path("start"));
            }
            for (LocalDateTime start : starts) {
                out.add(RawExternalEvent.builder()
                        .externalId(id + "#" + start.toLocalDate())
                        .source(EventSource.CULTURE_RU)
                        .title(title)
                        .description(description)
                        .rawCategories(categories)
                        .imageUrl(imageUrl)
                        .rawPrice(event.path("isFree").asBoolean(false) ? "бесплатно"
                                : event.path("price").isNumber() ? "от " + event.path("price").asLong() + " руб." : null)
                        .eventDate(start.toLocalDate())
                        .eventTime(start.toLocalTime())
                        .ticketUrl(ticketUrl)
                        .city(city)
                        .pushkinCard(true)
                        .build());
            }
        }
        return out;
    }

    private static void addStart(List<LocalDateTime> starts, JsonNode millis) {
        if (millis.isNumber()) {
            starts.add(LocalDateTime.ofInstant(Instant.ofEpochMilli(millis.asLong()), ZONE));
        }
    }

    private static String firstText(JsonNode... nodes) {
        for (JsonNode node : nodes) {
            if (node.isTextual() && !node.asText().isBlank()) {
                return node.asText().trim();
            }
        }
        return null;
    }

    private static void addText(List<String> list, JsonNode node) {
        if (node.isTextual() && !node.asText().isBlank()) {
            list.add(node.asText().trim());
        }
    }

    private static String stripTags(String html) {
        return html == null ? null : html.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
