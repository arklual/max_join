package com.join.back.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateFriendGroupRequest(
        @NotBlank @Size(max = 100) String name,
        @Min(2) @Max(10) Integer maxSize
) {
}
