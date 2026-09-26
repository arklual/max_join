package com.join.back.web.controller;

import com.join.back.model.dto.NotificationResponse;
import com.join.back.model.entity.NotificationType;
import com.join.back.model.entity.User;
import com.join.back.repository.UserRepository;
import com.join.back.security.MaxAuthenticationToken;
import com.join.back.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = NotificationController.class,
        excludeAutoConfiguration = {org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class})
@ActiveProfiles("test")
class NotificationControllerTest {

    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 3, 21, 14, 0);

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NotificationService notificationService;

    @MockBean
    private UserRepository userRepository;

    @Test
    void shouldReturnNotifications() throws Exception {
        setupAuthentication();

        NotificationResponse response = new NotificationResponse(
                1L, NotificationType.MATCH, "New match!", "You matched with Alice at event \"Test Concert\"",
                100L, 2L, "Alice", null, 7L, "Test Concert", 42L, null, false, FIXED_NOW
        );
        Page<NotificationResponse> page = new PageImpl<>(List.of(response), PageRequest.of(0, 10), 1);
        when(notificationService.getNotifications(eq(1L), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/notifications")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].type").value("MATCH"))
                .andExpect(jsonPath("$.content[0].read").value(false))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void shouldReturnUnreadCount() throws Exception {
        setupAuthentication();
        when(notificationService.countUnread(1L)).thenReturn(5L);

        mockMvc.perform(get("/api/notifications/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(5));
    }

    @Test
    void shouldMarkAllRead() throws Exception {
        setupAuthentication();

        mockMvc.perform(post("/api/notifications/read-all"))
                .andExpect(status().isOk());

        verify(notificationService).markAllRead(1L);
    }

    private void setupAuthentication() {
        User user = User.builder()
                .id(1L)
                .maxId(12345L)
                .build();
        when(userRepository.findByMaxId(12345L)).thenReturn(Optional.of(user));

        MaxAuthenticationToken auth = new MaxAuthenticationToken(12345L, "Test", "User", "testuser");
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
