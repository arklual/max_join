package com.join.back.service;

import com.join.back.model.entity.User;
import com.join.back.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One-tap MAX linking: a signed-in user (Android app, Telegram, browser) gets a
 * short-lived one-time token, opens {@code max.ru/<bot>?start=link_<token>} and
 * presses "Start" — the MAX webhook then attaches that MAX account to theirs.
 * Starting the bot also enables MAX notifications for the user.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MaxLinkService {

    public static final String PAYLOAD_PREFIX = "link_";
    static final Duration TTL = Duration.ofMinutes(15);

    public enum Result { LINKED, ALREADY_LINKED, TAKEN_BY_ANOTHER, EXPIRED }

    public record Outcome(Result result, User user) { }

    private record Pending(Long userId, Instant expiresAt) { }

    private final UserRepository userRepository;
    private final MaxBotInfoService maxBotInfoService;
    private final Clock clock = Clock.systemUTC();
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();

    /** Issues a one-time token for the user and returns the MAX deep link. */
    public String createLinkUrl(Long userId) {
        purgeExpired();
        byte[] bytes = new byte[18];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        pending.put(token, new Pending(userId, clock.instant().plus(TTL)));
        return "https://max.ru/" + maxBotInfoService.getUsername() + "?start=" + PAYLOAD_PREFIX + token;
    }

    /** Consumes the token and attaches the MAX account to the token's owner. */
    @Transactional(transactionManager = "transactionManager")
    public Outcome link(String token, Long maxId) {
        Pending p = pending.remove(token);
        if (p == null || p.expiresAt().isBefore(clock.instant())) {
            return new Outcome(Result.EXPIRED, null);
        }
        Optional<User> owner = userRepository.findById(p.userId());
        if (owner.isEmpty()) {
            return new Outcome(Result.EXPIRED, null);
        }
        User user = owner.get();
        if (maxId.equals(user.getMaxId())) {
            return new Outcome(Result.ALREADY_LINKED, user);
        }
        boolean taken = userRepository.findByMaxId(maxId)
                .filter(other -> !other.getId().equals(user.getId()))
                .isPresent();
        if (taken || user.getMaxId() != null) {
            return new Outcome(Result.TAKEN_BY_ANOTHER, user);
        }
        user.setMaxId(maxId);
        userRepository.save(user);
        log.info("MAX {} linked to user {}", maxId, user.getId());
        return new Outcome(Result.LINKED, user);
    }

    private void purgeExpired() {
        Instant now = clock.instant();
        pending.values().removeIf(p -> p.expiresAt().isBefore(now));
    }
}
