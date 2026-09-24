package com.join.back.service;

import com.join.back.model.dto.UserProfileResponse;
import com.join.back.model.dto.UserProfileUpdateRequest;
import com.join.back.model.dto.UserRegistrationRequest;
import com.join.back.model.dto.UserResponse;
import com.join.back.model.entity.EventType;
import com.join.back.model.entity.Gender;
import com.join.back.model.entity.User;
import com.join.back.repository.UniversityRepository;
import com.join.back.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final Long TELEGRAM_ID = 123456789L;
    private static final Long USER_ID = 1L;
    private static final LocalDateTime FIXED_CREATED_AT = LocalDateTime.of(2026, 3, 1, 12, 0);

    @Mock
    private UserRepository userRepository;

    @Mock
    private UniversityRepository universityRepository;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private UserService userService;

    @Test
    void shouldRegisterNewUser() {
        UserRegistrationRequest request = new UserRegistrationRequest(
                "test@example.com", "Moscow", "John", "MALE", 25, List.of("MUSIC", "ART"), null
        );

        User mappedUser = createTestUser();
        User savedUser = createTestUser();
        savedUser.setId(1L);

        UserResponse expectedResponse = new UserResponse(
                1L, TELEGRAM_ID, "test@example.com", "Moscow", "John",
                Gender.MALE, 25, List.of("MUSIC", "ART"), null, null, null
        );

        when(userRepository.findByMaxId(TELEGRAM_ID)).thenReturn(Optional.empty());
        when(userMapper.toEntity(request)).thenReturn(mappedUser);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(userMapper.toResponse(savedUser)).thenReturn(expectedResponse);

        UserResponse result = userService.register(TELEGRAM_ID, request, null);

        assertNotNull(result);
        assertEquals("test@example.com", result.email());
        assertEquals("John", result.firstName());
        assertEquals(TELEGRAM_ID, result.maxId());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void shouldRegisterNewUserWithoutEmail() {
        UserRegistrationRequest request = new UserRegistrationRequest(
                null, "Moscow", "John", "MALE", 25, List.of("MUSIC"), null
        );

        User mappedUser = createTestUser();
        mappedUser.setEmail(null);
        User savedUser = createTestUser();
        savedUser.setId(1L);
        savedUser.setEmail(null);

        UserResponse expectedResponse = new UserResponse(
                1L, TELEGRAM_ID, null, "Moscow", "John",
                Gender.MALE, 25, List.of("MUSIC"), null, null, null
        );

        when(userRepository.findByMaxId(TELEGRAM_ID)).thenReturn(Optional.empty());
        when(userMapper.toEntity(request)).thenReturn(mappedUser);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(userMapper.toResponse(savedUser)).thenReturn(expectedResponse);

        UserResponse result = userService.register(TELEGRAM_ID, request, null);

        assertNotNull(result);
        assertEquals(null, result.email());
        assertEquals("John", result.firstName());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void shouldThrowExceptionWhenRegisteringDuplicateUser() {
        UserRegistrationRequest request = new UserRegistrationRequest(
                "test@example.com", "Moscow", "John", "MALE", 25, List.of("MUSIC"), null
        );

        when(userRepository.findByMaxId(TELEGRAM_ID)).thenReturn(Optional.of(createTestUser()));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> userService.register(TELEGRAM_ID, request, null)
        );

        assertEquals("User with MAX id " + TELEGRAM_ID + " already exists", exception.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void shouldFindUserByMaxId() {
        User user = createTestUser();
        when(userRepository.findByMaxId(TELEGRAM_ID)).thenReturn(Optional.of(user));

        Optional<User> result = userService.findByMaxId(TELEGRAM_ID);

        assertTrue(result.isPresent());
        assertEquals(TELEGRAM_ID, result.get().getMaxId());
    }

    @Test
    void shouldReturnEmptyWhenUserNotFoundByMaxId() {
        when(userRepository.findByMaxId(TELEGRAM_ID)).thenReturn(Optional.empty());

        Optional<User> result = userService.findByMaxId(TELEGRAM_ID);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldGetProfile() {
        User user = createTestUser();
        UserProfileResponse expectedResponse = new UserProfileResponse(
                1L, TELEGRAM_ID, "test@example.com", "Moscow", "John", null,
                Gender.MALE, 25, null, List.of("MUSIC", "ART"), null, null, null, null, null
        ,
                false);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userMapper.toProfileResponse(user)).thenReturn(expectedResponse);

        UserProfileResponse result = userService.getProfile(USER_ID);

        assertNotNull(result);
        assertEquals("test@example.com", result.email());
        assertEquals(TELEGRAM_ID, result.maxId());
    }

    @Test
    void shouldThrowExceptionWhenProfileNotFound() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        EntityNotFoundException exception = assertThrows(
                EntityNotFoundException.class,
                () -> userService.getProfile(USER_ID)
        );

        assertEquals("User not found with id: " + USER_ID, exception.getMessage());
    }

    @Test
    void shouldUpdateProfilePartially() {
        User user = createTestUser();
        user.setInterests(new ArrayList<>(List.of(EventType.MUSIC)));

        UserProfileUpdateRequest request = new UserProfileUpdateRequest(
                null, "Doe", null, "Saint Petersburg",
                null, null, List.of("ART", "SPORT"), null, null, null, null
        );

        User savedUser = createTestUser();
        savedUser.setLastName("Doe");
        savedUser.setCity("Saint Petersburg");
        savedUser.setInterests(new ArrayList<>(List.of(EventType.ART, EventType.SPORT)));

        UserProfileResponse expectedResponse = new UserProfileResponse(
                1L, TELEGRAM_ID, "test@example.com", "Saint Petersburg", "John", "Doe",
                Gender.MALE, 25, null, List.of("ART", "SPORT"), null, null, null, null, null
        ,
                false);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(userMapper.toProfileResponse(savedUser)).thenReturn(expectedResponse);

        UserProfileResponse result = userService.updateProfile(USER_ID, request);

        assertNotNull(result);
        assertEquals("Saint Petersburg", result.city());
        assertEquals("Doe", result.lastName());
        assertEquals(List.of("ART", "SPORT"), result.interests());
    }

    @Test
    void shouldThrowExceptionWhenUpdatingNonExistentProfile() {
        UserProfileUpdateRequest request = new UserProfileUpdateRequest(
                "Updated", null, null, null, null, null, null, null, null, null, null
        );

        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> userService.updateProfile(USER_ID, request)
        );
    }

    private User createTestUser() {
        return User.builder()
                .id(1L)
                .maxId(TELEGRAM_ID)
                .email("test@example.com")
                .city("Moscow")
                .firstName("John")
                .gender(Gender.MALE)
                .age(25)
                .createdAt(FIXED_CREATED_AT)
                .interests(new ArrayList<>(List.of(EventType.MUSIC, EventType.ART)))
                .build();
    }
}
