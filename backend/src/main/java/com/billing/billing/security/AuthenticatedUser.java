package com.billing.billing.security;

import org.springframework.security.core.AuthenticatedPrincipal;

import com.billing.billing.model.Role;

// Implements AuthenticatedPrincipal (not just a bare record) so Authentication.getName() keeps
// returning the email for any caller — without this, getName() falls back to principal.toString(),
// silently returning a record dump instead of the email.
public record AuthenticatedUser(Long userId, String email, Role role, Long storeId) implements AuthenticatedPrincipal {
    @Override
    public String getName() {
        return email;
    }
}