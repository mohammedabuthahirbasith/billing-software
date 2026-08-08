package com.billing.billing.security;

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.billing.billing.model.Role;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7); // strip "Bearer "
            try {
                Claims claims = jwtService.parseClaims(token);
                String email = claims.getSubject();
                String role = claims.get("role", String.class);
                Long userId = claims.get("userId", Long.class);
                Long storeId = claims.get("storeId", Long.class);

                // A token issued before storeId existed on the JWT (i.e. before the multi-tenancy
                // deploy) has no storeId claim at all. Treat it — and any other token missing a claim
                // the principal needs — as invalid rather than partially authenticating: every
                // store-scoped code path assumes storeId is always present, and a null would surface
                // as a 500 instead of a clean 401. Checked explicitly so the resulting rejection is a
                // logged decision, not an incidental NullPointerException caught further down.
                if (storeId == null || userId == null || role == null) {
                    log.debug("Rejecting token for {} — missing userId/role/storeId claim", email);
                } else {
                    AuthenticatedUser principal = new AuthenticatedUser(userId, email, Role.valueOf(role), storeId);
                    var authentication = new UsernamePasswordAuthenticationToken(
                            principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (JwtException | IllegalArgumentException e) {
                // Expected for an expired, tampered-with, or otherwise malformed token (including a
                // role string no longer in the Role enum): leave unauthenticated so protected routes
                // return 401. Logged at debug because a flood of these is normal, client-caused traffic.
                log.debug("Rejecting bearer token on {} {}: {}",
                        request.getMethod(), request.getRequestURI(), e.getMessage());
            } catch (RuntimeException e) {
                // Anything else is a bug on our side, not a bad token — must not vanish silently.
                log.warn("Unexpected failure authenticating bearer token on {} {}",
                        request.getMethod(), request.getRequestURI(), e);
            }
        }

        filterChain.doFilter(request, response); // continue down the chain
    }
}