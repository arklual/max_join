package com.join.back.web.controller;

import com.join.back.model.dto.MatchResponse;
import com.join.back.repository.UserRepository;
import com.join.back.service.ContactRequestService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.join.back.config.ApiDocs;
import com.join.back.config.ApiError;

/**
 * Companions found for events. A match is not a chat yet: POST /request asks "пойдём вместе?",
 * the other side answers with /accept (the chat opens) or /decline.
 */
@Tag(name = ApiDocs.MATCHES)
@RestController
@RequestMapping("/api/matches")
public class MatchController extends BaseAuthController {

    private final ContactRequestService contactRequestService;

    public MatchController(UserRepository userRepository, ContactRequestService contactRequestService) {
        super(userRepository);
        this.contactRequestService = contactRequestService;
    }

    // GET /api/matches — found companions and requests that are not a chat yet
    @Operation(summary = "Найденные напарники",
            description = "Совпадения, по которым ещё нет чата: `NEW` — никто не позвал, `REQUESTED` — приглашение "
                    + "отправлено (`requestedByMe` — кем).")
    @GetMapping
    public ResponseEntity<List<MatchResponse>> getMatches() {
        return ResponseEntity.ok(contactRequestService.getPending(requireCurrentUserId()));
    }

    @Operation(summary = "Позвать пойти вместе",
            description = "Второй получит приглашение в приложении и сообщение бота с кнопками «Пойдём» / «Не в "
                    + "этот раз». Чата ещё нет.")
    @ApiError(code = "409", description = "Второй уже ответил на приглашение")
    @PostMapping("/{id}/request")
    public ResponseEntity<MatchResponse> request(@PathVariable Long id) {
        return ResponseEntity.ok(contactRequestService.request(id, requireCurrentUserId()));
    }

    @Operation(summary = "Ответить «Пойдём»",
            description = "Открывает чат — его id в `chatId`; пригласившему приходит сообщение бота.")
    @ApiError(code = "409", description = "Приглашения от этого человека нет")
    @PostMapping("/{id}/accept")
    public ResponseEntity<MatchResponse> accept(@PathVariable Long id) {
        return ResponseEntity.ok(contactRequestService.accept(id, requireCurrentUserId()));
    }

    @Operation(summary = "Ответить «Не в этот раз»",
            description = "Отказ не сообщается второму, чат не открывается.")
    @ApiError(code = "409", description = "Чат уже открыт")
    @PostMapping("/{id}/decline")
    public ResponseEntity<MatchResponse> decline(@PathVariable Long id) {
        return ResponseEntity.ok(contactRequestService.decline(id, requireCurrentUserId()));
    }
}
