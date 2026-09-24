package com.join.back.web.controller;

import com.join.back.model.dto.SendSupportMessageRequest;
import com.join.back.model.dto.SupportMessageResponse;
import com.join.back.model.dto.SupportTicketResponse;
import com.join.back.model.dto.UpdateTicketStatusRequest;
import com.join.back.service.SupportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/support")
@RequiredArgsConstructor
public class AdminSupportController {

    private final SupportService supportService;

    @GetMapping("/tickets")
    public ResponseEntity<List<SupportTicketResponse>> getAllTickets(Pageable pageable) {
        return ResponseEntity.ok(supportService.getAllTickets(pageable));
    }

    @GetMapping("/tickets/{ticketId}/messages")
    public ResponseEntity<Page<SupportMessageResponse>> getTicketMessages(
            @PathVariable Long ticketId,
            Pageable pageable
    ) {
        return ResponseEntity.ok(supportService.getTicketMessages(ticketId, pageable));
    }

    @PostMapping("/tickets/{ticketId}/messages")
    public ResponseEntity<SupportMessageResponse> sendOperatorMessage(
            @PathVariable Long ticketId,
            @Valid @RequestBody SendSupportMessageRequest request
    ) {
        return ResponseEntity.ok(supportService.sendOperatorMessage(ticketId, request));
    }

    @PutMapping("/tickets/{ticketId}/read")
    public ResponseEntity<Void> markTicketRead(@PathVariable Long ticketId) {
        supportService.markReadByOperator(ticketId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/tickets/{ticketId}/status")
    public ResponseEntity<SupportTicketResponse> updateTicketStatus(
            @PathVariable Long ticketId,
            @RequestBody UpdateTicketStatusRequest request
    ) {
        return ResponseEntity.ok(supportService.updateStatus(ticketId, request.status()));
    }
}
