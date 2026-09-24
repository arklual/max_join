package com.join.back.parser.service;

import com.join.back.model.entity.Event;
import com.join.back.model.entity.EventSource;
import com.join.back.model.entity.EventStatus;
import com.join.back.model.entity.EventType;
import com.join.back.model.entity.Tag;
import com.join.back.repository.EventRepository;
import com.join.back.repository.TagRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.liquibase.enabled=false"
})
@Import(EventDeduplicationService.class)
class EventDeduplicationServiceTest {

    @Autowired
    private EventDeduplicationService deduplicationService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void shouldUpdateExistingExternalEventAndMergeTags() {
        jdbcTemplate.update("""
                DELETE FROM event_tags
                WHERE event_id IN (SELECT id FROM events WHERE source = 'TICKETTOSHOW' AND external_id = '3127')
                   OR tag_id IN (SELECT id FROM tags WHERE slug IN ('спектакль', 'партнерское'))
                """);
        jdbcTemplate.update("DELETE FROM events WHERE source = 'TICKETTOSHOW' AND external_id = '3127'");
        jdbcTemplate.update("DELETE FROM tags WHERE slug IN ('спектакль', 'партнерское')");

        Tag oldTag = tagRepository.save(Tag.builder()
                .name("Спектакль")
                .slug("спектакль")
                .build());
        Tag newTag = tagRepository.save(Tag.builder()
                .name("Партнерское")
                .slug("партнерское")
                .build());

        Event existing = eventRepository.save(Event.builder()
                .title("Old title")
                .description("Old description")
                .type(EventType.THEATER)
                .imageUrl("https://example.com/old.jpg")
                .price(BigDecimal.valueOf(1000))
                .eventDate(LocalDate.of(2026, 5, 14))
                .eventTime(LocalTime.of(19, 0))
                .ticketUrl("https://example.com/old")
                .city("Москва")
                .createdAt(LocalDateTime.now().minusDays(1))
                .source(EventSource.TICKETTOSHOW)
                .externalId("3127")
                .status(EventStatus.ACTIVE)
                .tags(Set.of(oldTag))
                .build());

        Event updated = Event.builder()
                .title("New title")
                .description("New description")
                .type(EventType.MUSIC)
                .imageUrl("https://example.com/new.jpg")
                .price(BigDecimal.valueOf(1500))
                .eventDate(LocalDate.of(2026, 5, 16))
                .eventTime(LocalTime.of(20, 0))
                .ticketUrl("https://tickettoshow.ru/concert?concert_id=3127")
                .city("Москва")
                .createdAt(LocalDateTime.now())
                .source(EventSource.TICKETTOSHOW)
                .externalId("3127")
                .status(EventStatus.ACTIVE)
                .tags(Set.of(newTag))
                .build();

        EventDeduplicationService.DeduplicationResult result = deduplicationService.saveOrUpdate(updated);

        assertEquals(EventDeduplicationService.DeduplicationResult.UPDATED, result);
        assertEquals(1, eventRepository.count());

        Event saved = eventRepository.findById(existing.getId()).orElseThrow();
        assertEquals("New title", saved.getTitle());
        assertEquals("New description", saved.getDescription());
        assertEquals("https://tickettoshow.ru/concert?concert_id=3127", saved.getTicketUrl());
        assertEquals(LocalDate.of(2026, 5, 16), saved.getEventDate());
        assertEquals(LocalTime.of(20, 0), saved.getEventTime());
        assertTrue(hasEventTag(saved.getId(), "спектакль"));
        assertTrue(hasEventTag(saved.getId(), "партнерское"));
    }

    private boolean hasEventTag(Long eventId, String tagSlug) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM event_tags et
                JOIN tags t ON t.id = et.tag_id
                WHERE et.event_id = ? AND t.slug = ?
                """, Integer.class, eventId, tagSlug);
        return count != null && count > 0;
    }
}
