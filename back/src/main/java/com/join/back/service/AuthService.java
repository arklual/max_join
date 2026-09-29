package com.join.back.service;

import com.join.back.model.dto.UserResponse;
import com.join.back.model.dto.auth.AuthResponse;
import com.join.back.model.dto.auth.LinkEmailRequest;
import com.join.back.model.dto.auth.LinkMaxRequest;
import com.join.back.model.dto.auth.MaxRegisterRequest;
import com.join.back.security.Messenger;
import com.join.back.model.dto.auth.LoginRequest;
import com.join.back.model.dto.auth.RegisterRequest;
import com.join.back.model.entity.EventType;
import com.join.back.model.entity.Gender;
import com.join.back.model.entity.User;
import com.join.back.repository.UniversityRepository;
import com.join.back.repository.UserRepository;
import com.join.back.security.AuthException;
import com.join.back.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final UniversityRepository universityRepository;
    private final UserMapper userMapper;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    @Transactional(transactionManager = "transactionManager")
    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.email())) {
            throw AuthException.emailTaken();
        }

        User user = buildProfileUser(req.firstName(), req.age(), req.gender(), req.city(),
                req.universityId(), req.interests());
        user.setEmail(req.email());
        user.setPasswordHash(passwordEncoder.encode(req.password()));

        User saved = userRepository.save(user);
        String token = jwtService.issue(saved.getId());
        UserResponse u = userMapper.toResponse(saved);
        return new AuthResponse(token, u);
    }

    @Transactional(transactionManager = "transactionManager")
    public AuthResponse login(LoginRequest req) {
        User user = userRepository.findByEmail(req.email())
                .orElseThrow(AuthException::badCredentials);
        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw AuthException.badCredentials();
        }
        String token = jwtService.issue(user.getId());
        return new AuthResponse(token, userMapper.toResponse(user));
    }

    /**
     * Attaches the caller's MAX identity to an existing account that was
     * created with email + password (typically on the native Android client),
     * so the very same account works in the Mini App and in the app at once.
     *
     * Idempotent: re-linking the same maxId simply re-issues a token.
     */
    /**
     * Creates an account for a MAX user who signed in from the Android app or a
     * browser via "Войти через MAX" and has no JOIN profile yet — no password needed.
     */
    @Transactional(transactionManager = "transactionManager")
    public AuthResponse registerWithMax(Long maxId, MaxRegisterRequest req) {
        if (userRepository.findByMaxId(maxId).isPresent()) {
            throw new AuthException(HttpStatus.CONFLICT, "MAX_TAKEN", "Этот аккаунт MAX уже зарегистрирован в JOIN — просто войдите");
        }
        User user = buildProfileUser(req.firstName(), req.age(), req.gender(), req.city(),
                req.universityId(), req.interests());
        user.setMaxId(maxId);
        User saved = userRepository.save(user);
        return new AuthResponse(jwtService.issue(saved.getId()), userMapper.toResponse(saved));
    }

    /** Issues a session for an existing user (after they proved their MAX identity). */
    public AuthResponse issueFor(User user) {
        return new AuthResponse(jwtService.issue(user.getId()), userMapper.toResponse(user));
    }

    private User buildProfileUser(String firstName, Integer age, String genderName, String city,
                                  Long universityId, List<String> interestNames) {
        Gender gender;
        try {
            gender = Gender.valueOf(genderName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new AuthException(HttpStatus.BAD_REQUEST,
                    "GENDER_INVALID", "Некорректный пол");
        }

        List<EventType> interests = new ArrayList<>();
        if (interestNames != null) {
            for (String s : interestNames) {
                try {
                    interests.add(EventType.valueOf(s.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ignored) { /* skip unknown */ }
            }
        }

        User user = User.builder()
                .firstName(firstName)
                .age(age)
                .gender(gender)
                .city(city)
                .interests(interests)
                .createdAt(LocalDateTime.now(clock))
                // Both sign-up requests are rejected without the consent checkbox (@AssertTrue).
                .personalDataConsentAt(LocalDateTime.now(clock))
                .build();

        if (universityId != null) {
            user.setUniversity(universityRepository.findById(universityId)
                    .orElseThrow(() -> new AuthException(HttpStatus.BAD_REQUEST,
                            "UNIVERSITY_NOT_FOUND", "ВУЗ не найден")));
        }
        return user;
    }

    @Transactional(transactionManager = "transactionManager")
    public AuthResponse linkMax(Long maxId, LinkMaxRequest req) {
        return linkMessenger(Messenger.MAX, maxId, req);
    }

    /**
     * Attaches the caller's messenger identity (MAX or Telegram) to an account
     * created with email + password, so the same account works everywhere.
     * Idempotent: re-linking the same id simply re-issues a token.
     */
    @Transactional(transactionManager = "transactionManager")
    public AuthResponse linkMessenger(Messenger messenger, Long externalId, LinkMaxRequest req) {
        User user = userRepository.findByEmail(req.email())
                .orElseThrow(AuthException::badCredentials);
        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw AuthException.badCredentials();
        }

        Long linked = UserService.getMessengerId(user, messenger);
        if (linked != null && !linked.equals(externalId)) {
            throw AuthException.messengerMismatch(messenger);
        }
        if (linked == null) {
            var existing = messenger == Messenger.MAX
                    ? userRepository.findByMaxId(externalId)
                    : userRepository.findByTelegramId(externalId);
            boolean takenByAnother = existing
                    .filter(other -> !other.getId().equals(user.getId()))
                    .isPresent();
            if (takenByAnother) {
                throw AuthException.messengerTaken(messenger);
            }
            UserService.setMessengerId(user, messenger, externalId);
            userRepository.save(user);
        }

        String token = jwtService.issue(user.getId());
        return new AuthResponse(token, userMapper.toResponse(user));
    }

    @Transactional(transactionManager = "transactionManager")
    public void linkEmail(Long currentUserId, LinkEmailRequest req) {
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new IllegalStateException("authenticated user not found: " + currentUserId));
        // An email typed into the profile alone doesn't enable sign-in — only a password does.
        if (user.getPasswordHash() != null) {
            throw AuthException.alreadyLinked();
        }
        boolean ownEmail = req.email().equalsIgnoreCase(user.getEmail());
        if (!ownEmail && userRepository.existsByEmail(req.email())) {
            throw AuthException.emailTaken();
        }
        user.setEmail(req.email());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        userRepository.save(user);
    }
}
