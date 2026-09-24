package com.join.back.parser.service;

import com.join.back.model.entity.EventSource;
import com.join.back.model.entity.ParserRun;
import com.join.back.model.entity.ParserRunStatus;
import com.join.back.model.entity.TriggerType;
import com.join.back.parser.dto.RawExternalEvent;
import com.join.back.parser.provider.EventProvider;
import com.join.back.repository.ParserRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Orchestrates event parsing from all configured providers.
 * Runs automatically on schedule or manually via admin API.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventParserService {

    private final List<EventProvider> eventProviders;
    private final EventNormalizerService normalizerService;
    private final EventDeduplicationService deduplicationService;
    private final ContentModerationService contentModerationService;
    private final ParserRunRepository parserRunRepository;

    /**
     * Scheduled daily run at 03:00 MSK (00:00 UTC).
     */
    @Scheduled(cron = "${parser.schedule.cron:0 0 3 * * *}")
    public void scheduledRun() {
        log.info("Starting scheduled parser run");
        runAll(TriggerType.SCHEDULED);
    }

    /**
     * Manual run triggered via admin API.
     * Can optionally specify which sources to run.
     *
     * @param sources list of sources to run, or null/empty for all
     * @return list of created ParserRun records
     */
    public List<ParserRun> runManual(List<EventSource> sources) {
        log.info("Starting manual parser run for sources: {}", sources);

        List<EventProvider> providers;
        if (sources == null || sources.isEmpty()) {
            providers = eventProviders;
        } else {
            providers = eventProviders.stream()
                    .filter(p -> sources.contains(p.getSource()))
                    .toList();
        }

        return providers.stream()
                .map(provider -> runProvider(provider, TriggerType.MANUAL))
                .toList();
    }

    private void runAll(TriggerType triggerType) {
        for (EventProvider provider : eventProviders) {
            runProvider(provider, triggerType);
        }
    }

    private ParserRun runProvider(EventProvider provider, TriggerType triggerType) {
        EventSource source = provider.getSource();
        log.info("Starting parser run for source={} trigger={}", source, triggerType);

        ParserRun run = ParserRun.builder()
                .source(source)
                .startedAt(LocalDateTime.now())
                .status(ParserRunStatus.RUNNING)
                .triggerType(triggerType)
                .build();
        run = parserRunRepository.save(run);

        int found = 0, created = 0, updated = 0, skipped = 0;
        String errorMessage = null;
        ParserRunStatus finalStatus = ParserRunStatus.SUCCESS;

        try {
            List<RawExternalEvent> rawEvents = fetchWithRetry(provider);
            found = rawEvents.size();
            log.info("Fetched {} raw events from {}", found, source);

            for (RawExternalEvent raw : rawEvents) {
                try {
                    com.join.back.model.entity.Event event = normalizerService.normalize(raw);
                    if (event == null) {
                        skipped++;
                        continue;
                    }

                    if (contentModerationService.isExplicitContent(event.getTitle(), event.getDescription())) {
                        log.info("Skipping explicit event externalId={} title='{}' from {}",
                                raw.getExternalId(), event.getTitle(), source);
                        skipped++;
                        continue;
                    }

                    EventDeduplicationService.DeduplicationResult result =
                            deduplicationService.saveOrUpdate(event);

                    switch (result) {
                        case CREATED -> created++;
                        case UPDATED -> updated++;
                        case SKIPPED -> skipped++;
                    }
                } catch (Exception e) {
                    log.warn("Failed to process event externalId={} from {}: {}",
                            raw.getExternalId(), source, e.getMessage());
                    skipped++;
                }
            }

        } catch (Exception e) {
            log.error("Parser run failed for source={}: {}", source, e.getMessage(), e);
            finalStatus = ParserRunStatus.FAILED;
            errorMessage = e.getMessage();
        }

        // Update run record
        run.setFinishedAt(LocalDateTime.now());
        run.setStatus(finalStatus);
        run.setEventsFound(found);
        run.setEventsCreated(created);
        run.setEventsUpdated(updated);
        run.setEventsSkipped(skipped);
        run.setErrorMessage(errorMessage);
        run = parserRunRepository.save(run);

        log.info("Parser run completed for source={}: found={} created={} updated={} skipped={} status={}",
                source, found, created, updated, skipped, finalStatus);

        return run;
    }

    /**
     * Fetches events with retry logic (3 attempts with exponential backoff).
     */
    private List<RawExternalEvent> fetchWithRetry(EventProvider provider) {
        int maxAttempts = 3;
        long delayMs = 1000;
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return provider.fetchEvents();
            } catch (Exception e) {
                lastException = e;
                log.warn("Attempt {}/{} failed for source={}: {}",
                        attempt, maxAttempts, provider.getSource(), e.getMessage());
                if (attempt < maxAttempts) {
                    try {
                        Thread.sleep(delayMs);
                        delayMs *= 2;
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Parser interrupted", ie);
                    }
                }
            }
        }

        throw new RuntimeException("All " + maxAttempts + " attempts failed for source=" +
                provider.getSource(), lastException);
    }
}
