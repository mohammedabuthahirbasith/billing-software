package com.billing.billing.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.billing.billing.model.Role;
import com.billing.billing.support.TestEntities;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

class JwtAuthenticationFilterTest {

    private static final String SECRET = "unit-test-secret-key-that-is-long-enough-for-hs256";

    private final JwtService jwtService = new JwtService(SECRET, 60_000L);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static Authentication doFilter(JwtAuthenticationFilter filter, String authHeader) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/products");
        if (authHeader != null) {
            request.addHeader("Authorization", authHeader);
        }
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        assertThat(chain.getRequest()).as("chain must always continue").isNotNull();
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private static String tokenWithoutStoreId() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes());
        Date now = new Date();
        return Jwts.builder()
                .subject("owner@example.com")
                .claim("role", "OWNER")
                .claim("userId", 11L)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 60_000L))
                .signWith(key)
                .compact();
    }

    @Test
    void validTokenAuthenticatesWithTheDecodedPrincipalAndRoleAuthority() throws Exception {
        String token = jwtService.generateToken(TestEntities.user(11L, "owner@example.com", "hashed", Role.OWNER,
                TestEntities.store(3L, "Kirana Mart")));

        Authentication authentication = doFilter(filter, "Bearer " + token);

        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal())
                .isEqualTo(new AuthenticatedUser(11L, "owner@example.com", Role.OWNER, 3L));
        assertThat(authentication.getName()).isEqualTo("owner@example.com");
        assertThat(authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_OWNER");
    }

    @Test
    void missingAuthorizationHeaderLeavesTheRequestUnauthenticated() throws Exception {
        assertThat(doFilter(filter, null)).isNull();
    }

    @Test
    void nonBearerAuthorizationHeaderLeavesTheRequestUnauthenticated() throws Exception {
        assertThat(doFilter(filter, "Basic dXNlcjpwYXNz")).isNull();
    }

    @Test
    void invalidTokenLeavesTheRequestUnauthenticatedInsteadOfThrowing() throws Exception {
        assertThat(doFilter(filter, "Bearer not-a-jwt")).isNull();
    }

    @Test
    void preMultiTenancyTokenWithoutStoreIdIsTreatedAsUnauthenticated() throws Exception {
        assertThat(doFilter(filter, "Bearer " + tokenWithoutStoreId())).isNull();
    }
}
