package com.join.back.model.dto;

import com.join.back.model.entity.Gender;

public record SearchCriteriaRequest(
        Integer preferredAgeMin,
        Integer preferredAgeMax,
        Gender preferredGender,
        Long preferredUniversityId
) {
}
