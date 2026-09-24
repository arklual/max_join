package com.join.back.model.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Credentials of an existing JOIN account (created on the native mobile client)
 * that the caller wants to attach their MAX identity to, so the same
 * account is usable from both the Mini App and the Android app.
 */
public record LinkMaxRequest(
        @NotBlank @Email String email,
        @NotBlank String password
) {
}
