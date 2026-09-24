package com.join.back.service;

import com.join.back.model.dto.auth.AuthResponse;
import com.join.back.model.dto.auth.MaxRegisterRequest;
import com.join.back.model.entity.User;
import com.join.back.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MaxLoginServiceTest {

    private UserRepository userRepository;
    private AuthService authService;
    private MaxLoginService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        authService = mock(AuthService.class);
        MaxBotInfoService botInfo = mock(MaxBotInfoService.class);
        when(botInfo.getUsername()).thenReturn("join_bot");
        service = new MaxLoginService(userRepository, authService, botInfo);
    }

    @Test
    void existingUserGetsSessionOnceAfterConfirming() {
        MaxLoginService.Started started = service.start();
        assertEquals("https://max.ru/join_bot?start=login_" + started.token(), started.url());
        assertEquals(MaxLoginService.Status.PENDING, service.poll(started.token()).status());

        User user = User.builder().id(7L).maxId(555L).build();
        when(userRepository.findByMaxId(555L)).thenReturn(Optional.of(user));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        AuthResponse auth = new AuthResponse("jwt", null);
        when(authService.issueFor(user)).thenReturn(auth);

        assertTrue(service.confirm(started.token(), 555L).isPresent());
        MaxLoginService.PollResult result = service.poll(started.token());
        assertEquals(MaxLoginService.Status.SUCCESS, result.status());
        assertSame(auth, result.auth());
        // one-time: the session can't be picked up twice
        assertEquals(MaxLoginService.Status.EXPIRED, service.poll(started.token()).status());
    }

    @Test
    void newMaxUserRegistersWithoutPassword() {
        MaxLoginService.Started started = service.start();
        when(userRepository.findByMaxId(777L)).thenReturn(Optional.empty());

        assertTrue(service.confirm(started.token(), 777L).isEmpty());
        assertEquals(MaxLoginService.Status.NEEDS_REGISTRATION, service.poll(started.token()).status());

        MaxRegisterRequest req = new MaxRegisterRequest("Аня", 20, "FEMALE", "Москва", null, List.of("MUSIC"));
        when(authService.registerWithMax(777L, req)).thenReturn(new AuthResponse("jwt", null));
        assertEquals("jwt", service.register(started.token(), req).token());
        verify(authService).registerWithMax(777L, req);
    }

    @Test
    void unknownTokenIsExpired() {
        assertNull(service.confirm("nope", 1L));
        assertEquals(MaxLoginService.Status.EXPIRED, service.poll("nope").status());
    }
}
