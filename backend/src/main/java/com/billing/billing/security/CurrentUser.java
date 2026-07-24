package com.billing.billing.security;

import org.springframework.security.core.context.SecurityContextHolder;

// Static helper so any service method can reach the current request's store/user with zero extra
// DB queries and no controller signature changes anywhere — the identity is already fully decoded
// onto the Authentication's principal by JwtAuthenticationFilter.
public final class CurrentUser {

    private CurrentUser() {}

    public static AuthenticatedUser get() {
        return (AuthenticatedUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}