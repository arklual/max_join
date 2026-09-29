package com.join.back.web.controller;

import com.join.back.model.dto.UniversityResponse;
import com.join.back.model.entity.University;
import com.join.back.repository.UniversityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;

@SecurityRequirements
@RestController
@RequestMapping("/api/universities")
@RequiredArgsConstructor
public class UniversityController {

    private final UniversityRepository universityRepository;

    @GetMapping
    public ResponseEntity<List<UniversityResponse>> getUniversities(
            @RequestParam(required = false) String city
    ) {
        List<University> universities;
        if (city != null && !city.isBlank()) {
            universities = universityRepository.findByCityIgnoreCase(city.trim());
        } else {
            universities = universityRepository.findAll();
        }

        List<UniversityResponse> response = universities.stream()
                .map(u -> new UniversityResponse(u.getId(), u.getName(), u.getCity()))
                .toList();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UniversityResponse> getUniversity(@PathVariable Long id) {
        return universityRepository.findById(id)
                .map(u -> ResponseEntity.ok(new UniversityResponse(u.getId(), u.getName(), u.getCity())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
