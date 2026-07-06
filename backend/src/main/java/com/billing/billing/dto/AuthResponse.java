package com.billing.billing.dto;

import com.billing.billing.model.Role;

public record AuthResponse(String token, String email, Role role) {}