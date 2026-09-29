package com.join.back.service;

import com.join.back.model.dto.UserProfileUpdateRequest;
import com.join.back.model.dto.UserProfileResponse;
import com.join.back.model.dto.UserRegistrationRequest;
import com.join.back.model.dto.UserResponse;
import com.join.back.model.entity.EventType;
import com.join.back.model.entity.Gender;
import com.join.back.model.entity.University;
import com.join.back.model.entity.User;
import com.join.back.repository.UniversityRepository;
import com.join.back.repository.UserRepository;
import com.join.back.security.Messenger;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class UserService {

    static final String CONSENT_REQUIRED = "Нужно согласие на обработку персональных данных";

    private final UserRepository userRepository;
    private final UniversityRepository universityRepository;
    private final UserMapper userMapper;

    @Transactional(transactionManager = "transactionManager")
    public UserResponse register(Long maxId, UserRegistrationRequest request, MultipartFile photo) {
        return register(Messenger.MAX, maxId, request, photo);
    }

    /** Registers a mini-app user identified by their id in the given messenger. */
    @Transactional(transactionManager = "transactionManager")
    public UserResponse register(Messenger messenger, Long externalId, UserRegistrationRequest request, MultipartFile photo) {
        if (findByMessengerId(messenger, externalId).isPresent()) {
            throw new IllegalStateException("User with " + messenger + " id " + externalId + " already exists");
        }
        AgePolicy.requireAllowedAge(request.age());
        if (!request.personalDataConsent()) {
            throw new IllegalArgumentException(CONSENT_REQUIRED);
        }

        User user = userMapper.toEntity(request);
        setMessengerId(user, messenger, externalId);
        user.setCreatedAt(LocalDateTime.now());
        user.setPersonalDataConsentAt(user.getCreatedAt());

        if (request.universityId() != null) {
            University university = universityRepository.findById(request.universityId())
                    .orElseThrow(() -> new EntityNotFoundException("University not found with id: " + request.universityId()));
            user.setUniversity(university);
        }

        if (photo != null && !photo.isEmpty()) {
            String fileName = UUID.randomUUID() + "_" + photo.getOriginalFilename();
            Path uploadDir = Paths.get("uploads/photos");
            Path filePath = uploadDir.resolve(fileName);
            try {
                Files.createDirectories(uploadDir);
                Files.write(filePath, photo.getBytes());
            } catch (IOException e) {
                throw new RuntimeException("Failed to upload photo", e);
            }
            user.setPhoto("/uploads/photos/" + fileName);
        }

        User savedUser = userRepository.save(user);
        return userMapper.toResponse(savedUser);
    }

    @Transactional(readOnly = true, transactionManager = "transactionManager")
    public Optional<User> findByMaxId(Long maxId) {
        return userRepository.findByMaxId(maxId);
    }

    @Transactional(readOnly = true, transactionManager = "transactionManager")
    public Optional<User> findByMessengerId(Messenger messenger, Long externalId) {
        return switch (messenger) {
            case MAX -> userRepository.findByMaxId(externalId);
            case TELEGRAM -> userRepository.findByTelegramId(externalId);
        };
    }

    public static Long getMessengerId(User user, Messenger messenger) {
        return switch (messenger) {
            case MAX -> user.getMaxId();
            case TELEGRAM -> user.getTelegramId();
        };
    }

    public static void setMessengerId(User user, Messenger messenger, Long externalId) {
        switch (messenger) {
            case MAX -> user.setMaxId(externalId);
            case TELEGRAM -> user.setTelegramId(externalId);
        }
    }

    @Transactional(readOnly = true, transactionManager = "transactionManager")
    public UserProfileResponse getProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));
        return userMapper.toProfileResponse(user);
    }

    /** Someone else's profile; teenagers and adults don't see each other (AgePolicy). */
    @Transactional(readOnly = true, transactionManager = "transactionManager")
    public UserProfileResponse getProfileById(Long viewerId, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));
        User viewer = userRepository.findById(viewerId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + viewerId));
        if (!AgePolicy.canMeet(viewer, user)) {
            throw new EntityNotFoundException("User not found with id: " + userId);
        }
        UserProfileResponse profile = userMapper.toProfileResponse(user);
        return viewerId.equals(userId) ? profile : profile.forOthers();
    }

    /** Consent for accounts created before it was asked at sign-up. */
    @Transactional(transactionManager = "transactionManager")
    public void acceptPersonalDataConsent(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));
        if (user.getPersonalDataConsentAt() == null) {
            user.setPersonalDataConsentAt(LocalDateTime.now());
            userRepository.save(user);
        }
    }

    @Transactional(transactionManager = "transactionManager")
    public UserProfileResponse updateProfile(Long userId, UserProfileUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));

        if (request.firstName() != null) {
            user.setFirstName(request.firstName());
        }
        if (request.lastName() != null) {
            user.setLastName(request.lastName());
        }
        // Blank means "not provided": storing "" would collide on the unique email index.
        if (request.email() != null && !request.email().isBlank()) {
            user.setEmail(request.email().trim());
        }
        if (request.city() != null) {
            user.setCity(request.city());
        }
        if (request.gender() != null) {
            user.setGender(parseGender(request.gender()));
        }
        if (request.age() != null) {
            user.setAge(request.age());
        }
        if (request.interests() != null) {
            user.getInterests().clear();
            request.interests().stream()
                    .map(this::parseEventType)
                    .forEach(user.getInterests()::add);
        }
        if (request.telegramChannel() != null) {
            user.setTelegramChannel(request.telegramChannel());
        }
        if (request.status() != null) {
            user.setStatus(request.status());
        }
        if (request.bio() != null) {
            user.setBio(request.bio());
        }
        if (request.universityId() != null) {
            University university = universityRepository.findById(request.universityId())
                    .orElseThrow(() -> new EntityNotFoundException("University not found with id: " + request.universityId()));
            user.setUniversity(university);
        }

        User savedUser = userRepository.save(user);
        return userMapper.toProfileResponse(savedUser);
    }

    @Transactional(transactionManager = "transactionManager")
    public UserProfileResponse uploadPhoto(Long userId, MultipartFile file) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));

        String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
        Path uploadDir = Paths.get("uploads/photos");
        Path filePath = uploadDir.resolve(fileName);

        try {
            Files.createDirectories(uploadDir);
            Files.write(filePath, file.getBytes());
        } catch (IOException e) {
            throw new RuntimeException("Failed to upload photo", e);
        }

        user.setPhoto("/uploads/photos/" + fileName);
        User savedUser = userRepository.save(user);
        return userMapper.toProfileResponse(savedUser);
    }

    private Gender parseGender(String gender) {
        try {
            return Gender.valueOf(gender.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid gender value: " + gender);
        }
    }

    private EventType parseEventType(String eventType) {
        try {
            return EventType.valueOf(eventType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid interest value: " + eventType);
        }
    }
}
