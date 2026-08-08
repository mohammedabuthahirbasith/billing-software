package com.billing.billing.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,
        // max 72: BCrypt hashes only the first 72 bytes and silently ignores the rest, so anything
        // longer would authenticate on a truncated prefix — reject it instead of accepting a
        // password that is not really the one the user typed.
        @NotBlank @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters") String password,
        @NotBlank @Size(max = 255) String storeName
) {}