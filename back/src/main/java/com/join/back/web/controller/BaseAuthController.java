package com.join.back.web.controller;

import com.join.back.model.entity.User;
import com.join.back.repository.UserRepository;
import com.join.back.security.MaxAuthenticationToken;
import com.join.back.security.MessengerAuthenticationToken;
import com.join.back.security.UserIdAuthenticationToken;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Base class for controllers that require MAX authentication.
 * Provides helpers to extract current user's maxId and userId from the security context.
 */
@RequiredArgsConstructor
public abstract class BaseAuthController {

    protected final UserRepository userRepository;

    /**
     * Returns the MAX user ID of the authenticated user.
     * Throws {@link IllegalStateException} if the context doesn't hold a MAX token.
     */
    protected Long getCurrentMaxId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof MaxAuthenticationToken maxAuth) {
            return maxAuth.getMaxId();
        }
        throw new IllegalStateException("User is not authenticated via MAX");
    }

    /**
     * Returns the messenger identity (MAX or Telegram) of the caller.
     * Throws {@link IllegalStateException} if the request isn't from a messenger mini app.
     */
    protected MessengerAuthenticationToken getCurrentMessengerIdentity() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof MessengerAuthenticationToken messengerAuth) {
            return messengerAuth;
        }
        throw new IllegalStateException("User is not authenticated via a messenger mini app");
    }

    private java.util.Optional<User> findByMessenger(MessengerAuthenticationToken auth) {
        return switch (auth.getMessenger()) {
            case MAX -> userRepository.findByMaxId(auth.getExternalId());
            case TELEGRAM -> userRepository.findByTelegramId(auth.getExternalId());
        };
    }

    /**
     * Returns the database user ID of the authenticated user.
     * Supports both {@link UserIdAuthenticationToken} (JWT) and {@link MaxAuthenticationToken}.
     * Throws {@link EntityNotFoundException} if the user doesn't exist.
     */
    protected Long requireCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof UserIdAuthenticationToken jwt) {
            return jwt.getUserId();
        }
        if (auth instanceof MessengerAuthenticationToken messengerAuth) {
            return findByMessenger(messengerAuth)
                    .orElseThrow(() -> new EntityNotFoundException("User not found with "
                            + messengerAuth.getMessenger() + " id: " + messengerAuth.getExternalId()))
                    .getId();
        }
        throw new IllegalStateException("User is not authenticated");
    }

    /**
     * Returns the database user ID of the authenticated user, or {@code null} if not found.
     * Supports both {@link UserIdAuthenticationToken} (JWT) and {@link MaxAuthenticationToken}.
     * Use this for optional auth scenarios (e.g., public endpoints that enrich response for logged-in users).
     */
    protected Long getCurrentUserIdOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof UserIdAuthenticationToken jwt) {
            return jwt.getUserId();
        }
        if (auth instanceof MessengerAuthenticationToken messengerAuth) {
            return findByMessenger(messengerAuth)
                    .map(User::getId)
                    .orElse(null);
        }
        return null;
    }
}
