package com.join.back.web.controller;

import com.join.back.model.dto.EventCardResponse;
import com.join.back.model.entity.EventType;
import com.join.back.model.entity.User;
import com.join.back.repository.UserRepository;
import com.join.back.security.MaxAuthenticationToken;
import com.join.back.service.LikeService;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = LikeController.class,
        excludeAutoConfiguration = {org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class})
@ActiveProfiles("test")
class LikeControllerTest {

    private static final LocalDate FIXED_DATE = LocalDate.of(2026, 4, 15);
    private static final LocalTime FIXED_TIME = LocalTime.of(19, 0);

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LikeService likeService;

    @MockBean
    private UserRepository userRepository;

    @Test
    void shouldLikeEvent() throws Exception {
        setupAuthentication();

        mockMvc.perform(post("/api/events/10/like"))
                .andExpect(status().isOk());

        verify(likeService).like(1L, 10L);
    }

    @Test
    void shouldUnlikeEvent() throws Exception {
        setupAuthentication();

        mockMvc.perform(delete("/api/events/10/like"))
                .andExpect(status().isOk());

        verify(likeService).unlike(1L, 10L);
    }

    @Test
    void shouldReturnLikedEvents() throws Exception {
        setupAuthentication();

        EventCardResponse cardResponse = new EventCardResponse(
                10L, "Test Event", EventType.MUSIC, "https://example.com/image.jpg",
                BigDecimal.valueOf(1500), null, null, null, FIXED_DATE, FIXED_TIME, "Moscow", true, false
        , false);
        Page<EventCardResponse> page = new PageImpl<>(List.of(cardResponse), PageRequest.of(0, 10), 1);

        when(likeService.getLikedEvents(eq(1L), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/events/liked")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(10))
                .andExpect(jsonPath("$.content[0].liked").value(true))
                .andExpect(jsonPath("$.totalElements").value(1));
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
