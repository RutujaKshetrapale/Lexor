package com.lexor.admin.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@Component
public class JwtTokenProvider {

    private final SecretKey key;

    public JwtTokenProvider(@Value("${lexor.security.jwt.secret:}") String secret) {
        if (secret == null || secret.trim().length() < 32) {
            throw new IllegalStateException("Missing or insufficiently secure JWT secret. 'lexor.security.jwt.secret' must be at least 32 characters.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public Claims getClaimsFromToken(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public String getUserUuidFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims.getSubject();
    }

    public String getRoleFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        Object role = claims.get("role");
        return role != null ? role.toString() : "";
    }

    public String getEmailFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        Object email = claims.get("email");
        return email != null ? email.toString() : "";
    }
}
