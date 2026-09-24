package com.join.back.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateGroupRequest(
        @Size(max = 100) String title,
        @Size(max = 500) String description,
        @NotNull @Min(3) @Max(20) Integer maxSize
) {
}
