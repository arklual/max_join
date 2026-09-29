package com.join.back.web.controller;

import com.join.back.model.dto.CreateFriendGroupRequest;
import com.join.back.model.dto.EventCardResponse;
import com.join.back.model.dto.FriendGroupResponse;
import com.join.back.model.dto.JoinFriendGroupRequest;
import com.join.back.model.dto.JoinFriendGroupResponse;
import com.join.back.repository.UserRepository;
import com.join.back.service.FriendGroupService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springdoc.core.annotations.ParameterObject;

@RestController
@RequestMapping("/api/friend-groups")
public class FriendGroupController extends BaseAuthController {

    private final FriendGroupService friendGroupService;

    public FriendGroupController(UserRepository userRepository, FriendGroupService friendGroupService) {
        super(userRepository);
        this.friendGroupService = friendGroupService;
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public ResponseEntity<FriendGroupResponse> create(@Valid @RequestBody CreateFriendGroupRequest request) {
        Long userId = requireCurrentUserId();
        FriendGroupResponse response = friendGroupService.create(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<Page<FriendGroupResponse>> getMyGroups(@ParameterObject Pageable pageable) {
        Long userId = requireCurrentUserId();
        return ResponseEntity.ok(friendGroupService.getMyGroups(userId, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<FriendGroupResponse> getById(@PathVariable Long id) {
        Long userId = requireCurrentUserId();
        return ResponseEntity.ok(friendGroupService.getById(id, userId));
    }

    @PostMapping("/join")
    public ResponseEntity<JoinFriendGroupResponse> join(@Valid @RequestBody JoinFriendGroupRequest request) {
        Long userId = requireCurrentUserId();
        return ResponseEntity.ok(friendGroupService.joinByInviteCode(userId, request.inviteCode()));
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{id}/leave")
    public ResponseEntity<Void> leave(@PathVariable Long id) {
        Long userId = requireCurrentUserId();
        friendGroupService.leave(userId, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/common-events")
    public ResponseEntity<Page<EventCardResponse>> getCommonEvents(@PathVariable Long id, @ParameterObject Pageable pageable) {
        Long userId = requireCurrentUserId();
        return ResponseEntity.ok(friendGroupService.getCommonEvents(id, userId, pageable));
    }
}
