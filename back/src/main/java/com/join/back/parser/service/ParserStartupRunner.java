package com.join.back.parser.service;

import com.join.back.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Fills an empty database on the first start so the afisha is never blank:
 * the scheduled run happens at night, and a fresh local deployment would show
 * nothing until then. Runs in the background, the app starts serving right away.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ParserStartupRunner implements ApplicationRunner {

    private final EventRepository eventRepository;
    private final EventParserService eventParserService;

    @Value("${parser.run-on-startup-if-empty:true}")
    private boolean runIfEmpty;

    @Override
    public void run(ApplicationArguments args) {
        if (!runIfEmpty) {
            return;
        }
        try {
            if (eventRepository.count() > 0) {
                return;
            }
        } catch (Exception e) {
            log.warn("Could not check the events table on startup: {}", e.getMessage());
            return;
        }
        log.info("No events in the database — starting the initial parser run in the background");
        Thread thread = new Thread(() -> {
            try {
                eventParserService.runManual(null);
                log.info("Initial parser run finished: {} events", eventRepository.count());
            } catch (Exception e) {
                log.error("Initial parser run failed: {}", e.getMessage(), e);
            }
        }, "parser-initial-run");
        thread.setDaemon(true);
        thread.start();
    }
}
