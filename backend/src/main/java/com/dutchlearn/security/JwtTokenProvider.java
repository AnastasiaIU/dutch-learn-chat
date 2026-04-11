package com.dutchlearn.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * JwtTokenProvider
 * Utility class for JWT token generation and validation
 */
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    @Value("${jwt.secret:your-secret-key-change-this-in-production-env}")
    private String jwtSecret;

    @Value("${jwt.expiration:86400000}")
    private long jwtExpiration;

    /**
     * Generate JWT token from email
     */
    public String generateToken(String email) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", email);
        return createToken(claims, email);
    }

    /**
     * Create JWT token
     */
    private String createToken(Map<String, Object> claims, String subject) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpiration);

        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey(), Jwts.SIG.HS512)
                .compact();
    }

    /**
     * Get signing key from secret
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = resolveKeyBytes(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private byte[] resolveKeyBytes(String secret) {
        try {
            byte[] decoded = Decoders.BASE64.decode(secret);
            if (decoded.length >= 64) {
                return decoded;
            }
        } catch (RuntimeException ignored) {
            // Not base64 encoded; fallback to deterministic hashing.
        }

        try {
            return MessageDigest.getInstance("SHA-512")
                    .digest(secret.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Unable to initialize JWT signing key", e);
        }
    }

    /**
     * Get email from token
     */
    public String getEmailFromToken(String token) {
        Claims claims = getAllClaimsFromToken(token);
        return claims.getSubject();
    }

    /**
     * Get all claims from token
     */
    private Claims getAllClaimsFromToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Validate token
     */
    public boolean isTokenValid(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
