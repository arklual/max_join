package com.join.back.web.controller;

import com.join.back.model.dto.ChatMessageResponse;
import com.join.back.model.dto.ChatResponse;
import com.join.back.model.dto.IceBreakerResponse;
import com.join.back.model.dto.OutingStateResponse;
import com.join.back.model.dto.SendMessageRequest;
import com.join.back.repository.UserRepository;
import com.join.back.service.ChatService;
import com.join.back.service.IceBreakerService;
import com.join.back.service.OutingFeedbackService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/chats")
public class ChatController extends BaseAuthController {

    private final ChatService chatService;
    private final IceBreakerService iceBreakerService;
    private final OutingFeedbackService outingFeedbackService;

    public ChatController(UserRepository userRepository, ChatService chatService, IceBreakerService iceBreakerService,
                          OutingFeedbackService outingFeedbackService) {
        super(userRepository);
        this.chatService = chatService;
        this.iceBreakerService = iceBreakerService;
        this.outingFeedbackService = outingFeedbackService;
    }

    // GET /api/chats/{id}/outing — "сходили вместе?" state of the pair
    @GetMapping("/{id}/outing")
    public ResponseEntity<OutingStateResponse> getOuting(@PathVariable Long id) {
        return ResponseEntity.ok(outingFeedbackService.getState(id, requireCurrentUserId()));
    }

    // POST /api/chats/{id}/outing/went {"went": true|false}
    @PostMapping("/{id}/outing/went")
    public ResponseEntity<OutingStateResponse> answerWent(@PathVariable Long id, @Valid @RequestBody WentRequest request) {
        return ResponseEntity.ok(outingFeedbackService.answer(id, requireCurrentUserId(), request.went()));
    }

    public record WentRequest(@jakarta.validation.constraints.NotNull Boolean went) {
    }

    @GetMapping
    public ResponseEntity<List<ChatResponse>> getChats() {
        Long userId = requireCurrentUserId();
        return ResponseEntity.ok(chatService.getChats(userId));
    }

    @GetMapping("/{id}/messages")
    public ResponseEntity<Page<ChatMessageResponse>> getMessages(
            @PathVariable Long id,
            Pageable pageable
    ) {
        Long userId = requireCurrentUserId();
        return ResponseEntity.ok(chatService.getMessages(id, userId, pageable));
    }

    @PostMapping("/{id}/messages")
    public ResponseEntity<ChatMessageResponse> sendMessage(
            @PathVariable Long id,
            @Valid @RequestBody SendMessageRequest request
    ) {
        Long userId = requireCurrentUserId();
        return ResponseEntity.ok(chatService.sendMessage(id, userId, request.text()));
    }

    @GetMapping("/{id}/icebreakers")
    public ResponseEntity<IceBreakerResponse> getIceBreakers(@PathVariable Long id) {
        Long userId = requireCurrentUserId();
        return ResponseEntity.ok(iceBreakerService.getIceBreakers(id, userId));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable Long id) {
        Long userId = requireCurrentUserId();
        chatService.markAsRead(id, userId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/pin")
    public ResponseEntity<Void> pinChat(@PathVariable Long id) {
        chatService.setPinned(id, requireCurrentUserId(), true);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/pin")
    public ResponseEntity<Void> unpinChat(@PathVariable Long id) {
        chatService.setPinned(id, requireCurrentUserId(), false);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteChat(@PathVariable Long id) {
        Long userId = requireCurrentUserId();
        chatService.deleteChat(id, userId);
        return ResponseEntity.noContent().build();
    }
}
