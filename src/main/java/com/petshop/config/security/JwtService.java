package com.petshop.config.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Generates and validates HS256-signed JWT access tokens.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMs;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiration-ms}") long expirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    public String generateToken(CustomUserDetails user) {
        Date now = new Date();
        return Jwts.builder()
                .subject(user.getUsername())
                .claim("uid", user.getId())
                .claim("role", user.getRoleCode())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(key)
                .compact();
    }

    /** Returns claims if the token is valid, otherwise throws JwtException. */
    public Claims parse(String token) throws JwtException {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public String extractUsername(String token) {
        Claims claims = parse(token);
        // Email-verification tokens must never be usable as login tokens
        if (claims.get("purpose") != null) {
            throw new JwtException("Not a login token");
        }
        return claims.getSubject();
    }

    /** Short-lived signed token carrying pending registration data for email confirmation. */
    public String generateRegistrationToken(String fullName, String email, String phone,
                                            String passwordHash, long ttlMs) {
        Date now = new Date();
        return Jwts.builder()
                .subject(email)
                .claim("purpose", "REGISTER")
                .claim("fullName", fullName)
                .claim("phone", phone)
                .claim("pwd", passwordHash)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlMs))
                .signWith(key)
                .compact();
    }

    /** Parses a registration token; throws JwtException if invalid/expired/wrong purpose. */
    public Claims parseRegistrationToken(String token) throws JwtException {
        Claims claims = parse(token);
        if (!"REGISTER".equals(claims.get("purpose"))) {
            throw new JwtException("Not a registration token");
        }
        return claims;
    }
}
