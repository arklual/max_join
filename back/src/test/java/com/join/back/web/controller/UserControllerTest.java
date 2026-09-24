package com.join.back.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.join.back.model.dto.UserProfileResponse;
import com.join.back.model.dto.UserProfileUpdateRequest;
import com.join.back.model.dto.UserRegistrationRequest;
import com.join.back.model.dto.UserResponse;
import com.join.back.model.entity.EventType;
import com.join.back.model.entity.Gender;
import com.join.back.model.entity.User;
import com.join.back.repository.UserRepository;
import com.join.back.security.MaxAuthenticationToken;
import com.join.back.service.UserService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import com.join.back.security.Messenger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UserController.class,
        excludeAutoConfiguration = {org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class})
@ActiveProfiles("test")
class UserControllerTest {

    private static final Long TELEGRAM_ID = 123456789L;
    private static final Long USER_ID = 1L;
    private static final LocalDateTime FIXED_CREATED_AT = LocalDateTime.of(2026, 3, 1, 12, 0);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private UserService userService;

    @Test
    void shouldRegisterUser() throws Exception {
        setAuthentication();

        UserResponse response = new UserResponse(
                1L, TELEGRAM_ID, "test@example.com", "Moscow", "John",
                Gender.MALE, 25, List.of("MUSIC", "ART"), null, null, null
        );

        when(userService.register(eq(Messenger.MAX), eq(TELEGRAM_ID), any(UserRegistrationRequest.class), any())).thenReturn(response);

        mockMvc.perform(multipart("/api/users/register")
                        .param("email", "test@example.com")
                        .param("city", "Moscow")
                        .param("name", "John")
                        .param("gender", "MALE")
                        .param("age", "25")
                        .param("interests", "MUSIC", "ART"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.maxId").value(TELEGRAM_ID))
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.firstName").value("John"))
                .andExpect(jsonPath("$.gender").value("MALE"))
                .andExpect(jsonPath("$.age").value(25))
                .andExpect(jsonPath("$.interests[0]").value("MUSIC"))
                .andExpect(jsonPath("$.interests[1]").value("ART"));
    }

    @Test
    void shouldRegisterUserWithoutEmail() throws Exception {
        setAuthentication();

        UserResponse response = new UserResponse(
                1L, TELEGRAM_ID, null, "Moscow", "John",
                Gender.MALE, 25, List.of("MUSIC"), null, null, null
        );

        when(userService.register(eq(Messenger.MAX), eq(TELEGRAM_ID), any(UserRegistrationRequest.class), any())).thenReturn(response);

        mockMvc.perform(multipart("/api/users/register")
                        .param("city", "Moscow")
                        .param("name", "John")
                        .param("gender", "MALE")
                        .param("age", "25")
                        .param("interests", "MUSIC"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.maxId").value(TELEGRAM_ID))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.firstName").value("John"));
    }

    @Test
    void shouldReturnCurrentUserWhenFound() throws Exception {
        setAuthentication();

        User user = buildTestUser();
        when(userRepository.findByMaxId(TELEGRAM_ID)).thenReturn(Optional.of(user));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.maxId").value(TELEGRAM_ID))
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.firstName").value("John"));
    }

    @Test
    void shouldReturn404WhenCurrentUserNotFound() throws Exception {
        setAuthentication();

        User currentUser = buildTestUser();
        when(userRepository.findByMaxId(TELEGRAM_ID)).thenReturn(Optional.of(currentUser));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldGetProfile() throws Exception {
        setAuthentication();

        UserProfileResponse response = new UserProfileResponse(
                1L, TELEGRAM_ID, "test@example.com", "Moscow", "John", "Doe",
                Gender.MALE, 25, null, List.of("MUSIC"), "@johnchannel", "Active", "Hello world",
                null, null
        ,
                false);

        when(userRepository.findByMaxId(TELEGRAM_ID)).thenReturn(Optional.of(buildTestUser()));
        when(userService.getProfile(USER_ID)).thenReturn(response);

        mockMvc.perform(get("/api/users/me/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.maxId").value(TELEGRAM_ID))
                .andExpect(jsonPath("$.firstName").value("John"))
                .andExpect(jsonPath("$.lastName").value("Doe"))
                .andExpect(jsonPath("$.bio").value("Hello world"))
                .andExpect(jsonPath("$.telegramChannel").value("@johnchannel"))
                .andExpect(jsonPath("$.status").value("Active"));
    }

    @Test
    void shouldReturn404WhenProfileNotFound() throws Exception {
        setAuthentication();

        when(userRepository.findByMaxId(TELEGRAM_ID)).thenReturn(Optional.of(buildTestUser()));
        when(userService.getProfile(USER_ID))
                .thenThrow(new EntityNotFoundException("User not found with id: " + USER_ID));

        mockMvc.perform(get("/api/users/me/profile"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("User not found with id: " + USER_ID));
    }

    @Test
    void shouldUpdateProfile() throws Exception {
        setAuthentication();

        UserProfileUpdateRequest request = new UserProfileUpdateRequest(
                "UpdatedName", "Doe", "new@example.com", "SPB",
                "FEMALE", 30, List.of("SPORT"), "@channel", "New status", "New bio", null
        );

        UserProfileResponse response = new UserProfileResponse(
                1L, TELEGRAM_ID, "new@example.com", "SPB", "UpdatedName", "Doe",
                Gender.FEMALE, 30, null, List.of("SPORT"), "@channel", "New status", "New bio",
                null, null
        ,
                false);

        when(userRepository.findByMaxId(TELEGRAM_ID)).thenReturn(Optional.of(buildTestUser()));
        when(userService.updateProfile(eq(USER_ID), any(UserProfileUpdateRequest.class))).thenReturn(response);

        mockMvc.perform(put("/api/users/me/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("UpdatedName"))
                .andExpect(jsonPath("$.lastName").value("Doe"))
                .andExpect(jsonPath("$.email").value("new@example.com"))
                .andExpect(jsonPath("$.city").value("SPB"))
                .andExpect(jsonPath("$.gender").value("FEMALE"))
                .andExpect(jsonPath("$.age").value(30));
    }

    @Test
    void shouldUploadPhoto() throws Exception {
        setAuthentication();

        MockMultipartFile file = new MockMultipartFile(
                "photo", "photo.jpg", MediaType.IMAGE_JPEG_VALUE, "test image content".getBytes()
        );

        UserProfileResponse response = new UserProfileResponse(
                1L, TELEGRAM_ID, "test@example.com", "Moscow", "John", null,
                Gender.MALE, 25, "/uploads/photos/photo.jpg", List.of("MUSIC"), null, null, null,
                null, null
        ,
                false);

        when(userRepository.findByMaxId(TELEGRAM_ID)).thenReturn(Optional.of(buildTestUser()));
        when(userService.uploadPhoto(eq(USER_ID), any())).thenReturn(response);

        mockMvc.perform(multipart("/api/users/me/photo")
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photo").value("/uploads/photos/photo.jpg"));
    }

    private void setAuthentication() {
        MaxAuthenticationToken authentication =
                new MaxAuthenticationToken(TELEGRAM_ID, "John", "Doe", "johndoe");
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private User buildTestUser() {
        return User.builder()
                .id(USER_ID)
                .maxId(TELEGRAM_ID)
                .email("test@example.com")
                .city("Moscow")
                .firstName("John")
                .gender(Gender.MALE)
                .age(25)
                .createdAt(FIXED_CREATED_AT)
                .interests(new ArrayList<>(List.of(EventType.MUSIC)))
                .build();
    }
}
