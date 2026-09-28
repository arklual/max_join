package com.join.back.web.controller;

import com.join.back.model.dto.MatchResponse;
import com.join.back.repository.UserRepository;
import com.join.back.service.ContactRequestService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Companions found for events. A match is not a chat yet: POST /request asks "пойдём вместе?",
 * the other side answers with /accept (the chat opens) or /decline.
 */
@RestController
@RequestMapping("/api/matches")
public class MatchController extends BaseAuthController {

    private final ContactRequestService contactRequestService;

    public MatchController(UserRepository userRepository, ContactRequestService contactRequestService) {
        super(userRepository);
        this.contactRequestService = contactRequestService;
    }

    // GET /api/matches — found companions and requests that are not a chat yet
    @GetMapping
    public ResponseEntity<List<MatchResponse>> getMatches() {
        return ResponseEntity.ok(contactRequestService.getPending(requireCurrentUserId()));
    }

    @PostMapping("/{id}/request")
    public ResponseEntity<MatchResponse> request(@PathVariable Long id) {
        return ResponseEntity.ok(contactRequestService.request(id, requireCurrentUserId()));
    }

    @PostMapping("/{id}/accept")
    public ResponseEntity<MatchResponse> accept(@PathVariable Long id) {
        return ResponseEntity.ok(contactRequestService.accept(id, requireCurrentUserId()));
    }

    @PostMapping("/{id}/decline")
    public ResponseEntity<MatchResponse> decline(@PathVariable Long id) {
        return ResponseEntity.ok(contactRequestService.decline(id, requireCurrentUserId()));
    }
}
