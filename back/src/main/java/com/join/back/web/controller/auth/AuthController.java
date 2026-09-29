package com.join.back.web.controller.auth;

import com.join.back.model.dto.auth.AuthResponse;
import com.join.back.model.dto.auth.LinkEmailRequest;
import com.join.back.model.dto.auth.LinkMaxRequest;
import com.join.back.model.dto.auth.LoginRequest;
import com.join.back.model.dto.auth.RegisterRequest;
import com.join.back.repository.UserRepository;
import com.join.back.service.AuthService;
import com.join.back.web.controller.BaseAuthController;
import com.join.back.security.AuthException;
import com.join.back.security.LoginAttemptService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.join.back.service.MaxLinkService;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.join.back.config.ApiDocs;
import com.join.back.config.ApiError;

@Slf4j
@Tag(name = ApiDocs.AUTH)
@RestController
@RequestMapping("/api/auth")
public class AuthController extends BaseAuthController {

    private final AuthService authService;
    private final MaxLinkService maxLinkService;
    private final LoginAttemptService loginAttemptService;

    public AuthController(UserRepository userRepository, AuthService authService, MaxLinkService maxLinkService,
                          LoginAttemptService loginAttemptService) {
        super(userRepository);
        this.authService = authService;
        this.maxLinkService = maxLinkService;
        this.loginAttemptService = loginAttemptService;
    }

    @SecurityRequirements
    @ApiError(code = "409", description = "Email уже используется (code: EMAIL_TAKEN)")
    @Operation(summary = "Регистрация по email",
            description = "Аккаунт с паролем — для сайта и Android-приложения. Возраст — от 14 лет, "
                    + "`personalDataConsent` должен быть `true`. Возвращает токен и профиль.")
    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @SecurityRequirements
    @ApiError(code = "401", description = "Неверный email или пароль (code: BAD_CREDENTIALS)")
    @ApiError(code = "429", description = "Слишком много неудачных попыток, вход заблокирован на 15 минут")
    @Operation(summary = "Вход по email и паролю",
            description = "Возвращает токен для заголовка `Authorization: Bearer <token>`. После 10 неудачных "
                    + "попыток вход для email или адреса блокируется на 15 минут.")
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        String ip = clientIp(http);
        if (loginAttemptService.isBlocked(request.email(), ip)) {
            throw AuthException.tooManyAttempts();
        }
        try {
            AuthResponse response = authService.login(request);
            loginAttemptService.recordSuccess(request.email(), ip);
            return response;
        } catch (AuthException e) {
            loginAttemptService.recordFailure(request.email(), ip);
            throw e;
        }
    }

    /** nginx passes the real client address in X-Forwarded-For. */
    private static String clientIp(HttpServletRequest http) {
        String forwarded = http.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return http.getRemoteAddr();
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Добавить email и пароль к аккаунту",
            description = "Пользователь MAX задаёт email и пароль, чтобы входить в тот же аккаунт на сайте и в "
                    + "Android-приложении.")
    @ApiError(code = "409", description = "Email уже используется или уже привязан")
    @PostMapping("/link-email")
    public ResponseEntity<Void> linkEmail(@Valid @RequestBody LinkEmailRequest request) {
        authService.linkEmail(requireCurrentUserId(), request);
        return ResponseEntity.noContent().build();
    }

    /**
     * Called from the Mini App when MAX auth resolves to no JOIN account:
     * lets the user sign in with the credentials of the account they already
     * created in the Android app instead of registering a duplicate.
     */
    @Operation(summary = "Войти в существующий аккаунт из MAX",
            description = "Если пользователь MAX ещё не связан с JOIN, но уже зарегистрирован по email, — "
                    + "привязывает MAX к этому аккаунту вместо создания дубля.")
    @ApiError(code = "401", description = "Неверный email или пароль")
    @ApiError(code = "409", description = "Аккаунт уже привязан к другому аккаунту MAX или Telegram")
    @PostMapping("/link-max")
    public AuthResponse linkMax(@Valid @RequestBody LinkMaxRequest request) {
        return authService.linkMax(getCurrentMaxId(), request);
    }

    /**
     * One-tap MAX linking for a signed-in user: returns a one-time
     * {@code max.ru/<bot>?start=link_<token>} deep link (valid 15 minutes).
     */
    @Operation(summary = "Ссылка для привязки MAX",
            description = "Одноразовая ссылка `max.ru/<бот>?start=link_<token>` на 15 минут: после «Начать» в боте "
                    + "MAX привязывается к текущему аккаунту.")
    @PostMapping("/max-link")
    public java.util.Map<String, String> createMaxLink() {
        return java.util.Map.of("url", maxLinkService.createLinkUrl(requireCurrentUserId()));
    }

    /** Same as link-max, for whichever messenger mini app (MAX or Telegram) the request comes from. */
    @Operation(summary = "Войти в существующий аккаунт из мессенджера",
            description = "То же, что `link-max`, для мини-приложения MAX или Telegram — мессенджер определяется по "
                    + "заголовку запроса.")
    @ApiError(code = "401", description = "Неверный email или пароль")
    @ApiError(code = "409", description = "Аккаунт уже привязан к другому аккаунту MAX или Telegram")
    @PostMapping("/link-messenger")
    public AuthResponse linkMessenger(@Valid @RequestBody LinkMaxRequest request) {
        var identity = getCurrentMessengerIdentity();
        return authService.linkMessenger(identity.getMessenger(), identity.getExternalId(), request);
    }
}
