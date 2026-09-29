package com.join.back.model.dto.auth;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

/** Profile for an account created through "Войти через MAX" (no email / password). */
@Schema(description = "Профиль для аккаунта MAX, вошедшего через «Войти через MAX»")
public record MaxRegisterRequest(
        @Schema(description = "Имя", example = "Аня")
        @NotBlank String firstName,
        @Schema(description = "Возраст, от 14 лет", example = "20")
        @NotNull @Min(value = 14, message = "JOIN доступен с 14 лет") @Max(150) Integer age,
        @Schema(description = "Пол: MALE или FEMALE", example = "FEMALE")
        @NotNull String gender,
        @Schema(description = "Город", example = "Казань")
        @NotBlank String city,
        @Schema(description = "Вуз, необязательно")
        Long universityId,
        @Schema(description = "Интересы — категории событий")
        List<String> interests,
        @Schema(description = "Согласие с политикой конфиденциальности", example = "true")
        @AssertTrue(message = "Нужно согласие на обработку персональных данных") boolean personalDataConsent
) {
}
