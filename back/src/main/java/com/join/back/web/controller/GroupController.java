package com.join.back.web.controller;

import com.join.back.model.dto.CreateGroupRequest;
import com.join.back.model.dto.GroupInviteCandidateResponse;
import com.join.back.model.dto.GroupResponse;
import com.join.back.model.dto.GroupStatsResponse;
import com.join.back.model.dto.InviteToGroupRequest;
import com.join.back.model.dto.JoinGroupResponse;
import com.join.back.model.dto.LeaveGroupResponse;
import com.join.back.repository.UserRepository;
import com.join.back.service.GroupInviteService;
import com.join.back.service.GroupService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class GroupController extends BaseAuthController {

    private final GroupService groupService;
    private final GroupInviteService groupInviteService;

    public GroupController(UserRepository userRepository, GroupService groupService,
                           GroupInviteService groupInviteService) {
        super(userRepository);
        this.groupService = groupService;
        this.groupInviteService = groupInviteService;
    }

    // POST /api/events/{eventId}/groups — create group
    @PostMapping("/api/events/{eventId}/groups")
    public ResponseEntity<GroupResponse> createGroup(
            @PathVariable Long eventId,
            @Valid @RequestBody CreateGroupRequest request
    ) {
        Long userId = requireCurrentUserId();
        GroupResponse response = groupService.createGroup(userId, eventId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // GET /api/events/{eventId}/groups — list open groups for event
    @GetMapping("/api/events/{eventId}/groups")
    public ResponseEntity<Page<GroupResponse>> getGroupsForEvent(
            @PathVariable Long eventId,
            Pageable pageable
    ) {
        return ResponseEntity.ok(groupService.getGroupsForEvent(eventId, pageable));
    }

    // GET /api/events/{eventId}/groups/stats — stats for event card
    @GetMapping("/api/events/{eventId}/groups/stats")
    public ResponseEntity<GroupStatsResponse> getGroupStats(@PathVariable Long eventId) {
        return ResponseEntity.ok(groupService.getGroupStats(eventId));
    }

    // GET /api/groups/{id} — group details
    @GetMapping("/api/groups/{id}")
    public ResponseEntity<GroupResponse> getGroup(@PathVariable Long id) {
        return ResponseEntity.ok(groupService.getGroupById(id));
    }

    // POST /api/groups/{id}/join — join group
    @PostMapping("/api/groups/{id}/join")
    public ResponseEntity<JoinGroupResponse> joinGroup(@PathVariable Long id) {
        Long userId = requireCurrentUserId();
        return ResponseEntity.ok(groupService.joinGroup(userId, id));
    }

    // POST /api/groups/{id}/leave — leave group
    @PostMapping("/api/groups/{id}/leave")
    public ResponseEntity<LeaveGroupResponse> leaveGroup(@PathVariable Long id) {
        Long userId = requireCurrentUserId();
        return ResponseEntity.ok(groupService.leaveGroup(userId, id));
    }

    // GET /api/groups/my — my groups
    @GetMapping("/api/groups/my")
    public ResponseEntity<Page<GroupResponse>> getMyGroups(Pageable pageable) {
        Long userId = requireCurrentUserId();
        return ResponseEntity.ok(groupService.getMyGroups(userId, pageable));
    }

    // GET /api/groups/{id}/invite-candidates — people I know who can be invited
    @GetMapping("/api/groups/{id}/invite-candidates")
    public ResponseEntity<List<GroupInviteCandidateResponse>> getInviteCandidates(@PathVariable Long id) {
        Long userId = requireCurrentUserId();
        return ResponseEntity.ok(groupInviteService.getCandidates(userId, id));
    }

    // POST /api/groups/{id}/invite — invite a friend into the group
    @PostMapping("/api/groups/{id}/invite")
    public ResponseEntity<GroupInviteCandidateResponse> invite(
            @PathVariable Long id,
            @Valid @RequestBody InviteToGroupRequest request
    ) {
        Long userId = requireCurrentUserId();
        return ResponseEntity.ok(groupInviteService.invite(userId, id, request.userId()));
    }
}
