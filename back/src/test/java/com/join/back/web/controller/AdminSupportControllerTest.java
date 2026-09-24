package com.join.back.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.join.back.model.dto.SendSupportMessageRequest;
import com.join.back.model.dto.SupportMessageResponse;
import com.join.back.model.dto.SupportTicketResponse;
import com.join.back.model.dto.UpdateTicketStatusRequest;
import com.join.back.model.entity.SupportSenderType;
import com.join.back.model.entity.SupportTicketStatus;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminSupportController.class,
        excludeAutoConfiguration = {org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class})
@ActiveProfiles("test")
class AdminSupportControllerTest {

    private static final LocalDateTime FIXED_TIME = LocalDateTime.of(2026, 3, 21, 10, 0);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SupportService supportService;

    @Test
    void shouldGetAllTickets() throws Exception {
        SupportTicketResponse ticketResponse = new SupportTicketResponse(
                1L, 42L, "Ivan", 123456789L,
                SupportTicketStatus.OPEN, FIXED_TIME, FIXED_TIME, "Some preview", 3L
        );

        when(supportService.getAllTickets(any())).thenReturn(List.of(ticketResponse));

        mockMvc.perform(get("/api/admin/support/tickets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].userId").value(42))
                .andExpect(jsonPath("$[0].status").value("OPEN"))
                .andExpect(jsonPath("$[0].unreadCount").value(3));
    }

    @Test
    void shouldGetTicketMessages() throws Exception {
        SupportMessageResponse messageResponse = new SupportMessageResponse(
                10L, 1L, SupportSenderType.USER, "Help me", FIXED_TIME, false
        );
        Page<SupportMessageResponse> page = new PageImpl<>(
                List.of(messageResponse), PageRequest.of(0, 20), 1
        );

        when(supportService.getTicketMessages(eq(1L), any())).thenReturn(page);

        mockMvc.perform(get("/api/admin/support/tickets/1/messages")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(10))
                .andExpect(jsonPath("$.content[0].text").value("Help me"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void shouldSendOperatorMessage() throws Exception {
        SendSupportMessageRequest request = new SendSupportMessageRequest("Operator response");
        SupportMessageResponse messageResponse = new SupportMessageResponse(
                11L, 1L, SupportSenderType.OPERATOR, "Operator response", FIXED_TIME, false
        );

        when(supportService.sendOperatorMessage(eq(1L), any())).thenReturn(messageResponse);

        mockMvc.perform(post("/api/admin/support/tickets/1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(11))
                .andExpect(jsonPath("$.text").value("Operator response"))
                .andExpect(jsonPath("$.senderType").value("OPERATOR"));
    }

    @Test
    void shouldMarkTicketRead() throws Exception {
        mockMvc.perform(put("/api/admin/support/tickets/1/read"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldUpdateTicketStatus() throws Exception {
        UpdateTicketStatusRequest request = new UpdateTicketStatusRequest(SupportTicketStatus.CLOSED);
        SupportTicketResponse closedTicket = new SupportTicketResponse(
                1L, 42L, "Ivan", 123456789L,
                SupportTicketStatus.CLOSED, FIXED_TIME, FIXED_TIME, null, 0L
        );

        when(supportService.updateStatus(eq(1L), eq(SupportTicketStatus.CLOSED))).thenReturn(closedTicket);

        mockMvc.perform(put("/api/admin/support/tickets/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @Test
    void shouldReturn404WhenTicketNotFound() throws Exception {
        when(supportService.getTicketMessages(eq(999L), any()))
                .thenThrow(new EntityNotFoundException("Support ticket not found with id: 999"));

        mockMvc.perform(get("/api/admin/support/tickets/999/messages"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Support ticket not found with id: 999"));
    }

    @Test
    void shouldReturn409WhenSendingToClosedTicket() throws Exception {
        when(supportService.sendOperatorMessage(eq(1L), any()))
                .thenThrow(new IllegalStateException("Ticket is closed"));

        mockMvc.perform(post("/api/admin/support/tickets/1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"test\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Ticket is closed"));
    }
}
