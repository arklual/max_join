package com.join.back.parser.service;

import com.join.back.model.entity.Event;
import com.join.back.model.entity.EventSource;
import com.join.back.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Handles event deduplication.
 * Strategy: deduplicate by source + external_id (unique constraint in DB).
 * If event already exists - update it; if not - create new — unless another source already has
 * the same event (same city, date and title): then that card is enriched instead of showing a twin.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventDeduplicationService {

    private final EventRepository eventRepository;

    /**
     * Saves or updates an event based on source + externalId deduplication.
     *
     * @param event new or updated event
     * @return DeduplicationResult indicating what action was taken
     */
    @Transactional
    public DeduplicationResult saveOrUpdate(Event event) {
        if (event.getSource() == null || event.getSource() == EventSource.MANUAL
                || event.getExternalId() == null) {
            eventRepository.save(event);
            return DeduplicationResult.CREATED;
        }

        Optional<Event> existingOpt = eventRepository.findBySourceAndExternalId(
                event.getSource(), event.getExternalId());

        if (existingOpt.isPresent()) {
            Event existing = existingOpt.get();
            updateFields(existing, event);
            eventRepository.save(existing);
            log.debug("Updated existing event source={} externalId={}",
                    event.getSource(), event.getExternalId());
            return DeduplicationResult.UPDATED;
        }

        Optional<Event> twin = findTwinFromOtherSource(event);
        if (twin.isPresent()) {
            enrichTwin(twin.get(), event);
            log.debug("Merged event source={} externalId={} into {} #{}",
                    event.getSource(), event.getExternalId(), twin.get().getSource(), twin.get().getId());
            return DeduplicationResult.SKIPPED;
        }

        eventRepository.save(event);
        log.debug("Created new event source={} externalId={}",
                event.getSource(), event.getExternalId());
        return DeduplicationResult.CREATED;
    }

    /** The same event already loaded from another source: same city, date and normalized title. */
    private Optional<Event> findTwinFromOtherSource(Event event) {
        if (event.getCity() == null || event.getEventDate() == null || event.getTitle() == null) {
            return Optional.empty();
        }
        String key = titleKey(event.getTitle());
        if (key.isEmpty()) {
            return Optional.empty();
        }
        return eventRepository.findByCityAndEventDate(event.getCity(), event.getEventDate()).stream()
                .filter(other -> other.getSource() != event.getSource())
                .filter(other -> key.equals(titleKey(other.getTitle())))
                .findFirst();
    }

    /** Keeps the first card, adding what the second source knows better. */
    private void enrichTwin(Event existing, Event duplicate) {
        boolean changed = false;
        if (duplicate.isPushkinCard() && !existing.isPushkinCard()) {
            existing.setPushkinCard(true);
            changed = true;
        }
        if (existing.getPrice() == null && duplicate.getPrice() != null) {
            existing.setPrice(duplicate.getPrice());
            changed = true;
        }
        if (existing.getEventTime() == null && duplicate.getEventTime() != null) {
            existing.setEventTime(duplicate.getEventTime());
            changed = true;
        }
        if (existing.getImageUrl() == null && duplicate.getImageUrl() != null) {
            existing.setImageUrl(duplicate.getImageUrl());
            changed = true;
        }
        if (changed) {
            existing.setUpdatedAt(LocalDateTime.now());
            eventRepository.save(existing);
        }
    }

    /** "Концерт «Мельница» (16+)" and "КОНЦЕРТ МЕЛЬНИЦА 16+" → "концерт мельница". */
    static String titleKey(String title) {
        return title.toLowerCase(java.util.Locale.ROOT)
                .replace('ё', 'е')
                .replaceAll("\\b\\d{1,2}\\s*\\+", " ")
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim();
    }

    private void updateFields(Event existing, Event updated) {
        existing.setTitle(updated.getTitle());
        existing.setDescription(updated.getDescription());
        existing.setImageUrl(updated.getImageUrl());
        existing.setPrice(updated.getPrice());
        existing.setEventDate(updated.getEventDate());
        existing.setEventTime(updated.getEventTime());
        existing.setTicketUrl(updated.getTicketUrl());
        existing.setCity(updated.getCity());
        existing.setOriginalPrice(updated.getOriginalPrice());
        existing.setStudentPromoCode(updated.getStudentPromoCode());
        existing.setStudentPromoNote(updated.getStudentPromoNote());
        // Only a Pushkin-card feed knows about the card; regular feeds must not clear it.
        if (updated.isPushkinCard()) {
            existing.setPushkinCard(true);
        }
        existing.setUpdatedAt(LocalDateTime.now());
        // Merge new tags into existing set (don't remove old ones)
        if (updated.getTags() != null && !updated.getTags().isEmpty()) {
            existing.getTags().addAll(updated.getTags());
        }
    }

    public enum DeduplicationResult {
        CREATED,
        UPDATED,
        SKIPPED
    }
}
