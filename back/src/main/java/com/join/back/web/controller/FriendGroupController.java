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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.join.back.config.ApiDocs;
import com.join.back.config.ApiError;

@Tag(name = ApiDocs.FRIEND_GROUPS)
@RestController
@RequestMapping("/api/friend-groups")
public class FriendGroupController extends BaseAuthController {

    private final FriendGroupService friendGroupService;

    public FriendGroupController(UserRepository userRepository, FriendGroupService friendGroupService) {
        super(userRepository);
        this.friendGroupService = friendGroupService;
    }

    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Создать группу друзей",
            description = "В ответе код приглашения: по нему, ссылке или QR-коду вступают друзья.")
    @PostMapping
    public ResponseEntity<FriendGroupResponse> create(@Valid @RequestBody CreateFriendGroupRequest request) {
        Long userId = requireCurrentUserId();
        FriendGroupResponse response = friendGroupService.create(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Мои группы друзей")
    @GetMapping
    public ResponseEntity<Page<FriendGroupResponse>> getMyGroups(@ParameterObject Pageable pageable) {
        Long userId = requireCurrentUserId();
        return ResponseEntity.ok(friendGroupService.getMyGroups(userId, pageable));
    }

    @Operation(summary = "Группа друзей",
            description = "Участники и код приглашения.")
    @GetMapping("/{id}")
    public ResponseEntity<FriendGroupResponse> getById(@PathVariable Long id) {
        Long userId = requireCurrentUserId();
        return ResponseEntity.ok(friendGroupService.getById(id, userId));
    }

    @Operation(summary = "Вступить по коду приглашения",
            description = "Работает и там, где нет сканера QR-кода, например в веб-версии MAX.")
    @ApiError(code = "404", description = "Нет группы с таким кодом")
    @ApiError(code = "409", description = "В группе больше нет мест или пользователь уже в ней")
    @PostMapping("/join")
    public ResponseEntity<JoinFriendGroupResponse> join(@Valid @RequestBody JoinFriendGroupRequest request) {
        Long userId = requireCurrentUserId();
        return ResponseEntity.ok(friendGroupService.joinByInviteCode(userId, request.inviteCode()));
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Выйти из группы друзей",
            description = "Если выходит создатель, роль переходит следующему участнику; группа без участников "
                    + "архивируется.")
    @DeleteMapping("/{id}/leave")
    public ResponseEntity<Void> leave(@PathVariable Long id) {
        Long userId = requireCurrentUserId();
        friendGroupService.leave(userId, id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Общие события группы",
            description = "Предстоящие события, которые сохранили все участники.")
    @GetMapping("/{id}/common-events")
    public ResponseEntity<Page<EventCardResponse>> getCommonEvents(@PathVariable Long id, @ParameterObject Pageable pageable) {
        Long userId = requireCurrentUserId();
        return ResponseEntity.ok(friendGroupService.getCommonEvents(id, userId, pageable));
    }
}
