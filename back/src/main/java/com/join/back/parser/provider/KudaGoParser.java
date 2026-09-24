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
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser for KudaGo API events.
 * Fetches events from https://kudago.com/public-api/v1.4
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KudaGoParser implements EventProvider {

    private static final ZoneId MOSCOW_TZ = ZoneId.of("Europe/Moscow");
    private static final String FIELDS = "id,title,description,categories,dates,price,images,location,place,site_url";

    private static final java.util.Map<String, String> CITY_SLUG_MAP = java.util.Map.ofEntries(
            java.util.Map.entry("msk", "Москва"),
            java.util.Map.entry("spb", "Санкт-Петербург"),
            java.util.Map.entry("nsk", "Новосибирск"),
            java.util.Map.entry("ekb", "Екатеринбург"),
            java.util.Map.entry("nnv", "Нижний Новгород"),
            java.util.Map.entry("kzn", "Казань"),
            java.util.Map.entry("smr", "Самара"),
            java.util.Map.entry("krd", "Краснодар"),
            java.util.Map.entry("sochi", "Сочи"),
            java.util.Map.entry("ufa", "Уфа"),
            java.util.Map.entry("krasnoyarsk", "Красноярск")
    );

    private final ParserProperties parserProperties;
    private final ObjectMapper objectMapper;

    @Override
    public EventSource getSource() {
        return EventSource.KUDAGO;
    }

    @Override
    public List<RawExternalEvent> fetchEvents() {
        ParserProperties.KudaGoConfig config = parserProperties.getSources().getKudago();

        if (!config.isEnabled()) {
            log.info("KudaGo parser is disabled, skipping");
            return List.of();
        }

        List<RawExternalEvent> result = new ArrayList<>();
        long actualSince = Instant.now().getEpochSecond();

        RestClient restClient = RestClient.builder()
                .baseUrl(config.getBaseUrl())
                .build();

        for (String location : config.getLocations()) {
            log.info("Fetching KudaGo events for location: {}", location);
            int page = 1;
            int totalFetched = 0;

            while (page <= config.getMaxPages()) {
                try {
                    final int currentPage = page;
                    String responseBody = restClient.get()
                            .uri(uriBuilder -> uriBuilder
                                    .path("/events/")
                                    .queryParam("fields", FIELDS)
                                    .queryParam("location", location)
                                    .queryParam("actual_since", actualSince)
                                    .queryParam("page_size", config.getPageSize())
                                    .queryParam("page", currentPage)
                                    .queryParam("text_format", "plain")
                                    .queryParam("expand", "place")
                                    .build())
                            .retrieve()
                            .body(String.class);

                    JsonNode root = objectMapper.readTree(responseBody);
                    JsonNode results = root.get("results");

                    if (results == null || !results.isArray() || results.isEmpty()) {
                        log.debug("No more results for location {} page {}", location, page);
                        break;
                    }

                    for (JsonNode eventNode : results) {
                        try {
                            RawExternalEvent raw = parseEventNode(eventNode, location);
                            if (raw != null) {
                                result.add(raw);
                                totalFetched++;
                            }
                        } catch (Exception e) {
                            log.warn("Failed to parse KudaGo event node: {}", e.getMessage());
                        }
                    }

                    // Check if there are more pages
                    JsonNode nextNode = root.get("next");
                    if (nextNode == null || nextNode.isNull()) {
                        break;
                    }

                    page++;

                    // Rate limiting
                    if (config.getRequestDelayMs() > 0) {
                        Thread.sleep(config.getRequestDelayMs());
                    }

                } catch (RestClientException e) {
                    log.error("KudaGo API error for location {} page {}: {}", location, page, e.getMessage());
                    break;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("KudaGo parser interrupted");
                    return result;
                } catch (Exception e) {
                    log.error("Unexpected error fetching KudaGo events for location {} page {}: {}", location, page, e.getMessage());
                    break;
                }
            }

            log.info("Fetched {} events from KudaGo for location {}", totalFetched, location);
        }

        log.info("Total KudaGo events fetched: {}", result.size());
        return result;
    }

    private RawExternalEvent parseEventNode(JsonNode node, String locationSlug) {
        String externalId = getTextSafe(node, "id");
        String title = getTextSafe(node, "title");

        if (externalId == null || title == null || title.isBlank()) {
            log.warn("Skipping KudaGo event with missing id or title");
            return null;
        }

        // Description
        String description = getTextSafe(node, "description");

        // Categories
        List<String> categories = new ArrayList<>();
        JsonNode categoriesNode = node.get("categories");
        if (categoriesNode != null && categoriesNode.isArray()) {
            for (JsonNode cat : categoriesNode) {
                categories.add(cat.asText());
            }
        }

        // Date/time from dates[0].start
        LocalDate eventDate = null;
        LocalTime eventTime = null;
        JsonNode datesNode = node.get("dates");
        if (datesNode != null && datesNode.isArray() && !datesNode.isEmpty()) {
            JsonNode firstDate = datesNode.get(0);
            JsonNode startNode = firstDate.get("start");
            if (startNode != null && !startNode.isNull()) {
                long startTs = startNode.asLong();
                ZonedDateTime zdt = Instant.ofEpochSecond(startTs).atZone(MOSCOW_TZ);
                eventDate = zdt.toLocalDate();
                eventTime = zdt.toLocalTime();
            }
        }

        if (eventDate == null) {
            log.warn("Skipping KudaGo event {} - no date", externalId);
            return null;
        }

        // Price
        String rawPrice = getTextSafe(node, "price");

        // Image
        String imageUrl = null;
        JsonNode imagesNode = node.get("images");
        if (imagesNode != null && imagesNode.isArray() && !imagesNode.isEmpty()) {
            JsonNode firstImage = imagesNode.get(0);
            if (firstImage != null) {
                imageUrl = getTextSafe(firstImage, "image");
            }
        }

        // Location - use the slug passed in (more reliable)
        String city = CITY_SLUG_MAP.getOrDefault(locationSlug, locationSlug);

        // Ticket URL
        String ticketUrl = getTextSafe(node, "site_url");

        return RawExternalEvent.builder()
                .externalId(externalId)
                .source(EventSource.KUDAGO)
                .title(title.length() > 255 ? title.substring(0, 255) : title)
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
