package com.join.back.web.controller;

import com.join.back.model.dto.EventCardResponse;
import com.join.back.model.dto.LikeResultResponse;
import com.join.back.repository.UserRepository;
import com.join.back.service.LikeService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springdoc.core.annotations.ParameterObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.join.back.config.ApiDocs;
import com.join.back.config.ApiError;

@Tag(name = ApiDocs.LIKES)
@RestController
@RequestMapping("/api/events")
public class LikeController extends BaseAuthController {

    private final LikeService likeService;

    public LikeController(UserRepository userRepository, LikeService likeService) {
        super(userRepository);
        this.likeService = likeService;
    }

    @Operation(summary = "Сохранить событие",
            description = "Если событие уже сохранил подходящий человек, сразу создаётся совпадение: оно вернётся в "
                    + "`matches`, обоим придёт уведомление и сообщение бота. Повторный вызов ничего не меняет.")
    @ApiError(code = "409", description = "Лимит: не больше 10 лайков в сутки")
    @PostMapping("/{id}/like")
    public ResponseEntity<LikeResultResponse> likeEvent(@PathVariable Long id) {
        Long userId = requireCurrentUserId();
        return ResponseEntity.ok(likeService.like(userId, id));
    }

    @Operation(summary = "Убрать из сохранённых",
            description = "Совпадения и чаты по этому событию удаляются.")
    @DeleteMapping("/{id}/like")
    public ResponseEntity<Void> unlikeEvent(@PathVariable Long id) {
        Long userId = requireCurrentUserId();
        likeService.unlike(userId, id);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Мои сохранённые события")
    @GetMapping("/liked")
    public ResponseEntity<Page<EventCardResponse>> getLikedEvents(@ParameterObject Pageable pageable) {
        Long userId = requireCurrentUserId();
        return ResponseEntity.ok(likeService.getLikedEvents(userId, pageable));
    }
}
