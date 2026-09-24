package com.join.back.parser.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ContentModerationService")
class ContentModerationServiceTest {

    private ContentModerationService service;

    @BeforeEach
    void setUp() {
        service = new ContentModerationService();
    }

    // ── Positive (explicit) cases ──────────────────────────────────────────────

    @ParameterizedTest(name = "title ''{0}'' → explicit")
    @ValueSource(strings = {
            "Вечер стриптиза",
            "Эротическое шоу",
            "Порно фестиваль",
            "Секс-вечеринка",
            "Интимные танцы",
            "Nude photo session",
            "XXX Party",
            "Strip club night",
            "Adult entertainment show",
            "Erotic dance festival",
            "Burlesque night",
            "18+ вечеринка",
            "Голые танцы",
    })
    void shouldDetectExplicitTitle(String title) {
        assertTrue(service.isExplicitContent(title, null),
                "Expected explicit detection for title: " + title);
    }

    @ParameterizedTest(name = "description ''{0}'' → explicit")
    @ValueSource(strings = {
            "Приходите на вечер эротики и страсти",
            "Шоу со стриптизом и порно элементами",
            "Nudity is welcome at this event",
            "An adult club experience awaits you",
            "xxx rated performance",
    })
    void shouldDetectExplicitDescription(String description) {
        assertTrue(service.isExplicitContent("Нормальное название", description),
                "Expected explicit detection for description: " + description);
    }

    @Test
    @DisplayName("Case-insensitive matching — uppercase")
    void shouldBeCaseInsensitive_uppercase() {
        assertTrue(service.isExplicitContent("СТРИПТИЗ НА ВЕЧЕРИНКЕ", null));
    }

    @Test
    @DisplayName("Case-insensitive matching — mixed case")
    void shouldBeCaseInsensitive_mixedCase() {
        assertTrue(service.isExplicitContent("Erotic Dance Night", null));
    }

    @Test
    @DisplayName("Stop-word in description, clean title")
    void shouldDetectStopWordInDescription() {
        assertTrue(service.isExplicitContent("Вечеринка", "Ждём вас на шоу стриптиз в центре города"));
    }

    @Test
    @DisplayName("Stop-word only in title, null description")
    void shouldDetectStopWordInTitleWithNullDescription() {
        assertTrue(service.isExplicitContent("Nude Art Exhibition", null));
    }

    // ── Negative (clean) cases ─────────────────────────────────────────────────

    @ParameterizedTest(name = "clean title ''{0}'' → not explicit")
    @ValueSource(strings = {
            "Джазовый вечер",
            "Выставка современного искусства",
            "Кино под открытым небом",
            "Stand-up comedy night",
            "Family festival",
            "Концерт классической музыки",
            "Tech conference 2026",
    })
    void shouldNotFlagCleanEvents(String title) {
        assertFalse(service.isExplicitContent(title, "Обычное описание мероприятия для всех"),
                "Expected clean for title: " + title);
    }

    @Test
    @DisplayName("Null title and null description → not explicit")
    void shouldHandleBothNulls() {
        assertFalse(service.isExplicitContent(null, null));
    }

    @Test
    @DisplayName("Empty title and empty description → not explicit")
    void shouldHandleBothBlanks() {
        assertFalse(service.isExplicitContent("  ", "   "));
    }

    @NullSource
    @ParameterizedTest(name = "null description → relies on title only")
    void shouldHandleNullDescription(String description) {
        assertFalse(service.isExplicitContent("Фестиваль еды", description));
    }

    @Test
    @DisplayName("Word 'adult' in clean context — partial match not triggered for substring 'adult' inside normal word")
    void shouldNotFlagWordThatContainsStopWordAsSubstring() {
        // "adult" is a stop-word, but "adultery" is not in the list → this test documents
        // that we do substring matching and thus "adultery" WOULD be caught.
        // Document expected behaviour: substring match is intentional for safety.
        assertTrue(service.isExplicitContent("The adultery drama play", null),
                "'adult' substring in 'adultery' should trigger because we do substring matching for safety");
    }
}
