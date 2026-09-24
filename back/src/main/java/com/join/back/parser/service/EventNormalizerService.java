package com.join.back.parser.service;

import com.join.back.model.entity.Event;
import com.join.back.model.entity.EventSource;
import com.join.back.model.entity.EventStatus;
import com.join.back.model.entity.EventType;
import com.join.back.model.entity.Tag;
import com.join.back.parser.dto.RawExternalEvent;
import com.join.back.service.TagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Normalizes raw external events to internal Event entities.
 * Handles category mapping, price parsing, and validation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventNormalizerService {

    private static final Map<EventType, CanonicalTag> CANONICAL_TAGS = Map.of(
            EventType.CAREER, new CanonicalTag("Карьера", "career"),
            EventType.THEATER, new CanonicalTag("Театр", "theater"),
            EventType.ART, new CanonicalTag("Искусство", "art"),
            EventType.MUSIC, new CanonicalTag("Музыка", "music"),
            EventType.SPORT, new CanonicalTag("Спорт", "sport"),
            EventType.CINEMA, new CanonicalTag("Кино", "cinema"),
            EventType.MASTER_CLASS, new CanonicalTag("Мастер-класс", "master-class"),
            EventType.EXCURSION, new CanonicalTag("Экскурсия", "excursion"),
            EventType.FESTIVAL, new CanonicalTag("Фестиваль", "festival")
    );

    private final CategoryMapper categoryMapper;
    private final PriceParser priceParser;
    private final TagService tagService;

    /**
     * Converts a raw external event to an Event entity.
     * Returns null if the event fails validation.
     *
     * @param raw raw event from external source
     * @return normalized Event entity, or null if invalid
     */
    public Event normalize(RawExternalEvent raw) {
        // Validate required fields
        if (!isValid(raw)) {
            return null;
        }

        // Map category
        EventType type = mapCategory(raw);

        // Parse price
        java.math.BigDecimal price = null;
        if (raw.getRawPrice() != null) {
            price = priceParser.parsePrice(raw.getRawPrice());
        } else if (raw.getMinPrice() != null) {
            price = raw.getMinPrice();
        }

        // Determine status (sources like KUDAGO and TIMEPAD auto-approve)
        EventStatus status = determineStatus(raw);

        // Build tags from source categories
        Set<Tag> tags = buildTags(raw, type);

        return Event.builder()
                .title(raw.getTitle())
                .description(raw.getDescription())
                .type(type)
                .imageUrl(raw.getImageUrl())
                .price(price)
                .originalPrice(raw.getOriginalPrice())
                .studentPromoCode(raw.getStudentPromoCode())
                .studentPromoNote(raw.getStudentPromoNote())
                .eventDate(raw.getEventDate())
                .eventTime(raw.getEventTime())
                .ticketUrl(raw.getTicketUrl())
                .city(raw.getCity())
                .source(raw.getSource())
                .pushkinCard(raw.isPushkinCard())
                .externalId(raw.getExternalId())
                .status(status)
                .createdAt(LocalDateTime.now())
                .tags(tags)
                .build();
    }

    private boolean isValid(RawExternalEvent raw) {
        if (raw.getTitle() == null || raw.getTitle().isBlank()) {
            log.warn("Skipping event from {} with id={}: missing title",
                    raw.getSource(), raw.getExternalId());
            return false;
        }
        if (raw.getEventDate() == null) {
            log.warn("Skipping event from {} with id={}: missing date",
                    raw.getSource(), raw.getExternalId());
            return false;
        }
        // Allow events from yesterday (1-day tolerance)
        if (raw.getEventDate().isBefore(LocalDate.now().minusDays(1))) {
            log.warn("Skipping event from {} with id={}: event date {} is in the past",
                    raw.getSource(), raw.getExternalId(), raw.getEventDate());
            return false;
        }
        if (raw.getCity() == null || raw.getCity().isBlank()) {
            log.warn("Skipping event from {} with id={}: missing city",
                    raw.getSource(), raw.getExternalId());
            return false;
        }
        return true;
    }

    /**
     * Builds Tag entities from raw categories of the external event.
     * Creates tags that don't yet exist in the database.
     */
    private Set<Tag> buildTags(RawExternalEvent raw, EventType type) {
        List<String> categories = raw.getRawCategories();
        Set<Tag> tags = new HashSet<>();

        CanonicalTag canonicalTag = CANONICAL_TAGS.get(type);
        if (canonicalTag != null) {
            try {
                tags.add(tagService.findOrCreate(canonicalTag.name(), canonicalTag.slug()));
            } catch (Exception e) {
                log.warn("Failed to create canonical tag '{}': {}", canonicalTag.name(), e.getMessage());
            }
        }

        if (categories != null && !categories.isEmpty()) {
            for (String category : categories) {
                if (category != null && !category.isBlank()) {
                    try {
                        Tag tag = tagService.findOrCreate(category.trim());
                        tags.add(tag);
                    } catch (Exception e) {
                        log.warn("Failed to create tag for category '{}': {}", category, e.getMessage());
                    }
                }
            }
        }
        return tags;
    }

    private record CanonicalTag(String name, String slug) {
    }

    private EventType mapCategory(RawExternalEvent raw) {
        if (raw.getSource() == EventSource.KUDAGO) {
            return categoryMapper.mapKudaGoCategories(raw.getRawCategories());
        } else if (raw.getSource() == EventSource.TIMEPAD) {
            return categoryMapper.mapTimepadCategories(raw.getRawCategories());
        } else if (raw.getSource() == EventSource.TICKETTOSHOW) {
            return categoryMapper.mapTickettoshowCategories(raw.getRawCategories());
        } else if (raw.getSource() == EventSource.YANDEX_AFISHA) {
            // Rubric codes from the URL (concert, theater, tour, балет, …) share KudaGo's vocabulary.
            return categoryMapper.mapKudaGoCategories(raw.getRawCategories());
        } else if (raw.getSource() == EventSource.TELEGRAM) {
            List<String> categories = raw.getRawCategories();
            if (categories != null) {
                for (String c : categories) {
                    if (c == null) {
                        continue;
                    }
                    String lower = c.toLowerCase();
                    if (lower.contains("опера") || lower.contains("театр") || lower.contains("балет")) {
                        return EventType.THEATER;
                    }
                    if (lower.contains("конц") || lower.contains("музыка")) {
                        return EventType.MUSIC;
                    }
                }
            }
            return EventType.THEATER;
        }
        return EventType.FESTIVAL; // fallback
    }

    private EventStatus determineStatus(RawExternalEvent raw) {
        EventSource source = raw.getSource();
        return switch (source) {
            case KUDAGO, TIMEPAD, CULTURE_RU, YANDEX_AFISHA, TBANK_AFISHA, TICKETTOSHOW -> EventStatus.ACTIVE;
            case TELEGRAM -> raw.getStudentPromoCode() != null && !raw.getStudentPromoCode().isBlank()
                    ? EventStatus.ACTIVE
                    : EventStatus.NEEDS_REVIEW;
            case MANUAL -> EventStatus.ACTIVE;
        };
    }
}
