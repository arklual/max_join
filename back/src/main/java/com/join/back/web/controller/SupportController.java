package com.join.back.web.controller;

import com.join.back.model.dto.SendSupportMessageRequest;
import com.join.back.model.dto.SupportMessageResponse;
import com.join.back.model.dto.SupportTicketResponse;
import com.join.back.repository.UserRepository;
import com.join.back.service.SupportService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springdoc.core.annotations.ParameterObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.join.back.config.ApiDocs;

@Tag(name = ApiDocs.SUPPORT)
@RestController
@RequestMapping("/api/support")
public class SupportController extends BaseAuthController {

    private final SupportService supportService;

    public SupportController(UserRepository userRepository, SupportService supportService) {
        super(userRepository);
        this.supportService = supportService;
    }

    @Operation(summary = "Обращение в поддержку",
            description = "Возвращает обращение пользователя; при первом вызове создаёт его.")
    @GetMapping("/ticket")
    public ResponseEntity<SupportTicketResponse> getOrCreateTicket() {
        Long userId = requireCurrentUserId();
        return ResponseEntity.ok(supportService.getOrCreateTicket(userId));
    }

    @Operation(summary = "Переписка с поддержкой")
    @GetMapping("/ticket/messages")
    public ResponseEntity<Page<SupportMessageResponse>> getMessages(@ParameterObject Pageable pageable) {
        Long userId = requireCurrentUserId();
        return ResponseEntity.ok(supportService.getMessages(userId, pageable));
    }

    @Operation(summary = "Написать в поддержку")
    @PostMapping("/ticket/messages")
    public ResponseEntity<SupportMessageResponse> sendMessage(
            @Valid @RequestBody SendSupportMessageRequest request
    ) {
        Long userId = requireCurrentUserId();
        return ResponseEntity.ok(supportService.sendUserMessage(userId, request));
    }

    @Operation(summary = "Отметить ответы поддержки прочитанными")
    @PutMapping("/ticket/read")
    public ResponseEntity<Void> markRead() {
        Long userId = requireCurrentUserId();
        supportService.markReadByUser(userId);
        return ResponseEntity.ok().build();
    }
}
