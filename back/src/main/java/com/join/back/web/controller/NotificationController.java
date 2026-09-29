package com.join.back.web.controller;

import com.join.back.model.dto.NotificationResponse;
import com.join.back.repository.UserRepository;
import com.join.back.service.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import org.springdoc.core.annotations.ParameterObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.join.back.config.ApiDocs;

@Tag(name = ApiDocs.NOTIFICATIONS)
@RestController
@RequestMapping("/api/notifications")
public class NotificationController extends BaseAuthController {

    private final NotificationService notificationService;

    public NotificationController(UserRepository userRepository, NotificationService notificationService) {
        super(userRepository);
        this.notificationService = notificationService;
    }

    @Operation(summary = "Уведомления",
            description = "Новые совпадения, приглашения, сообщения — то же, что присылает бот.")
    @GetMapping
    public ResponseEntity<Page<NotificationResponse>> getNotifications(@ParameterObject Pageable pageable) {
        Long userId = requireCurrentUserId();
        return ResponseEntity.ok(notificationService.getNotifications(userId, pageable));
    }

    @Operation(summary = "Число непрочитанных")
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount() {
        Long userId = requireCurrentUserId();
        return ResponseEntity.ok(Map.of("count", notificationService.countUnread(userId)));
    }

    @Operation(summary = "Прочитать все")
    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllRead() {
        Long userId = requireCurrentUserId();
        notificationService.markAllRead(userId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Отметить уведомление прочитанным")
    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable Long id) {
        Long userId = requireCurrentUserId();
        notificationService.markRead(userId, id);
        return ResponseEntity.ok().build();
    }
}
