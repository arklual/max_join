package com.join.back.model.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LinkEmailRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8) String password
) {
}
