package com.billing.billing.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.billing.billing.model.Role;

public record CreateUserRequest(
        @NotBlank @Email @Size(max = 255) String email,
        // Same 8..72 bound as RegisterRequest — see the note there on BCrypt's 72-byte truncation.
        @NotBlank @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters") String password,
        @NotNull Role role
) {}