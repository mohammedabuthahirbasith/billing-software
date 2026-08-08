package com.billing.billing.support;

import com.billing.billing.model.Role;
import com.billing.billing.security.JwtService;

// Controller slice tests drive requests through the real filter chain (JwtAuthenticationFilter +
// SecurityConfig), so they need genuine signed tokens rather than a stubbed SecurityContext.
public final class ApiTokens {

    public static final String SECRET = "unit-test-secret-key-that-is-long-enough-for-hs256";
    public static final long STORE_ID = 3L;

    private static final JwtService JWT_SERVICE = new JwtService(SECRET, 3_600_000L);

    private ApiTokens() {}

    public static String bearer(Role role) {
        return "Bearer " + JWT_SERVICE.generateToken(TestEntities.user(11L, role.name().toLowerCase() + "@example.com",
                "hashed", role, TestEntities.store(STORE_ID, "Kirana Mart")));
    }
}
