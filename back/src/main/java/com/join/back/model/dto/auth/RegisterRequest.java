package com.join.back.model.dto.auth;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Регистрация по email")
public record RegisterRequest(
        @Schema(description = "Email для входа", example = "anya@example.com")
        @NotBlank @Email String email,
        @Schema(description = "Пароль, не короче 8 символов", example = "password123")
        @NotBlank @Size(min = 8) String password,
        @Schema(description = "Имя", example = "Аня")
        @NotBlank String firstName,
        @Schema(description = "Возраст, от 14 лет", example = "20")
        @NotNull @Min(value = 14, message = "JOIN доступен с 14 лет") @Max(150) Integer age,
        @Schema(description = "Пол: MALE или FEMALE", example = "FEMALE")
        @NotNull String gender,
        @Schema(description = "Город из GET /api/cities", example = "Казань")
        @NotBlank String city,
        @Schema(description = "Вуз из GET /api/universities, необязательно")
        Long universityId,
        @Schema(description = "Интересы — категории событий: THEATER, MUSIC, ART, CINEMA, SPORT, EXCURSION, FESTIVAL, MASTER_CLASS, CAREER")
        List<String> interests,
        @Schema(description = "Согласие с политикой конфиденциальности; без него регистрация отклоняется", example = "true")
        @AssertTrue(message = "Нужно согласие на обработку персональных данных") boolean personalDataConsent
) {
}
