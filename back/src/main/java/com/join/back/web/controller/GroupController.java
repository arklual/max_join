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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springdoc.core.annotations.ParameterObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.join.back.config.ApiDocs;
import com.join.back.config.ApiError;

@Tag(name = ApiDocs.GROUPS)
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
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Собрать компанию на событие",
            description = "Создатель сразу становится участником, у компании появляется общий чат.")
    @ApiError(code = "409", description = "Пользователь уже в компании на это событие")
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
    @Operation(summary = "Открытые компании события",
            description = "Подростки видят только компании сверстников, взрослые — взрослых.")
    @GetMapping("/api/events/{eventId}/groups")
    public ResponseEntity<Page<GroupResponse>> getGroupsForEvent(
            @PathVariable Long eventId,
            @ParameterObject Pageable pageable
    ) {
        return ResponseEntity.ok(groupService.getGroupsForEvent(eventId, requireCurrentUserId(), pageable));
    }

    // GET /api/events/{eventId}/groups/stats — stats for event card
    @Operation(summary = "Сколько компаний собирается",
            description = "Число открытых компаний и участников в них — для карточки события.")
    @GetMapping("/api/events/{eventId}/groups/stats")
    public ResponseEntity<GroupStatsResponse> getGroupStats(@PathVariable Long eventId) {
        return ResponseEntity.ok(groupService.getGroupStats(eventId));
    }

    // GET /api/groups/{id} — group details
    @Operation(summary = "Компания")
    @GetMapping("/api/groups/{id}")
    public ResponseEntity<GroupResponse> getGroup(@PathVariable Long id) {
        return ResponseEntity.ok(groupService.getGroupById(id, requireCurrentUserId()));
    }

    // POST /api/groups/{id}/join — join group
    @Operation(summary = "Вступить в компанию")
    @ApiError(code = "409", description = "Набор закрыт, пользователь уже в компании на это событие, в компании кто-то из чёрного списка или участники другой возрастной группы")
    @PostMapping("/api/groups/{id}/join")
    public ResponseEntity<JoinGroupResponse> joinGroup(@PathVariable Long id) {
        Long userId = requireCurrentUserId();
        return ResponseEntity.ok(groupService.joinGroup(userId, id));
    }

    // POST /api/groups/{id}/leave — leave group
    @Operation(summary = "Выйти из компании",
            description = "Если выходит создатель, роль переходит следующему участнику.")
    @PostMapping("/api/groups/{id}/leave")
    public ResponseEntity<LeaveGroupResponse> leaveGroup(@PathVariable Long id) {
        Long userId = requireCurrentUserId();
        return ResponseEntity.ok(groupService.leaveGroup(userId, id));
    }

    // GET /api/groups/my — my groups
    @Operation(summary = "Мои компании")
    @GetMapping("/api/groups/my")
    public ResponseEntity<Page<GroupResponse>> getMyGroups(@ParameterObject Pageable pageable) {
        Long userId = requireCurrentUserId();
        return ResponseEntity.ok(groupService.getMyGroups(userId, pageable));
    }

    // GET /api/groups/{id}/invite-candidates — people I know who can be invited
    @Operation(summary = "Кого можно позвать",
            description = "Напарники по личным чатам и участники групп друзей, с пометкой, можно ли их позвать.")
    @GetMapping("/api/groups/{id}/invite-candidates")
    public ResponseEntity<List<GroupInviteCandidateResponse>> getInviteCandidates(@PathVariable Long id) {
        Long userId = requireCurrentUserId();
        return ResponseEntity.ok(groupInviteService.getCandidates(userId, id));
    }

    // POST /api/groups/{id}/invite — invite a friend into the group
    @Operation(summary = "Позвать в компанию",
            description = "Приглашённый получит уведомление и сообщение бота.")
    @ApiError(code = "409", description = "Мест нет, человек не из ваших контактов, уже идёт с компанией или уже приглашён")
    @PostMapping("/api/groups/{id}/invite")
    public ResponseEntity<GroupInviteCandidateResponse> invite(
            @PathVariable Long id,
            @Valid @RequestBody InviteToGroupRequest request
    ) {
        Long userId = requireCurrentUserId();
        return ResponseEntity.ok(groupInviteService.invite(userId, id, request.userId()));
    }
}
