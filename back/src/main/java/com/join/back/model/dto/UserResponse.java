package com.join.back.model.dto;

import com.join.back.model.entity.Gender;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Текущий пользователь")
public record UserResponse(
        @Schema(description = "Id пользователя в JOIN", example = "42")
        Long id,
        @Schema(description = "Id в MAX, если вход через MAX")
        Long maxId,
        @Schema(description = "Email, если задан", example = "anya@example.com")
        String email,
        @Schema(description = "Город", example = "Казань")
        String city,
        @Schema(description = "Имя", example = "Аня")
        String firstName,
        @Schema(description = "Пол")
        Gender gender,
        @Schema(description = "Возраст", example = "20")
        Integer age,
        @Schema(description = "Интересы — категории событий")
        List<String> interests,
        @Schema(description = "Путь к фото относительно домена", example = "/uploads/photos/anya.jpg")
        String photo,
        @Schema(description = "Id вуза")
        Long universityId,
        @Schema(description = "Вуз", example = "Казанский федеральный университет")
        String universityName,
        /** Agreed to the privacy policy; false for accounts created before consent was asked. */
        @Schema(description = "Принята ли политика конфиденциальности; false — вызвать POST /api/users/me/consent", example = "true")
        boolean personalDataConsent
) {
}
