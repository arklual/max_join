package com.join.back.web.controller;

import com.join.back.model.dto.EventCardResponse;
import com.join.back.repository.UserRepository;
import com.join.back.service.LikeService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
public class LikeController extends BaseAuthController {

    private final LikeService likeService;

    public LikeController(UserRepository userRepository, LikeService likeService) {
        super(userRepository);
        this.likeService = likeService;
    }

    @PostMapping("/{id}/like")
    public ResponseEntity<Void> likeEvent(@PathVariable Long id) {
        Long userId = requireCurrentUserId();
        likeService.like(userId, id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/like")
    public ResponseEntity<Void> unlikeEvent(@PathVariable Long id) {
        Long userId = requireCurrentUserId();
        likeService.unlike(userId, id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/liked")
    public ResponseEntity<Page<EventCardResponse>> getLikedEvents(Pageable pageable) {
        Long userId = requireCurrentUserId();
        return ResponseEntity.ok(likeService.getLikedEvents(userId, pageable));
    }
}
