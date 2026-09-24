package com.join.back.web.controller.auth;

import com.join.back.model.dto.auth.AuthResponse;
import com.join.back.model.dto.auth.LinkEmailRequest;
import com.join.back.model.dto.auth.LinkMaxRequest;
import com.join.back.model.dto.auth.LoginRequest;
import com.join.back.model.dto.auth.RegisterRequest;
import com.join.back.repository.UserRepository;
import com.join.back.service.AuthService;
import com.join.back.web.controller.BaseAuthController;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.join.back.service.MaxLinkService;

@Slf4j
@RestController
@RequestMapping("/api/auth")
public class AuthController extends BaseAuthController {

    private final AuthService authService;
    private final MaxLinkService maxLinkService;

    public AuthController(UserRepository userRepository, AuthService authService, MaxLinkService maxLinkService) {
        super(userRepository);
        this.authService = authService;
        this.maxLinkService = maxLinkService;
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

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
    @PostMapping("/link-max")
    public AuthResponse linkMax(@Valid @RequestBody LinkMaxRequest request) {
        return authService.linkMax(getCurrentMaxId(), request);
    }

    /**
     * One-tap MAX linking for a signed-in user: returns a one-time
     * {@code max.ru/<bot>?start=link_<token>} deep link (valid 15 minutes).
     */
    @PostMapping("/max-link")
    public java.util.Map<String, String> createMaxLink() {
        return java.util.Map.of("url", maxLinkService.createLinkUrl(requireCurrentUserId()));
    }

    /** Same as link-max, for whichever messenger mini app (MAX or Telegram) the request comes from. */
    @PostMapping("/link-messenger")
    public AuthResponse linkMessenger(@Valid @RequestBody LinkMaxRequest request) {
        var identity = getCurrentMessengerIdentity();
        return authService.linkMessenger(identity.getMessenger(), identity.getExternalId(), request);
    }
}
