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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parser for Tickettoshow events.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TickettoshowParser implements EventProvider {

    private static final DateTimeFormatter TICKETTOSHOW_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss");
    private static final String USER_AGENT = "insomnia/12.2.0";
    private static final String TICKET_BASE_URL = "https://tickettoshow.ru/concert";
    private static final List<String> KNOWN_CITIES = List.of(
            "Москва",
            "Санкт-Петербург",
            "Новосибирск",
            "Екатеринбург",
            "Казань",
            "Нижний Новгород",
            "Самара",
            "Краснодар",
            "Сочи",
            "Уфа",
            "Красноярск"
    );

    private final ParserProperties parserProperties;
    private final ObjectMapper objectMapper;

    @Override
    public EventSource getSource() {
        return EventSource.TICKETTOSHOW;
    }

    @Override
    public List<RawExternalEvent> fetchEvents() {
        ParserProperties.TickettoshowConfig config = parserProperties.getSources().getTickettoshow();

        if (!config.isEnabled()) {
            log.info("Tickettoshow parser is disabled, skipping");
            return List.of();
        }

        RestClient restClient = RestClient.builder()
                .baseUrl(config.getBaseUrl())
                .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
                .build();

        List<TickettoshowListEvent> listEvents;
        try {
            String responseBody = restClient.get()
                    .uri("/p_list")
                    .retrieve()
                    .body(String.class);
            listEvents = parseEventsFromListResponse(responseBody);
        } catch (RestClientException e) {
            log.error("Tickettoshow list API error: {}", e.getMessage());
            return List.of();
        } catch (Exception e) {
            log.error("Failed to parse Tickettoshow list response: {}", e.getMessage());
            return List.of();
        }

        List<RawExternalEvent> result = new ArrayList<>();
        for (TickettoshowListEvent listEvent : listEvents) {
            try {
                String detailsBody = restClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/card_p")
                                .queryParam("performance_id", listEvent.performanceId())
                                .build())
                        .retrieve()
                        .body(String.class);
                RawExternalEvent raw = toRawExternalEvent(listEvent, detailsBody);
                if (raw != null) {
                    result.add(raw);
                }

                if (config.getRequestDelayMs() > 0) {
                    Thread.sleep(config.getRequestDelayMs());
                }
            } catch (RestClientException e) {
                log.warn("Tickettoshow detail API error for performance_id={}: {}",
                        listEvent.performanceId(), e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Tickettoshow parser interrupted");
                return result;
            } catch (Exception e) {
                log.warn("Failed to parse Tickettoshow detail for performance_id={}: {}",
                        listEvent.performanceId(), e.getMessage());
            }
        }

        log.info("Total Tickettoshow events fetched: {}", result.size());
        return result;
    }

    List<TickettoshowListEvent> parseEventsFromListResponse(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode data = root.get("data");
        if (data == null || !data.isObject()) {
            return List.of();
        }

        Map<String, TickettoshowListEvent> eventsById = new LinkedHashMap<>();
        data.fields().forEachRemaining(categoryEntry -> {
            String category = categoryEntry.getKey();
            JsonNode categoryItems = categoryEntry.getValue();
            if (!categoryItems.isArray()) {
                return;
            }

            for (JsonNode item : categoryItems) {
                String performanceId = getTextSafe(item, "performance_id");
                String title = getTextSafe(item, "show_name");
                if (performanceId == null || title == null) {
                    continue;
                }

                TickettoshowListEvent existing = eventsById.get(performanceId);
                if (existing != null) {
                    existing.categories().add(category);
                    continue;
                }

                eventsById.put(performanceId, new TickettoshowListEvent(
                        performanceId,
                        title,
                        getTextSafe(item, "theatre_name"),
                        getTextSafe(item, "address"),
                        getTextSafe(item, "img"),
                        getTextSafe(item, "date_time"),
                        getBigDecimalSafe(item, "min_price"),
                        new ArrayList<>(List.of(category))
                ));
            }
        });

        return new ArrayList<>(eventsById.values());
    }

    RawExternalEvent toRawExternalEvent(TickettoshowListEvent listEvent, String detailsBody) throws Exception {
        JsonNode root = objectMapper.readTree(detailsBody);
        JsonNode data = root.get("data");
        if (data == null || !data.isObject()) {
            return null;
        }

        String performanceId = getTextSafe(data, "performance_id");
        if (performanceId == null) {
            performanceId = listEvent.performanceId();
        }

        String title = firstNonBlank(getTextSafe(data, "show_name"), listEvent.title());
        String dateTime = firstDateTime(data);
        if (dateTime == null) {
            dateTime = listEvent.dateTime();
        }

        LocalDateTime startsAt = parseDateTime(dateTime);
        if (startsAt == null) {
            log.warn("Skipping Tickettoshow event {} - no valid date", performanceId);
            return null;
        }

        String address = firstNonBlank(getTextSafe(data, "theatre_address"), listEvent.address());
        String theatreName = firstNonBlank(getTextSafe(data, "theatre_name"), listEvent.theatreName());
        String hallName = getTextSafe(data, "hall_name");
        BigDecimal minPrice = firstNonNull(
                getBigDecimalSafe(data, "min_price"),
                firstNonNull(firstDatePrice(data), listEvent.minPrice())
        );

        return RawExternalEvent.builder()
                .externalId(performanceId)
                .source(EventSource.TICKETTOSHOW)
                .title(truncate(title, 255))
                .description(buildDescription(getTextSafe(data, "show_description"), theatreName, hallName, address))
                .rawCategories(buildCategories(listEvent, getTextSafe(data, "type_name")))
                .imageUrl(normalizeImageUrl(firstNonBlank(getTextSafe(data, "img"), listEvent.imageUrl())))
                .minPrice(minPrice)
                .eventDate(startsAt.toLocalDate())
                .eventTime(startsAt.toLocalTime())
                .ticketUrl(buildTicketUrl(performanceId))
                .city(resolveCity(address))
                .build();
    }

    private List<String> buildCategories(TickettoshowListEvent listEvent, String detailType) {
        List<String> categories = new ArrayList<>(listEvent.categories());
        if (detailType != null && !detailType.isBlank()) {
            categories.add(detailType);
        }
        return categories;
    }

    private String firstDateTime(JsonNode data) {
        JsonNode selected = selectedDateTimeItem(data);
        if (selected != null) {
            return getTextSafe(selected, "begin_time");
        }
        JsonNode dateTime = data.get("date_time");
        if (dateTime != null && dateTime.isTextual()) {
            return textOrNull(dateTime.asText());
        }
        return null;
    }

    private BigDecimal firstDatePrice(JsonNode data) {
        JsonNode selected = selectedDateTimeItem(data);
        return selected == null ? null : getBigDecimalSafe(selected, "min_price");
    }

    private JsonNode selectedDateTimeItem(JsonNode data) {
        JsonNode dateTime = data.get("date_time");
        if (dateTime == null || dateTime.isNull()) {
            return null;
        }
        if (dateTime.isArray() && !dateTime.isEmpty()) {
            JsonNode selected = null;
            for (JsonNode item : dateTime) {
                if ("active".equalsIgnoreCase(getTextSafe(item, "performance_status"))) {
                    selected = item;
                    break;
                }
            }
            if (selected == null) {
                selected = dateTime.get(0);
            }
            return selected;
        }
        return null;
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, TICKETTOSHOW_DATE_TIME);
        } catch (DateTimeParseException e) {
            log.warn("Failed to parse Tickettoshow date_time: {}", value);
            return null;
        }
    }

    private String buildDescription(String showDescription, String theatreName, String hallName, String address) {
        List<String> parts = new ArrayList<>();
        if (showDescription != null && !showDescription.isBlank()) {
            parts.add(showDescription.trim());
        }

        List<String> placeParts = new ArrayList<>();
        if (theatreName != null && !theatreName.isBlank()) {
            placeParts.add(theatreName.trim());
        }
        if (hallName != null && !hallName.isBlank()) {
            placeParts.add(hallName.trim());
        }
        if (address != null && !address.isBlank()) {
            placeParts.add(address.trim());
        }
        if (!placeParts.isEmpty()) {
            parts.add("Место: " + String.join(", ", placeParts));
        }

        return parts.isEmpty() ? null : String.join("\n\n", parts);
    }

    private String normalizeImageUrl(String imageUrl) {
        if (imageUrl == null) {
            return null;
        }
        if (imageUrl.startsWith("http://") || imageUrl.startsWith("https://")) {
            return imageUrl;
        }
        return "https://" + imageUrl;
    }

    private String buildTicketUrl(String performanceId) {
        ParserProperties.TickettoshowConfig config = parserProperties.getSources().getTickettoshow();
        String url = TICKET_BASE_URL + "?concert_id=" + performanceId;
        if (config.getRefCode() == null || config.getRefCode().isBlank()) {
            return appendUtm(url, config.getUtmQuery());
        }
        return appendUtm(url + "&ref=" + config.getRefCode().trim(), config.getUtmQuery());
    }

    private String appendUtm(String url, String utmQuery) {
        if (utmQuery == null || utmQuery.isBlank()) {
            return url;
        }
        String normalizedUtm = utmQuery.trim();
        if (normalizedUtm.startsWith("?") || normalizedUtm.startsWith("&")) {
            normalizedUtm = normalizedUtm.substring(1);
        }
        if (normalizedUtm.isBlank()) {
            return url;
        }
        return url + "&" + normalizedUtm;
    }

    private String resolveCity(String address) {
        ParserProperties.TickettoshowConfig config = parserProperties.getSources().getTickettoshow();
        if (address != null) {
            String lowerAddress = address.toLowerCase();
            for (String city : KNOWN_CITIES) {
                if (lowerAddress.contains(city.toLowerCase())) {
                    return city;
                }
            }
        }
        if (config.getDefaultCity() == null || config.getDefaultCity().isBlank()) {
            return "Москва";
        }
        return config.getDefaultCity();
    }

    private String getTextSafe(JsonNode node, String field) {
        JsonNode fieldNode = node.get(field);
        if (fieldNode == null || fieldNode.isNull()) {
            return null;
        }
        return textOrNull(fieldNode.asText());
    }

    private String textOrNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private BigDecimal getBigDecimalSafe(JsonNode node, String field) {
        JsonNode fieldNode = node.get(field);
        if (fieldNode == null || fieldNode.isNull()) {
            return null;
        }
        if (!fieldNode.isNumber() && !fieldNode.isTextual()) {
            return null;
        }
        String value = fieldNode.asText().trim();
        if (value.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }

    private BigDecimal firstNonNull(BigDecimal first, BigDecimal second) {
        return first != null ? first : second;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    record TickettoshowListEvent(
            String performanceId,
            String title,
            String theatreName,
            String address,
            String imageUrl,
            String dateTime,
            BigDecimal minPrice,
            List<String> categories
    ) {
    }
}
