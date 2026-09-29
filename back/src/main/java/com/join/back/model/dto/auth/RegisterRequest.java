package com.join.back.model.dto.auth;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8) String password,
        @NotBlank String firstName,
        @NotNull @Min(value = 14, message = "JOIN доступен с 14 лет") @Max(150) Integer age,
        @NotNull String gender,
        @NotBlank String city,
        Long universityId,
        List<String> interests,
        @AssertTrue(message = "Нужно согласие на обработку персональных данных") boolean personalDataConsent
) {
}
