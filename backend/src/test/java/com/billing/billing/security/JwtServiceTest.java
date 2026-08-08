package com.billing.billing.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.billing.billing.model.Role;
import com.billing.billing.model.Store;
import com.billing.billing.model.User;
import com.billing.billing.support.TestEntities;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-key-that-is-long-enough-for-hs256";

    private final JwtService jwtService = new JwtService(SECRET, 60_000L);

    private static User user() {
        Store store = TestEntities.store(3L, "Kirana Mart");
        return TestEntities.user(11L, "owner@example.com", "hashed", Role.OWNER, store);
    }

    @Test
    void generatedTokenCarriesIdentityAndTenantClaims() {
        Claims claims = jwtService.parseClaims(jwtService.generateToken(user()));

        assertThat(claims.getSubject()).isEqualTo("owner@example.com");
        assertThat(claims.get("role", String.class)).isEqualTo("OWNER");
        assertThat(claims.get("userId", Long.class)).isEqualTo(11L);
        assertThat(claims.get("storeId", Long.class)).isEqualTo(3L);
    }

    @Test
    void generatedTokenExpiresAfterTheConfiguredLifetime() {
        Claims claims = jwtService.parseClaims(jwtService.generateToken(user()));

        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
        assertThat(claims.getExpiration().getTime() - claims.getIssuedAt().getTime()).isEqualTo(60_000L);
    }

    @Test
    void parseClaimsRejectsATokenSignedWithAnotherSecret() {
        String foreignToken = new JwtService("a-totally-different-secret-key-of-sufficient-length", 60_000L)
                .generateToken(user());

        assertThatThrownBy(() -> jwtService.parseClaims(foreignToken)).isInstanceOf(JwtException.class);
    }

    @Test
    void parseClaimsRejectsAnExpiredToken() {
        String expiredToken = new JwtService(SECRET, -1_000L).generateToken(user());

        assertThatThrownBy(() -> jwtService.parseClaims(expiredToken)).isInstanceOf(JwtException.class);
    }

    @Test
    void parseClaimsRejectsGarbage() {
        assertThatThrownBy(() -> jwtService.parseClaims("not-a-jwt")).isInstanceOf(JwtException.class);
    }
}
