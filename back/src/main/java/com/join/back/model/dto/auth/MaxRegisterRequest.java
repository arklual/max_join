package com.join.back.model.dto.auth;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Profile for an account created through "Войти через MAX" (no email / password). */
public record MaxRegisterRequest(
        @NotBlank String firstName,
        @NotNull @Min(value = 14, message = "JOIN доступен с 14 лет") @Max(150) Integer age,
        @NotNull String gender,
        @NotBlank String city,
        Long universityId,
        List<String> interests,
        @AssertTrue(message = "Нужно согласие на обработку персональных данных") boolean personalDataConsent
) {
}
