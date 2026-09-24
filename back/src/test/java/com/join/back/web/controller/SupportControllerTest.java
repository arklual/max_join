package com.join.back.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.join.back.model.dto.SendSupportMessageRequest;
import com.join.back.model.dto.SupportMessageResponse;
import com.join.back.model.dto.SupportTicketResponse;
import com.join.back.model.entity.SupportSenderType;
import com.join.back.model.entity.SupportTicketStatus;
import com.join.back.model.entity.User;
import com.join.back.repository.UserRepository;
import com.join.back.security.MaxAuthenticationToken;
import com.join.back.service.SupportService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SupportController.class,
        excludeAutoConfiguration = {org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class})
@ActiveProfiles("test")
class SupportControllerTest {

    private static final Long TELEGRAM_ID = 123456789L;
    private static final Long USER_ID = 1L;
    private static final LocalDateTime FIXED_TIME = LocalDateTime.of(2026, 3, 21, 10, 0);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SupportService supportService;

    @MockBean
    private UserRepository userRepository;

    @Test
    void shouldGetOrCreateTicket() throws Exception {
        setAuthentication();

        SupportTicketResponse ticketResponse = new SupportTicketResponse(
                1L, USER_ID, "Ivan", TELEGRAM_ID,
                SupportTicketStatus.OPEN, FIXED_TIME, null, null, 0L
        );

        when(userRepository.findByMaxId(TELEGRAM_ID))
                .thenReturn(Optional.of(buildUser()));
        when(supportService.getOrCreateTicket(USER_ID)).thenReturn(ticketResponse);

        mockMvc.perform(get("/api/support/ticket"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.userId").value(USER_ID));
    }

    @Test
    void shouldGetMessages() throws Exception {
        setAuthentication();

        SupportMessageResponse messageResponse = new SupportMessageResponse(
                10L, 1L, SupportSenderType.USER, "Hello", FIXED_TIME, false
        );
        Page<SupportMessageResponse> page = new PageImpl<>(
                List.of(messageResponse), PageRequest.of(0, 20), 1
        );

        when(userRepository.findByMaxId(TELEGRAM_ID))
                .thenReturn(Optional.of(buildUser()));
        when(supportService.getMessages(eq(USER_ID), any())).thenReturn(page);

        mockMvc.perform(get("/api/support/ticket/messages")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(10))
                .andExpect(jsonPath("$.content[0].text").value("Hello"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void shouldSendMessage() throws Exception {
        setAuthentication();

        SendSupportMessageRequest request = new SendSupportMessageRequest("My question");
        SupportMessageResponse messageResponse = new SupportMessageResponse(
                11L, 1L, SupportSenderType.USER, "My question", FIXED_TIME, false
        );

        when(userRepository.findByMaxId(TELEGRAM_ID))
                .thenReturn(Optional.of(buildUser()));
        when(supportService.sendUserMessage(eq(USER_ID), any())).thenReturn(messageResponse);

        mockMvc.perform(post("/api/support/ticket/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(11))
                .andExpect(jsonPath("$.text").value("My question"))
                .andExpect(jsonPath("$.senderType").value("USER"));
    }

    @Test
    void shouldReturnBadRequestForEmptyMessage() throws Exception {
        setAuthentication();

        when(userRepository.findByMaxId(TELEGRAM_ID))
                .thenReturn(Optional.of(buildUser()));

        mockMvc.perform(post("/api/support/ticket/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldMarkRead() throws Exception {
        setAuthentication();

        when(userRepository.findByMaxId(TELEGRAM_ID))
                .thenReturn(Optional.of(buildUser()));

        mockMvc.perform(put("/api/support/ticket/read"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldReturn404WhenTicketNotFound() throws Exception {
        setAuthentication();

        when(userRepository.findByMaxId(TELEGRAM_ID))
                .thenReturn(Optional.of(buildUser()));
        when(supportService.getMessages(eq(USER_ID), any()))
                .thenThrow(new EntityNotFoundException("Support ticket not found for user: " + USER_ID));

        mockMvc.perform(get("/api/support/ticket/messages"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Support ticket not found for user: " + USER_ID));
    }

    // ---- helpers ----

    private void setAuthentication() {
        MaxAuthenticationToken auth = new MaxAuthenticationToken(TELEGRAM_ID, "Ivan", null, null);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private User buildUser() {
        return User.builder()
                .id(USER_ID)
                .maxId(TELEGRAM_ID)
                .firstName("Ivan")
                .createdAt(FIXED_TIME)
                .build();
    }
}
