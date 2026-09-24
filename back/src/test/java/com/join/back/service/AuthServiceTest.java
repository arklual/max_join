package com.join.back.service;

import com.join.back.config.JwtProperties;
import com.join.back.model.dto.UserResponse;
import com.join.back.model.dto.auth.LinkEmailRequest;
import com.join.back.model.dto.auth.LinkMaxRequest;
import com.join.back.model.dto.auth.LoginRequest;
import com.join.back.model.dto.auth.RegisterRequest;
import com.join.back.model.dto.auth.AuthResponse;
import com.join.back.model.entity.Gender;
import com.join.back.model.entity.User;
import com.join.back.repository.UserRepository;
import com.join.back.security.AuthException;
import com.join.back.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private static final String SECRET_BASE64 =
            Base64.getEncoder().encodeToString("0123456789ABCDEF0123456789ABCDEF".getBytes());

    private UserRepository userRepository;
    private com.join.back.repository.UniversityRepository universityRepository;
    private com.join.back.service.UserMapper userMapper;
    private JwtService jwtService;
    private org.springframework.security.crypto.password.PasswordEncoder encoder;
    private AuthService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        userMapper = mock(com.join.back.service.UserMapper.class);
        Clock clock = Clock.fixed(Instant.parse("2026-05-15T12:00:00Z"), ZoneOffset.UTC);
        jwtService = new JwtService(new JwtProperties(SECRET_BASE64, 30), clock);
        encoder = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
        universityRepository = mock(com.join.back.repository.UniversityRepository.class);
        service = new AuthService(userRepository, universityRepository, userMapper, jwtService, encoder, clock);
    }

    @Test
    void registerHappy() {
        RegisterRequest req = new RegisterRequest(
                "ivan@example.com", "secret123",
                "Ivan", 22, "MALE", "Moscow", null, List.of()
        );
        when(userRepository.existsByEmail("ivan@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(42L);
            return u;
        });
        when(userMapper.toResponse(any(User.class))).thenReturn(new UserResponse(
                42L, null, "ivan@example.com", "Moscow", "Ivan",
                com.join.back.model.entity.Gender.MALE, 22, List.of(),
                null, null, null
        ));

        AuthResponse resp = service.register(req);

        assertNotNull(resp.token());
        assertEquals(42L, jwtService.verify(resp.token()));
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertEquals("ivan@example.com", saved.getEmail());
        assertNotNull(saved.getPasswordHash());
        assertNull(saved.getMaxId());
    }

    @Test
    void registerEmailTaken() {
        when(userRepository.existsByEmail("dup@example.com")).thenReturn(true);
        RegisterRequest req = new RegisterRequest(
                "dup@example.com", "secret123",
                "Anna", 20, "FEMALE", "Spb", null, List.of()
        );
        AuthException ex = assertThrows(AuthException.class, () -> service.register(req));
        assertEquals("EMAIL_TAKEN", ex.getCode());
    }

    @Test
    void loginHappy() {
        User stored = User.builder()
                .id(7L).email("a@b.com")
                .passwordHash(encoder.encode("secret123"))
                .firstName("A").age(20).gender(Gender.MALE).city("X")
                .createdAt(LocalDateTime.parse("2026-01-01T00:00:00"))
                .build();
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(stored));
        when(userMapper.toResponse(stored)).thenReturn(new UserResponse(
                7L, null, "a@b.com", "X", "A",
                Gender.MALE, 20, java.util.List.of(),
                null, null, null
        ));

        AuthResponse resp = service.login(new LoginRequest("a@b.com", "secret123"));
        assertEquals(7L, jwtService.verify(resp.token()));
    }

    @Test
    void loginWrongPassword() {
        User stored = User.builder()
                .id(7L).email("a@b.com")
                .passwordHash(encoder.encode("correct"))
                .build();
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(stored));

        AuthException ex = assertThrows(AuthException.class,
                () -> service.login(new LoginRequest("a@b.com", "wrong")));
        assertEquals("BAD_CREDENTIALS", ex.getCode());
    }

    @Test
    void loginUnknownEmail() {
        when(userRepository.findByEmail("ghost@b.com")).thenReturn(Optional.empty());
        AuthException ex = assertThrows(AuthException.class,
                () -> service.login(new LoginRequest("ghost@b.com", "x")));
        assertEquals("BAD_CREDENTIALS", ex.getCode());
    }

    @Test
    void linkEmailHappy() {
        User tg = User.builder().id(11L).maxId(99L).firstName("T").build();
        when(userRepository.findById(11L)).thenReturn(Optional.of(tg));
        when(userRepository.existsByEmail("new@x.com")).thenReturn(false);

        service.linkEmail(11L, new LinkEmailRequest("new@x.com", "secret123"));

        assertEquals("new@x.com", tg.getEmail());
        assertNotNull(tg.getPasswordHash());
    }

    @Test
    void linkEmailAlreadyLinked() {
        User tg = User.builder().id(11L).email("existing@x.com").passwordHash("hash").build();
        when(userRepository.findById(11L)).thenReturn(Optional.of(tg));
        AuthException ex = assertThrows(AuthException.class,
                () -> service.linkEmail(11L, new LinkEmailRequest("new@x.com", "secret123")));
        assertEquals("ALREADY_LINKED", ex.getCode());
    }

    @Test
    void linkEmailAllowedWhenProfileEmailSetWithoutPassword() {
        User tg = User.builder().id(11L).maxId(99L).email("mine@x.com").build();
        when(userRepository.findById(11L)).thenReturn(Optional.of(tg));

        service.linkEmail(11L, new LinkEmailRequest("mine@x.com", "secret123"));

        assertTrue(encoder.matches("secret123", tg.getPasswordHash()));
        verify(userRepository).save(tg);
    }

    @Test
    void linkEmailTaken() {
        User tg = User.builder().id(11L).maxId(99L).build();
        when(userRepository.findById(11L)).thenReturn(Optional.of(tg));
        when(userRepository.existsByEmail("taken@x.com")).thenReturn(true);
        AuthException ex = assertThrows(AuthException.class,
                () -> service.linkEmail(11L, new LinkEmailRequest("taken@x.com", "secret123")));
        assertEquals("EMAIL_TAKEN", ex.getCode());
    }

    // ===== link-max: same account usable from the Mini App and the app =====

    @Test
    void linkMaxAttachesMaxIdAndIssuesToken() {
        User mobileAccount = User.builder()
                .id(5L).email("a@b.com")
                .passwordHash(encoder.encode("secret123"))
                .build();
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(mobileAccount));
        when(userRepository.findByMaxId(99L)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userMapper.toResponse(any(User.class))).thenReturn(new UserResponse(
                5L, 99L, "a@b.com", "X", "A", Gender.MALE, 20, List.of(), null, null, null
        ));

        AuthResponse resp = service.linkMax(99L, new LinkMaxRequest("a@b.com", "secret123"));

        assertEquals(99L, mobileAccount.getMaxId());
        assertEquals(5L, jwtService.verify(resp.token()));
    }

    @Test
    void linkMaxIsIdempotentForSameMaxId() {
        User account = User.builder()
                .id(5L).email("a@b.com").maxId(99L)
                .passwordHash(encoder.encode("secret123"))
                .build();
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(account));
        when(userMapper.toResponse(any(User.class))).thenReturn(new UserResponse(
                5L, 99L, "a@b.com", "X", "A", Gender.MALE, 20, List.of(), null, null, null
        ));

        AuthResponse resp = service.linkMax(99L, new LinkMaxRequest("a@b.com", "secret123"));

        assertEquals(5L, jwtService.verify(resp.token()));
        verify(userRepository, org.mockito.Mockito.never()).save(any(User.class));
    }

    @Test
    void linkMaxRejectsAccountBoundToAnotherMax() {
        User account = User.builder()
                .id(5L).email("a@b.com").maxId(1L)
                .passwordHash(encoder.encode("secret123"))
                .build();
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(account));

        AuthException ex = assertThrows(AuthException.class,
                () -> service.linkMax(99L, new LinkMaxRequest("a@b.com", "secret123")));
        assertEquals("MAX_MISMATCH", ex.getCode());
    }

    @Test
    void linkMaxRejectsMaxAlreadyUsedByAnotherAccount() {
        User account = User.builder()
                .id(5L).email("a@b.com")
                .passwordHash(encoder.encode("secret123"))
                .build();
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(account));
        when(userRepository.findByMaxId(99L))
                .thenReturn(Optional.of(User.builder().id(6L).maxId(99L).build()));

        AuthException ex = assertThrows(AuthException.class,
                () -> service.linkMax(99L, new LinkMaxRequest("a@b.com", "secret123")));
        assertEquals("MAX_TAKEN", ex.getCode());
    }

    @Test
    void linkMaxRejectsWrongPassword() {
        User account = User.builder()
                .id(5L).email("a@b.com")
                .passwordHash(encoder.encode("correct"))
                .build();
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(account));

        AuthException ex = assertThrows(AuthException.class,
                () -> service.linkMax(99L, new LinkMaxRequest("a@b.com", "wrong")));
        assertEquals("BAD_CREDENTIALS", ex.getCode());
    }
}
