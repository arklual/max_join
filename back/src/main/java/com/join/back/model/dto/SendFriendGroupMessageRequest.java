package com.join.back.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendFriendGroupMessageRequest(
        @NotBlank @Size(max = 2000) String text
) {
}
