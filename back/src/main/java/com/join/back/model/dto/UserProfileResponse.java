package com.join.back.model.dto;

import com.join.back.model.entity.Gender;

import java.util.List;

public record UserProfileResponse(
        Long id,
        Long maxId,
        String email,
        String city,
        String firstName,
        String lastName,
        Gender gender,
        Integer age,
        String photo,
        List<String> interests,
        String telegramChannel,
        String status,
        String bio,
        Long universityId,
        String universityName,
        /** Whether email + password sign-in is set up (the email alone may come from the profile). */
        boolean hasPassword
) {
}
