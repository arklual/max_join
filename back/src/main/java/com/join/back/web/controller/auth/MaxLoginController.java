package com.join.back.web.controller.auth;

import com.join.back.model.dto.auth.AuthResponse;
import com.join.back.model.dto.auth.MaxRegisterRequest;
import com.join.back.service.MaxLoginService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;

/** "Войти через MAX" — public endpoints, the one-time token is the credential. */
@SecurityRequirements
@RestController
@RequestMapping("/api/auth/max-login")
@RequiredArgsConstructor
public class MaxLoginController {

    private final MaxLoginService maxLoginService;

    @PostMapping
    public MaxLoginService.Started start() {
        return maxLoginService.start();
    }

    @GetMapping("/{token}")
    public Map<String, Object> poll(@PathVariable String token) {
        MaxLoginService.PollResult result = maxLoginService.poll(token);
        Map<String, Object> body = new HashMap<>();
        body.put("status", result.status());
        if (result.auth() != null) {
            body.put("token", result.auth().token());
        }
        return body;
    }

    @PostMapping("/{token}/register")
    public AuthResponse register(@PathVariable String token, @Valid @RequestBody MaxRegisterRequest request) {
        return maxLoginService.register(token, request);
    }
}
