package com.join.back.service;

import com.join.back.model.entity.Event;
import com.join.back.parser.service.ContentModerationService;
import com.join.back.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service responsible for content moderation of existing events stored in the database.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModerationService {

    private final EventRepository eventRepository;
    private final ContentModerationService contentModerationService;

    /**
     * Scans all events in the database and hides those that contain explicit content.
     *
     * @return {@link CleanupResult} with statistics of the operation
     */
    @Transactional
    public CleanupResult cleanupExplicitEvents() {
        List<Event> allEvents = eventRepository.findAll();
        log.info("Content moderation cleanup started — total events: {}", allEvents.size());

        int hidden = 0;
        int alreadyHidden = 0;

        for (Event event : allEvents) {
            if (event.isHidden()) {
                alreadyHidden++;
                continue;
            }
            if (contentModerationService.isExplicitContent(event.getTitle(), event.getDescription())) {
                event.setHidden(true);
                eventRepository.save(event);
                log.info("Hidden explicit event id={} title='{}'", event.getId(), event.getTitle());
                hidden++;
            }
        }

        log.info("Content moderation cleanup finished — hidden: {} alreadyHidden: {} total: {}",
                hidden, alreadyHidden, allEvents.size());

        return new CleanupResult(allEvents.size(), hidden, alreadyHidden);
    }

    /**
     * Result of a moderation cleanup run.
     *
     * @param totalScanned    total number of events scanned
     * @param hiddenNow       events that were hidden during this run
     * @param alreadyHidden   events that were already hidden before this run
     */
    public record CleanupResult(int totalScanned, int hiddenNow, int alreadyHidden) {
    }
}
