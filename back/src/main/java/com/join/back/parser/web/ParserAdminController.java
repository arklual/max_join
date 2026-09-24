package com.join.back.parser.web;

import com.join.back.model.entity.EventSource;
import com.join.back.model.entity.ParserRun;
import com.join.back.parser.service.EventParserService;
import com.join.back.repository.ParserRunRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Admin API for managing event parser.
 * All endpoints require ADMIN role (protected by SecurityConfig).
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/parser")
@RequiredArgsConstructor
public class ParserAdminController {

    private final EventParserService eventParserService;
    private final ParserRunRepository parserRunRepository;

    /**
     * Trigger manual parser run.
     * Body is optional: { "sources": ["KUDAGO", "TIMEPAD"] }
     * If no sources specified, all enabled providers are run.
     */
    @PostMapping("/run")
    public ResponseEntity<ParserRunResponse> runParser(
            @RequestBody(required = false) ParserRunRequest request) {
        log.info("Manual parser run triggered via admin API");

        List<EventSource> sources = (request != null) ? request.sources() : null;

        List<ParserRun> runs = eventParserService.runManual(sources);

        if (runs.isEmpty()) {
            return ResponseEntity.ok(new ParserRunResponse(
                    null,
                    "STARTED",
                    "No providers matched the requested sources",
                    List.of()
            ));
        }

        // Return summary
        List<Long> runIds = runs.stream().map(ParserRun::getId).toList();
        return ResponseEntity.ok(new ParserRunResponse(
                runs.get(0).getId(),
                "COMPLETED",
                "Parser run completed",
                runIds
        ));
    }

    /**
     * Get all parser runs ordered by start time descending.
     */
    @GetMapping("/runs")
    public List<ParserRunDto> getRuns() {
        return parserRunRepository.findAllByOrderByStartedAtDesc().stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Get details of a specific parser run.
     */
    @GetMapping("/runs/{id}")
    public ParserRunDto getRun(@PathVariable Long id) {
        return parserRunRepository.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> new EntityNotFoundException("Parser run not found: " + id));
    }

    private ParserRunDto toDto(ParserRun run) {
        return new ParserRunDto(
                run.getId(),
                run.getSource() != null ? run.getSource().name() : null,
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getStatus() != null ? run.getStatus().name() : null,
                run.getEventsFound(),
                run.getEventsCreated(),
                run.getEventsUpdated(),
                run.getEventsSkipped(),
                run.getErrorMessage(),
                run.getTriggerType() != null ? run.getTriggerType().name() : null
        );
    }

    public record ParserRunRequest(
            List<EventSource> sources
    ) {
    }

    public record ParserRunResponse(
            Long runId,
            String status,
            String message,
            List<Long> runIds
    ) {
    }

    public record ParserRunDto(
            Long id,
            String source,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            String status,
            int eventsFound,
            int eventsCreated,
            int eventsUpdated,
            int eventsSkipped,
            String errorMessage,
            String triggerType
    ) {
    }
}
