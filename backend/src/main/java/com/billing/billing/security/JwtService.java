package com.billing.billing.security;

import java.util.Date;
import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.billing.billing.model.User;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMs;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms}") long expirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
        this.expirationMs = expirationMs;
    }

    public String generateToken(User user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);
        return Jwts.builder()
                .subject(user.getEmail())          // "sub" claim: who the token is for
                .claim("role", user.getRole().name())
                .claim("userId", user.getId())
                .issuedAt(now)                     // "iat"
                .expiration(expiry)                // "exp"
                .signWith(key)                     // sign with our secret → the signature
                .compact();                        // produce header.payload.signature
    }
}