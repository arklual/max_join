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
 * If event already exists - update it; if not - create new.
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
        } else {
            eventRepository.save(event);
            log.debug("Created new event source={} externalId={}",
                    event.getSource(), event.getExternalId());
            return DeduplicationResult.CREATED;
        }
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
