package com.join.back.web.controller;

import com.join.back.model.dto.MatchResponse;
import com.join.back.repository.UserRepository;
import com.join.back.service.MatchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/matches")
public class MatchController extends BaseAuthController {

    private final MatchService matchService;

    public MatchController(UserRepository userRepository, MatchService matchService) {
        super(userRepository);
        this.matchService = matchService;
    }

    @GetMapping
    public ResponseEntity<List<MatchResponse>> getMatches() {
        Long userId = requireCurrentUserId();
        return ResponseEntity.ok(matchService.getMatches(userId));
    }
}
