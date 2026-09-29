package com.join.back.web.controller;

import com.join.back.model.dto.BlockStatusResponse;
import com.join.back.model.dto.BlockedUserResponse;
import com.join.back.repository.UserRepository;
import com.join.back.service.UserBlockService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.join.back.config.ApiDocs;

@Tag(name = ApiDocs.BLOCKS)
@RestController
public class UserBlockController extends BaseAuthController {

    private final UserBlockService userBlockService;

    public UserBlockController(UserRepository userRepository, UserBlockService userBlockService) {
        super(userRepository);
        this.userBlockService = userBlockService;
    }

    // GET /api/users/me/blocked — my black list
    @Operation(summary = "Мой чёрный список")
    @GetMapping("/api/users/me/blocked")
    public ResponseEntity<List<BlockedUserResponse>> getBlocked() {
        return ResponseEntity.ok(userBlockService.getBlockedUsers(requireCurrentUserId()));
    }

    // GET /api/users/{id}/block — block relation with a user
    @Operation(summary = "Блокировка между мной и пользователем",
            description = "`BLOCKED_BY_ME`, `BLOCKED_ME` или ничего.")
    @GetMapping("/api/users/{id}/block")
    public ResponseEntity<BlockStatusResponse> getStatus(@PathVariable Long id) {
        return ResponseEntity.ok(userBlockService.status(requireCurrentUserId(), id));
    }

    // POST /api/users/{id}/block — add to black list
    @Operation(summary = "Заблокировать",
            description = "Совпадения и переписка с этим человеком прекращаются, в его компанию не вступить.")
    @PostMapping("/api/users/{id}/block")
    public ResponseEntity<BlockStatusResponse> block(@PathVariable Long id) {
        return ResponseEntity.ok(userBlockService.block(requireCurrentUserId(), id));
    }

    // DELETE /api/users/{id}/block — remove from black list
    @Operation(summary = "Разблокировать")
    @DeleteMapping("/api/users/{id}/block")
    public ResponseEntity<BlockStatusResponse> unblock(@PathVariable Long id) {
        return ResponseEntity.ok(userBlockService.unblock(requireCurrentUserId(), id));
    }
}
