package com.join.back.web.controller;

import com.join.back.model.entity.User;
import com.join.back.repository.UserRepository;
import com.join.back.security.MaxAuthenticationToken;
import com.join.back.security.UserIdAuthenticationToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BaseAuthControllerTest {

    private final UserRepository repo = mock(UserRepository.class);
    private final BaseAuthController sut = new BaseAuthController(repo) {};

    @AfterEach
    void clear() { SecurityContextHolder.clearContext(); }

    @Test
    void requireCurrentUserIdFromJwt() {
        SecurityContextHolder.getContext().setAuthentication(new UserIdAuthenticationToken(55L));
        assertEquals(55L, sut.requireCurrentUserId());
    }

    @Test
    void requireCurrentUserIdFromMax() {
        SecurityContextHolder.getContext().setAuthentication(
                new MaxAuthenticationToken(999L, "", "", ""));
        User u = new User();
        u.setId(7L);
        when(repo.findByMaxId(999L)).thenReturn(Optional.of(u));
        assertEquals(7L, sut.requireCurrentUserId());
    }
}
