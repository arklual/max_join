package com.join.back.model.dto;

import com.join.back.model.entity.Gender;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Профиль. В чужом профиле нет email, maxId и hasPassword")
public record UserProfileResponse(
        @Schema(description = "Id пользователя в JOIN", example = "42")
        Long id,
        @Schema(description = "Id в MAX — только в своём профиле")
        Long maxId,
        @Schema(description = "Email — только в своём профиле")
        String email,
        @Schema(description = "Город", example = "Казань")
        String city,
        @Schema(description = "Имя", example = "Аня")
        String firstName,
        @Schema(description = "Фамилия")
        String lastName,
        @Schema(description = "Пол")
        Gender gender,
        @Schema(description = "Возраст", example = "20")
        Integer age,
        @Schema(description = "Путь к фото", example = "/uploads/photos/anya.jpg")
        String photo,
        @Schema(description = "Интересы")
        List<String> interests,
        @Schema(description = "Канал в Telegram, если указан")
        String telegramChannel,
        @Schema(description = "Статус", example = "Ищу компанию на выставки")
        String status,
        @Schema(description = "О себе", example = "Учусь на дизайнера, люблю театр")
        String bio,
        @Schema(description = "Id вуза")
        Long universityId,
        @Schema(description = "Вуз", example = "Казанский федеральный университет")
        String universityName,
        /** Whether email + password sign-in is set up (the email alone may come from the profile). */
        @Schema(description = "Настроен ли вход по email и паролю — только в своём профиле")
        boolean hasPassword
) {

    /** What other users see: no email, MAX id or sign-in details. */
    public UserProfileResponse forOthers() {
        return new UserProfileResponse(id, null, null, city, firstName, lastName, gender, age, photo, interests,
                telegramChannel, status, bio, universityId, universityName, false);
    }
}
