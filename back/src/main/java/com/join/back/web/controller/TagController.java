package com.join.back.web.controller;

import com.join.back.model.dto.TagResponse;
import com.join.back.service.TagService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
public class TagController {

    private static final int DEFAULT_POPULAR_LIMIT = 10;

    private final TagService tagService;

    /**
     * Search tags by query string (matches slug, case-insensitive).
     * If query is absent or blank, returns popular tags.
     *
     * GET /api/tags?query=music
     */
    @GetMapping
    public ResponseEntity<List<TagResponse>> searchTags(
            @RequestParam(required = false) String query
    ) {
        return ResponseEntity.ok(tagService.searchTags(query));
    }

    /**
     * Returns top-10 tags by number of associated events.
     *
     * GET /api/tags/popular
     */
    @GetMapping("/popular")
    public ResponseEntity<List<TagResponse>> getPopularTags(
            @RequestParam(defaultValue = "10") int limit
    ) {
        return ResponseEntity.ok(tagService.getPopularTags(Math.min(limit, DEFAULT_POPULAR_LIMIT)));
    }
}
