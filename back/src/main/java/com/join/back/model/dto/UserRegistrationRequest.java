package com.join.back.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UserRegistrationRequest(
        @Email(message = "Invalid email format")
        String email,  // optional at registration, can be filled in profile

        @NotBlank
        String city,

        @NotBlank
        String firstName,

        @NotNull
        String gender,

        @NotNull
        @Min(1)
        @Max(150)
        Integer age,

        List<String> interests,

        Long universityId
) {
}
