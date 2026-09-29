package com.join.back.model.dto;

import com.join.back.model.entity.Gender;

import java.util.List;

public record UserResponse(
        Long id,
        Long maxId,
        String email,
        String city,
        String firstName,
        Gender gender,
        Integer age,
        List<String> interests,
        String photo,
        Long universityId,
        String universityName,
        /** Agreed to the privacy policy; false for accounts created before consent was asked. */
        boolean personalDataConsent
) {
}
