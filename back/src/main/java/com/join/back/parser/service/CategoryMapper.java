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
            Map.entry("театры", EventType.THEATER),
            Map.entry("искусство и культура", EventType.ART),
            Map.entry("экскурсии и путешествия", EventType.EXCURSION),
            Map.entry("хобби и творчество", EventType.MASTER_CLASS),
            Map.entry("наука", EventType.MASTER_CLASS),
            Map.entry("интеллектуальные игры", EventType.MASTER_CLASS),
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

    /** Kassir.ru category names (see KassirParser.CATEGORY_NAMES). */
    private static final Map<String, EventType> KASSIR_MAPPING = Map.of(
            "Концерт", EventType.MUSIC,
            "Театр", EventType.THEATER,
            "Шоу", EventType.THEATER,
            "Стендап", EventType.THEATER,
            "Фестивали", EventType.FESTIVAL,
            "Спорт", EventType.SPORT,
            "Выставки", EventType.ART,
            "Экскурсии", EventType.EXCURSION);

    /** PRO.Культура.РФ category and tag names. */
    private static final Map<String, EventType> CULTURE_RU_MAPPING = Map.ofEntries(
            Map.entry("концерты", EventType.MUSIC),
            Map.entry("концерт", EventType.MUSIC),
            Map.entry("музыка", EventType.MUSIC),
            Map.entry("спектакли", EventType.THEATER),
            Map.entry("спектакль", EventType.THEATER),
            Map.entry("театр", EventType.THEATER),
            Map.entry("опера", EventType.THEATER),
            Map.entry("балет", EventType.THEATER),
            Map.entry("выставки", EventType.ART),
            Map.entry("выставка", EventType.ART),
            Map.entry("музеи", EventType.ART),
            Map.entry("кино", EventType.CINEMA),
            Map.entry("кинопоказы", EventType.CINEMA),
            Map.entry("экскурсии", EventType.EXCURSION),
            Map.entry("экскурсия", EventType.EXCURSION),
            Map.entry("мастер-классы", EventType.MASTER_CLASS),
            Map.entry("мастер-класс", EventType.MASTER_CLASS),
            Map.entry("лекции", EventType.MASTER_CLASS),
            Map.entry("фестивали", EventType.FESTIVAL),
            Map.entry("праздники", EventType.FESTIVAL)
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

    /** Maps a Kassir.ru category name (first raw category) to EventType. */
    public EventType mapKassirCategory(List<String> categories) {
        if (categories == null || categories.isEmpty()) {
            return FALLBACK;
        }
        return KASSIR_MAPPING.getOrDefault(categories.get(0), FALLBACK);
    }

    /** Maps PRO.Культура.РФ category/tag names to EventType (first known one wins). */
    public EventType mapCultureRuCategories(List<String> categories) {
        if (categories == null) {
            return FALLBACK;
        }
        for (String category : categories) {
            if (category == null) continue;
            EventType mapped = CULTURE_RU_MAPPING.get(category.toLowerCase().trim());
            if (mapped != null) {
                return mapped;
            }
        }
        return FALLBACK;
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
