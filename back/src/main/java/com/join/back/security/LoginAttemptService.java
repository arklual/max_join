package com.join.back.security;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Slows down password guessing on /auth/login: after {@value #MAX_FAILURES} failed attempts
 * for the same email or from the same address within {@link #WINDOW}, further attempts are
 * rejected until the window passes. State is in memory — enough for a single instance.
 */
@Service
public class LoginAttemptService {

    static final int MAX_FAILURES = 10;
    static final Duration WINDOW = Duration.ofMinutes(15);

    private final Clock clock;
    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    public LoginAttemptService(Clock clock) {
        this.clock = clock;
    }

    /** True when either key (email, ip) has hit the limit. */
    public boolean isBlocked(String email, String ip) {
        return count(keyFor("email", email)) >= MAX_FAILURES || count(keyFor("ip", ip)) >= MAX_FAILURES;
    }

    public void recordFailure(String email, String ip) {
        record(keyFor("email", email));
        record(keyFor("ip", ip));
    }

    public void recordSuccess(String email, String ip) {
        failures.remove(keyFor("email", email));
        failures.remove(keyFor("ip", ip));
    }

    private void record(String key) {
        Deque<Instant> deque = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (deque) {
            prune(deque);
            deque.addLast(clock.instant());
        }
    }

    private int count(String key) {
        Deque<Instant> deque = failures.get(key);
        if (deque == null) {
            return 0;
        }
        synchronized (deque) {
            prune(deque);
            return deque.size();
        }
    }

    private void prune(Deque<Instant> deque) {
        Instant cutoff = clock.instant().minus(WINDOW);
        while (!deque.isEmpty() && deque.peekFirst().isBefore(cutoff)) {
            deque.pollFirst();
        }
    }

    private static String keyFor(String kind, String value) {
        return kind + ":" + (value == null ? "" : value.trim().toLowerCase());
    }
}
