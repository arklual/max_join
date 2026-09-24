package com.join.back.parser.service;

import com.join.back.model.entity.EventType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Maps external category slugs/names to internal EventType.
 * Mapping is defined according to the parser specification §4.4.
 */
@Component
public class CategoryMapper {

    private static final Map<String, EventType> KUDAGO_MAPPING = Map.ofEntries(
            Map.entry("concert", EventType.MUSIC),
            Map.entry("party", EventType.MUSIC),
            Map.entry("theater", EventType.THEATER),
            Map.entry("exhibition", EventType.ART),
            Map.entry("photo", EventType.ART),
            Map.entry("cinema", EventType.CINEMA),
            Map.entry("festival", EventType.FESTIVAL),
            Map.entry("holiday", EventType.FESTIVAL),
            Map.entry("yarmarki-razvlecheniya-yarmarki", EventType.FESTIVAL),
            Map.entry("education", EventType.MASTER_CLASS),
            Map.entry("quest", EventType.MASTER_CLASS),
            Map.entry("tour", EventType.EXCURSION),
            Map.entry("recreation", EventType.EXCURSION),
            Map.entry("business-events", EventType.CAREER),
            Map.entry("entertainment", EventType.SPORT)
    );

    private static final Map<String, EventType> TIMEPAD_MAPPING = Map.ofEntries(
            Map.entry("концерты", EventType.MUSIC),
            Map.entry("музыка", EventType.MUSIC),
            Map.entry("вечеринки", EventType.MUSIC),
            Map.entry("театр", EventType.THEATER),
            Map.entry("спектакли", EventType.THEATER),
            Map.entry("балет", EventType.THEATER),
            Map.entry("опера", EventType.THEATER),
            Map.entry("выставки", EventType.ART),
            Map.entry("искусство", EventType.ART),
            Map.entry("кино", EventType.CINEMA),
            Map.entry("фильмы", EventType.CINEMA),
            Map.entry("фестивали", EventType.FESTIVAL),
            Map.entry("мастер-класс", EventType.MASTER_CLASS),
            Map.entry("тренинг", EventType.MASTER_CLASS),
            Map.entry("воркшоп", EventType.MASTER_CLASS),
            Map.entry("экскурсии", EventType.EXCURSION),
            Map.entry("бизнес", EventType.CAREER),
            Map.entry("нетворкинг", EventType.CAREER),
            Map.entry("конференция", EventType.CAREER),
            Map.entry("карьера", EventType.CAREER),
            Map.entry("спорт", EventType.SPORT),
            Map.entry("марафон", EventType.SPORT),
            Map.entry("забег", EventType.SPORT)
    );

    private static final Map<String, EventType> TICKETTOSHOW_MAPPING = Map.ofEntries(
            Map.entry("концерты", EventType.MUSIC),
            Map.entry("концерт", EventType.MUSIC),
            Map.entry("классика", EventType.MUSIC),
            Map.entry("спектакли", EventType.THEATER),
            Map.entry("спектакль", EventType.THEATER),
            Map.entry("театр", EventType.THEATER),
            Map.entry("театры", EventType.THEATER),
            Map.entry("детям", EventType.FESTIVAL)
    );

    private static final EventType FALLBACK = EventType.FESTIVAL;

    /**
     * Maps a list of KudaGo category slugs to EventType.
     * Picks the first non-fallback category. If all map to fallback, returns fallback.
     */
    public EventType mapKudaGoCategories(List<String> categories) {
        if (categories == null || categories.isEmpty()) {
            return FALLBACK;
        }

        EventType firstMatch = null;
        for (String category : categories) {
            EventType mapped = KUDAGO_MAPPING.getOrDefault(
                    category.toLowerCase().trim(), FALLBACK);
            if (mapped != FALLBACK) {
                return mapped;
            }
            if (firstMatch == null) {
                firstMatch = mapped;
            }
        }

        return firstMatch != null ? firstMatch : FALLBACK;
    }

    /**
     * Maps a list of Timepad category names to EventType.
     * Picks the first non-fallback category. If all map to fallback, returns fallback.
     */
    public EventType mapTimepadCategories(List<String> categories) {
        if (categories == null || categories.isEmpty()) {
            return FALLBACK;
        }

        EventType firstMatch = null;
        for (String category : categories) {
            EventType mapped = TIMEPAD_MAPPING.getOrDefault(
                    category.toLowerCase().trim(), FALLBACK);
            if (mapped != FALLBACK) {
                return mapped;
            }
            if (firstMatch == null) {
                firstMatch = mapped;
            }
        }

        return firstMatch != null ? firstMatch : FALLBACK;
    }

    /**
     * Maps Tickettoshow category names to EventType.
     */
    public EventType mapTickettoshowCategories(List<String> categories) {
        if (categories == null || categories.isEmpty()) {
            return FALLBACK;
        }

        EventType firstMatch = null;
        for (String category : categories) {
            EventType mapped = TICKETTOSHOW_MAPPING.getOrDefault(
                    category.toLowerCase().trim(), FALLBACK);
            if (mapped != FALLBACK) {
                return mapped;
            }
            if (firstMatch == null) {
                firstMatch = mapped;
            }
        }

        return firstMatch != null ? firstMatch : FALLBACK;
    }
}
