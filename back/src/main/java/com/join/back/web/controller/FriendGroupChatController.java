package com.join.back.web.controller;

import com.join.back.model.dto.FriendGroupChatMessageResponse;
import com.join.back.model.dto.SendFriendGroupMessageRequest;
import com.join.back.repository.UserRepository;
import com.join.back.service.FriendGroupChatService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/friend-groups/{groupId}/messages")
public class FriendGroupChatController extends BaseAuthController {

    private final FriendGroupChatService chatService;

    public FriendGroupChatController(UserRepository userRepository, FriendGroupChatService chatService) {
        super(userRepository);
        this.chatService = chatService;
    }

    @GetMapping
    public ResponseEntity<Page<FriendGroupChatMessageResponse>> getMessages(@PathVariable Long groupId,
                                                                            Pageable pageable) {
        Long userId = requireCurrentUserId();
        return ResponseEntity.ok(chatService.getMessages(groupId, userId, pageable));
    }

    @PostMapping
    public ResponseEntity<FriendGroupChatMessageResponse> send(@PathVariable Long groupId,
                                                               @Valid @RequestBody SendFriendGroupMessageRequest request) {
        Long userId = requireCurrentUserId();
        FriendGroupChatMessageResponse response = chatService.send(groupId, userId, request.text());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
