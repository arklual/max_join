package com.join.back.service;

import com.join.back.model.dto.auth.AuthResponse;
import com.join.back.model.dto.auth.MaxRegisterRequest;
import com.join.back.model.entity.User;
import com.join.back.repository.UserRepository;
import com.join.back.security.AuthException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "Войти через MAX" for the Android app and browsers: the client starts a login,
 * opens {@code max.ru/<bot>?start=login_<token>}, the user presses Start and the
 * MAX webhook confirms the token with their MAX id. The client polls the token and
 * receives a JWT — or, for a MAX user without a JOIN profile, registers one.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MaxLoginService {

    public static final String PAYLOAD_PREFIX = "login_";
    static final Duration TTL = Duration.ofMinutes(10);

    public enum Status { PENDING, SUCCESS, NEEDS_REGISTRATION, EXPIRED }

    /** Poll result: {@code auth} is set only for SUCCESS (consumed once). */
    public record PollResult(Status status, AuthResponse auth) { }

    public record Started(String token, String url) { }

    private static final class Pending {
        final Instant expiresAt;
        volatile Long maxId;
        volatile Long userId;

        Pending(Instant expiresAt) {
            this.expiresAt = expiresAt;
        }
    }

    private final UserRepository userRepository;
    private final AuthService authService;
    private final MaxBotInfoService maxBotInfoService;
    private final Clock clock = Clock.systemUTC();
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();

    public Started start() {
        purgeExpired();
        byte[] bytes = new byte[18];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        pending.put(token, new Pending(clock.instant().plus(TTL)));
        return new Started(token, "https://max.ru/" + maxBotInfoService.getUsername() + "?start=" + PAYLOAD_PREFIX + token);
    }

    /**
     * Called from the MAX webhook when the user presses Start.
     * @return the existing JOIN user for that MAX account (empty → needs registration),
     *         or {@code null} if the token is unknown / expired.
     */
    public Optional<User> confirm(String token, Long maxId) {
        Pending p = live(token);
        if (p == null) return null;
        p.maxId = maxId;
        Optional<User> user = userRepository.findByMaxId(maxId);
        user.ifPresent(u -> p.userId = u.getId());
        log.info("MAX login confirmed for MAX user {} (existing account: {})", maxId, user.isPresent());
        return user;
    }

    public PollResult poll(String token) {
        Pending p = live(token);
        if (p == null) return new PollResult(Status.EXPIRED, null);
        if (p.maxId == null) return new PollResult(Status.PENDING, null);
        if (p.userId == null) return new PollResult(Status.NEEDS_REGISTRATION, null);
        pending.remove(token);
        User user = userRepository.findById(p.userId).orElse(null);
        if (user == null) return new PollResult(Status.EXPIRED, null);
        return new PollResult(Status.SUCCESS, authService.issueFor(user));
    }

    /** Creates the JOIN profile for a confirmed MAX user that had none. */
    public AuthResponse register(String token, MaxRegisterRequest request) {
        Pending p = live(token);
        if (p == null || p.maxId == null) {
            throw new AuthException(HttpStatus.GONE, "LOGIN_EXPIRED", "Вход устарел — нажмите «Войти через MAX» ещё раз");
        }
        AuthResponse auth = authService.registerWithMax(p.maxId, request);
        pending.remove(token);
        return auth;
    }

    private Pending live(String token) {
        Pending p = token == null ? null : pending.get(token);
        if (p == null) return null;
        if (p.expiresAt.isBefore(clock.instant())) {
            pending.remove(token);
            return null;
        }
        return p;
    }

    private void purgeExpired() {
        Instant now = clock.instant();
        pending.values().removeIf(p -> p.expiresAt.isBefore(now));
    }
}
