package com.join.back.web.controller.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.join.back.model.dto.UserResponse;
import com.join.back.model.dto.auth.AuthResponse;
import com.join.back.model.dto.auth.LinkMaxRequest;
import com.join.back.model.dto.auth.LoginRequest;
import com.join.back.model.dto.auth.RegisterRequest;
import com.join.back.repository.UserRepository;
import com.join.back.security.AuthException;
import com.join.back.security.MaxAuthenticationToken;
import com.join.back.service.AuthService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(com.join.back.web.controller.GlobalExceptionHandler.class)
class AuthControllerTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @MockBean private AuthService authService;
    @MockBean private UserRepository userRepository;
    @MockBean private com.join.back.service.MaxLinkService maxLinkService;
    @MockBean private com.join.back.security.LoginAttemptService loginAttemptService;

    private UserResponse anyUser() {
        return new UserResponse(
                42L, null, "x@y.com", "Moscow", "X",
                com.join.back.model.entity.Gender.MALE, 20, java.util.List.of(),
                null, null, null, true
        );
    }

    @Test
    void registerReturns200WithToken() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
                .thenReturn(new AuthResponse("the-token", anyUser()));

        String body = json.writeValueAsString(new RegisterRequest(
                "x@y.com", "secret123", "X", 20, "MALE", "Moscow", null, List.of(), true
        ));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("the-token"));
    }

    @Test
    void registerEmailTakenReturns409WithCode() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(AuthException.emailTaken());
        String body = json.writeValueAsString(new RegisterRequest(
                "x@y.com", "secret123", "X", 20, "MALE", "Moscow", null, List.of(), true
        ));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"))
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void loginBadCredentialsReturns401() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(AuthException.badCredentials());
        String body = json.writeValueAsString(new LoginRequest("x@y.com", "wrong"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
    }

    @Test
    void registerValidatesEmptyPassword() throws Exception {
        String body = json.writeValueAsString(new RegisterRequest(
                "x@y.com", "", "X", 20, "MALE", "Moscow", null, List.of(), true
        ));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_TOO_SHORT"));
    }

    @Test
    void registerShortPasswordReturnsPasswordTooShort() throws Exception {
        String body = json.writeValueAsString(new RegisterRequest(
                "x@y.com", "short", "X", 20, "MALE", "Moscow", null, List.of(), true
        ));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_TOO_SHORT"));
    }

    @Test
    void registerInvalidEmailReturnsEmailInvalid() throws Exception {
        String body = json.writeValueAsString(new RegisterRequest(
                "not-an-email", "secret123", "X", 20, "MALE", "Moscow", null, List.of(), true
        ));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMAIL_INVALID"));
    }

    @Test
    void registerWithoutConsentReturns400() throws Exception {
        String body = json.writeValueAsString(new RegisterRequest(
                "x@y.com", "secret123", "X", 20, "MALE", "Moscow", null, List.of(), false
        ));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verify(authService, never()).register(any());
    }

    @Test
    void registerResponseDoesNotLeakPasswordHash() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
                .thenReturn(new AuthResponse("the-token", anyUser()));
        String body = json.writeValueAsString(new RegisterRequest(
                "x@y.com", "secret123", "X", 20, "MALE", "Moscow", null, List.of(), true
        ));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
    }

    // ===== link-max: Mini App attaching this MAX account to a mobile account =====

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAsMax(long maxId) {
        SecurityContextHolder.getContext().setAuthentication(
                new MaxAuthenticationToken(maxId, "T", "", "t"));
    }

    @Test
    void linkMaxReturnsTokenForTheExistingAccount() throws Exception {
        authenticateAsMax(99L);
        when(authService.linkMax(org.mockito.ArgumentMatchers.eq(99L), any(LinkMaxRequest.class)))
                .thenReturn(new AuthResponse("linked-token", anyUser()));

        String body = json.writeValueAsString(new LinkMaxRequest("x@y.com", "secret123"));
        mvc.perform(post("/api/auth/link-max").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("linked-token"));
    }

    @Test
    void linkMaxBadCredentialsReturns401WithCode() throws Exception {
        authenticateAsMax(99L);
        when(authService.linkMax(org.mockito.ArgumentMatchers.anyLong(), any(LinkMaxRequest.class)))
                .thenThrow(AuthException.badCredentials());

        String body = json.writeValueAsString(new LinkMaxRequest("x@y.com", "wrong"));
        mvc.perform(post("/api/auth/link-max").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
    }

    @Test
    void linkMaxAlreadyUsedReturns409WithCode() throws Exception {
        authenticateAsMax(99L);
        when(authService.linkMax(org.mockito.ArgumentMatchers.anyLong(), any(LinkMaxRequest.class)))
                .thenThrow(AuthException.maxTaken());

        String body = json.writeValueAsString(new LinkMaxRequest("x@y.com", "secret123"));
        mvc.perform(post("/api/auth/link-max").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MAX_TAKEN"));
    }
}
