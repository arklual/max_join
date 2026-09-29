package com.join.back.web.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.join.back.config.ApiDocs;
import com.join.back.config.ApiError;

/** Liveness for Docker/nginx: UP only when the database answers. */
@SecurityRequirements
@Tag(name = ApiDocs.SERVICE)
@RestController
@RequiredArgsConstructor
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    @Operation(summary = "Проверка работоспособности",
            description = "`UP`, если отвечает база данных. Этот же метод использует Docker healthcheck.")
    @ApiError(code = "503", description = "База данных недоступна")
    @GetMapping("/api/health")
    public ResponseEntity<Map<String, String>> healthCheck() {
        try {
            jdbcTemplate.queryForObject("select 1", Integer.class);
            return ResponseEntity.ok(Map.of("status", "UP", "db", "UP"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("status", "DOWN", "db", "DOWN"));
        }
    }
}
