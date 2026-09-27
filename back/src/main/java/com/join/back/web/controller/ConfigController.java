package com.join.back.web.controller;

import com.join.back.service.MaxBotInfoService;
import com.join.back.service.TelegramBotInfoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Public read-only configuration that the client app needs at boot time —
 * notably the MAX bot username, used to build {@code max.ru/<bot>?startapp=...}
 * deep links for QR codes and friend-group invites.
 */
@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
public class ConfigController {

    private final MaxBotInfoService maxBotInfoService;
    private final TelegramBotInfoService telegramBotInfoService;
    private final com.join.back.service.CityScope cityScope;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getConfig() {
        Map<String, Object> body = new HashMap<>();
        body.put("maxBotUsername", maxBotInfoService.getUsername());
        body.put("telegramBotUsername", telegramBotInfoService.getUsername());
        body.put("cities", cityScope.servedCities());
        return ResponseEntity.ok(body);
    }
}
