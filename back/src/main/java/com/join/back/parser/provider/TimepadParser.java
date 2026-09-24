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

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser for Timepad API events.
 * Fetches events from https://api.timepad.ru/v1
 * Requires: parser.sources.timepad.token = Bearer OAuth2 token
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TimepadParser implements EventProvider {

    private static final String FIELDS =
            "id,name,description_short,starts_at,ends_at,url,poster_image,location,min_price,max_price,categories";

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

        if (config.getToken() == null || config.getToken().isBlank()) {
            log.warn("Timepad token is not configured, skipping");
            return List.of();
        }

        List<RawExternalEvent> result = new ArrayList<>();

        RestClient restClient = RestClient.builder()
                .baseUrl(config.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + config.getToken())
                .build();

        String startsAtMin = java.time.LocalDate.now().atStartOfDay().toString() + "+03:00";
        int skip = 0;
        int pageCount = 0;

        log.info("Fetching Timepad events starting from {}", startsAtMin);

        while (pageCount < config.getMaxPages()) {
            final int finalSkip = skip;
            try {
                String responseBody = restClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/events")
                                .queryParam("limit", config.getLimit())
                                .queryParam("skip", finalSkip)
                                .queryParam("starts_at_min", startsAtMin)
                                .queryParam("fields", FIELDS)
                                .build())
                        .retrieve()
                        .body(String.class);

                JsonNode root = objectMapper.readTree(responseBody);
                JsonNode values = root.get("values");

                if (values == null || !values.isArray() || values.isEmpty()) {
                    log.debug("No more results from Timepad at skip={}", finalSkip);
                    break;
                }

                for (JsonNode eventNode : values) {
                    try {
                        RawExternalEvent raw = parseEventNode(eventNode);
                        if (raw != null) {
                            result.add(raw);
                        }
                    } catch (Exception e) {
                        log.warn("Failed to parse Timepad event: {}", e.getMessage());
                    }
                }

                skip += config.getLimit();
                pageCount++;

                // Check if we fetched less than limit (last page)
                if (values.size() < config.getLimit()) {
                    break;
                }

                // Rate limiting
                if (config.getRequestDelayMs() > 0) {
                    Thread.sleep(config.getRequestDelayMs());
                }

            } catch (RestClientException e) {
                log.error("Timepad API error at skip={}: {}", finalSkip, e.getMessage());
                break;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Timepad parser interrupted");
                return result;
            } catch (Exception e) {
                log.error("Unexpected error fetching Timepad events at skip={}: {}", finalSkip, e.getMessage());
                break;
            }
        }

        log.info("Total Timepad events fetched: {}", result.size());
        return result;
    }

    private RawExternalEvent parseEventNode(JsonNode node) {
        String externalId = getTextSafe(node, "id");
        String name = getTextSafe(node, "name");

        if (externalId == null || name == null || name.isBlank()) {
            log.warn("Skipping Timepad event with missing id or name");
            return null;
        }

        // Description
        String description = getTextSafe(node, "description_short");

        // Categories
        List<String> categories = new ArrayList<>();
        JsonNode categoriesNode = node.get("categories");
        if (categoriesNode != null && categoriesNode.isArray()) {
            for (JsonNode cat : categoriesNode) {
                String catName = getTextSafe(cat, "name");
                if (catName != null) {
                    categories.add(catName);
                }
            }
        }

        // Date/time from starts_at
        LocalDate eventDate = null;
        LocalTime eventTime = null;
        String startsAt = getTextSafe(node, "starts_at");
        if (startsAt != null) {
            try {
                OffsetDateTime odt = OffsetDateTime.parse(startsAt);
                eventDate = odt.toLocalDate();
                eventTime = odt.toLocalTime();
            } catch (DateTimeParseException e) {
                log.warn("Failed to parse Timepad event date: {}", startsAt);
            }
        }

        if (eventDate == null) {
            log.warn("Skipping Timepad event {} - no date", externalId);
            return null;
        }

        // Price
        String rawPrice = null;
        JsonNode minPriceNode = node.get("min_price");
        if (minPriceNode != null && !minPriceNode.isNull()) {
            rawPrice = minPriceNode.asText();
        }

        // Image
        String imageUrl = null;
        JsonNode posterImage = node.get("poster_image");
        if (posterImage != null && !posterImage.isNull()) {
            imageUrl = getTextSafe(posterImage, "default_url");
        }

        // City
        String city = null;
        JsonNode locationNode = node.get("location");
        if (locationNode != null && !locationNode.isNull()) {
            city = getTextSafe(locationNode, "city");
        }
        if (city != null && !city.isBlank()) {
            // Normalize city name - capitalize first letter
            city = city.substring(0, 1).toUpperCase() + city.substring(1).toLowerCase();
        }

        // Ticket URL
        String ticketUrl = getTextSafe(node, "url");

        if (city == null || city.isBlank()) {
            log.warn("Skipping Timepad event {} - no city", externalId);
            return null;
        }

        return RawExternalEvent.builder()
                .externalId(externalId)
                .source(EventSource.TIMEPAD)
                .title(name.length() > 255 ? name.substring(0, 255) : name)
                .description(description)
                .rawCategories(categories)
                .rawPrice(rawPrice)
                .eventDate(eventDate)
                .eventTime(eventTime)
                .imageUrl(imageUrl)
                .ticketUrl(ticketUrl)
                .city(city)
                .build();
    }

    private String getTextSafe(JsonNode node, String field) {
        JsonNode fieldNode = node.get(field);
        if (fieldNode == null || fieldNode.isNull()) {
            return null;
        }
        String value = fieldNode.asText().trim();
        return value.isEmpty() ? null : value;
    }
}
