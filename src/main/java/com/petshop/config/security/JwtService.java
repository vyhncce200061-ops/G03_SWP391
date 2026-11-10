package com.petshop.config.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.Date;

/**
 * Encrypted email-registration tokens. Login remains session-based.
 */
@Service
public class JwtService {

    private final SecretKey key;
    public JwtService(@Value("${app.jwt.registration-encryption-key:${REGISTRATION_ENCRYPTION_KEY:}}") String encodedKey) {
        // A private persistent key keeps links valid across application restarts.
        if (encodedKey == null || encodedKey.isBlank()) {
            this.key = Jwts.ENC.A256GCM.key().build();
        } else {
            byte[] bytes;
            try {
                bytes = Base64.getDecoder().decode(encodedKey.strip());
            } catch (IllegalArgumentException error) {
                throw new IllegalArgumentException("app.jwt.registration-encryption-key phải là Base64 của 32 byte.");
            }
            if (bytes.length != 32) {
                throw new IllegalArgumentException("app.jwt.registration-encryption-key phải là Base64 của 32 byte.");
            }
            this.key = new SecretKeySpec(bytes, "AES");
        }
    }

    /** Only authenticated encrypted payloads are accepted; legacy signed links are rejected. */
    public Claims parse(String token) throws JwtException {
        var encrypted = Jwts.parser().decryptWith(key).build().parseEncryptedClaims(token);
        if (!"dir".equals(encrypted.getHeader().getAlgorithm())
                || !"A256GCM".equals(encrypted.getHeader().getEncryptionAlgorithm())) {
            throw new io.jsonwebtoken.UnsupportedJwtException("Unsupported registration token encryption");
        }
        return encrypted.getPayload();
    }

    /** Short-lived encrypted pending registration data for email confirmation. */
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
                .encryptWith(key, Jwts.ENC.A256GCM)
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
