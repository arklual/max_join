package com.join.back.web.controller;

import com.join.back.model.dto.CityResponse;
import com.join.back.service.CityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;

@SecurityRequirements
@RestController
@RequestMapping("/api/cities")
@RequiredArgsConstructor
public class CityController {

    private final CityService cityService;

    @GetMapping
    public ResponseEntity<List<CityResponse>> getCities(
            @RequestParam(value = "name", required = false) String name) {
        List<CityResponse> cities = cityService.searchCities(name);
        return ResponseEntity.ok(cities);
    }
}
