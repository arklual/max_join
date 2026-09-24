package com.join.back.web.controller;

import com.join.back.service.ModerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin API for content moderation operations.
 * All endpoints are protected by ADMIN role (via SecurityConfig matcher for /api/admin/**).
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/moderation")
@RequiredArgsConstructor
public class ModerationController {

    private final ModerationService moderationService;

    /**
     * Scans all events in the database and marks explicit/adult-content events as hidden.
     *
     * <pre>POST /api/admin/moderation/cleanup</pre>
     *
     * @return cleanup statistics
     */
    @PostMapping("/cleanup")
    public ResponseEntity<CleanupResponse> cleanup() {
        log.info("Content moderation cleanup triggered via admin API");
        ModerationService.CleanupResult result = moderationService.cleanupExplicitEvents();
        return ResponseEntity.ok(new CleanupResponse(
                result.totalScanned(),
                result.hiddenNow(),
                result.alreadyHidden(),
                "Cleanup completed successfully"
        ));
    }

    public record CleanupResponse(
            int totalScanned,
            int hiddenNow,
            int alreadyHidden,
            String message
    ) {
    }
}
