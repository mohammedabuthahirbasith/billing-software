package com.billing.billing.dto;

import com.billing.billing.model.Role;

public record UserResponse(Long id, String email, Role role) {}