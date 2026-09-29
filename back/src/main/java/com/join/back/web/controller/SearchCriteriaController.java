package com.join.back.web.controller;

import com.join.back.model.dto.SearchCriteriaRequest;
import com.join.back.model.dto.SearchCriteriaResponse;
import com.join.back.repository.UserRepository;
import com.join.back.service.SearchCriteriaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.join.back.config.ApiDocs;

@Tag(name = ApiDocs.PROFILE)
@RestController
@RequestMapping("/api/users/me/search-criteria")
public class SearchCriteriaController extends BaseAuthController {

    private final SearchCriteriaService searchCriteriaService;

    public SearchCriteriaController(UserRepository userRepository, SearchCriteriaService searchCriteriaService) {
        super(userRepository);
        this.searchCriteriaService = searchCriteriaService;
    }

    @Operation(summary = "Изменить настройки поиска компании",
            description = "Учитываются при следующих совпадениях, причём с обеих сторон.")
    @PutMapping
    public ResponseEntity<Void> updateSearchCriteria(@RequestBody SearchCriteriaRequest request) {
        Long userId = requireCurrentUserId();
        searchCriteriaService.updateSearchCriteria(userId, request);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Настройки поиска компании",
            description = "Пол, возраст и вуз напарника; пустое поле — без ограничения.")
    @GetMapping
    public ResponseEntity<SearchCriteriaResponse> getSearchCriteria() {
        Long userId = requireCurrentUserId();
        return ResponseEntity.ok(searchCriteriaService.getSearchCriteria(userId));
    }
}
