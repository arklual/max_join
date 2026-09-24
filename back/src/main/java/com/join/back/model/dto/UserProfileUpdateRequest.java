package com.join.back.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.List;

public record UserProfileUpdateRequest(
        String firstName,

        String lastName,

        @Email
        String email,

        String city,

        String gender,

        @Min(1)
        @Max(150)
        Integer age,

        List<String> interests,

        String telegramChannel,

        String status,

        String bio,

        Long universityId
) {
}
