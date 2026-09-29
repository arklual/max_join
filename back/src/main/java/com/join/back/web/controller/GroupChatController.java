package com.join.back.web.controller;

import com.join.back.model.dto.GroupChatMessageResponse;
import com.join.back.model.dto.IceBreakerResponse;
import com.join.back.model.dto.SendGroupMessageRequest;
import com.join.back.repository.UserRepository;
import com.join.back.service.GroupService;
import com.join.back.service.IceBreakerService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springdoc.core.annotations.ParameterObject;

@RestController
@RequestMapping("/api/group-chats")
public class GroupChatController extends BaseAuthController {

    private final GroupService groupService;
    private final IceBreakerService iceBreakerService;

    public GroupChatController(UserRepository userRepository, GroupService groupService, IceBreakerService iceBreakerService) {
        super(userRepository);
        this.groupService = groupService;
        this.iceBreakerService = iceBreakerService;
    }

    @GetMapping("/{groupChatId}/messages")
    public ResponseEntity<Page<GroupChatMessageResponse>> getMessages(
            @PathVariable Long groupChatId,
            @ParameterObject Pageable pageable
    ) {
        Long userId = requireCurrentUserId();
        return ResponseEntity.ok(groupService.getGroupMessages(groupChatId, userId, pageable));
    }

    @GetMapping("/{groupChatId}/icebreakers")
    public ResponseEntity<IceBreakerResponse> getIceBreakers(@PathVariable Long groupChatId) {
        Long userId = requireCurrentUserId();
        return ResponseEntity.ok(iceBreakerService.getGroupIceBreakers(groupChatId, userId));
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/{groupChatId}/messages")
    public ResponseEntity<GroupChatMessageResponse> sendMessage(
            @PathVariable Long groupChatId,
            @Valid @RequestBody SendGroupMessageRequest request
    ) {
        Long userId = requireCurrentUserId();
        GroupChatMessageResponse response = groupService.sendGroupMessage(groupChatId, userId, request.text());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
