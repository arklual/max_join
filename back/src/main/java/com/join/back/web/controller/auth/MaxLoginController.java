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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.join.back.config.ApiDocs;
import com.join.back.config.ApiError;

/** "Войти через MAX" — public endpoints, the one-time token is the credential. */
@SecurityRequirements
@Tag(name = ApiDocs.AUTH)
@RestController
@RequestMapping("/api/auth/max-login")
@RequiredArgsConstructor
public class MaxLoginController {

    private final MaxLoginService maxLoginService;

    @Operation(summary = "Вход через MAX: начать",
            description = "Создаёт одноразовый токен на 10 минут и ссылку на бота `/start login_<token>`; на "
                    + "компьютере сайт показывает её QR-кодом.")
    @PostMapping
    public MaxLoginService.Started start() {
        return maxLoginService.start();
    }

    @Operation(summary = "Вход через MAX: статус",
            description = "Сайт опрашивает, пока пользователь не нажмёт «Начать» в боте. `status`: `PENDING` — "
                    + "ждём, `SUCCESS` — вход выполнен (в ответе `token`), `NEEDS_REGISTRATION` — нужен "
                    + "профиль, `EXPIRED` — токен истёк.")
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

    @Operation(summary = "Вход через MAX: регистрация",
            description = "Создаёт профиль для аккаунта MAX без пароля, если при входе пришёл `NEEDS_REGISTRATION`. "
                    + "Возраст — от 14 лет, нужно согласие на обработку данных.")
    @ApiError(code = "409", description = "Этот аккаунт MAX уже зарегистрирован")
    @PostMapping("/{token}/register")
    public AuthResponse register(@PathVariable String token, @Valid @RequestBody MaxRegisterRequest request) {
        return maxLoginService.register(token, request);
    }
}
