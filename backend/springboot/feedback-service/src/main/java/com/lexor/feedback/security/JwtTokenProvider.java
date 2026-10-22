package com.lexor.feedback.security;

import com.lexor.feedback.config.FeedbackProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtTokenProvider {

    private final FeedbackProperties properties;

    private SecretKey getSigningKey() {
        byte[] keyBytes = properties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (Exception ex) {
            log.warn("Invalid JWT token: {}", ex.getMessage());
            return false;
        }
    }

    public Claims getClaimsFromToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public UserPrincipal getUserPrincipal(String token) {
        Claims claims = getClaimsFromToken(token);
        String subject = claims.getSubject();
        String role = claims.get("role", String.class);
        String email = claims.get("email", String.class);
        Long userId = null;
        Object rawUserId = claims.get("userId");
        if (rawUserId instanceof Number number) {
            userId = number.longValue();
        }

        return UserPrincipal.fromClaims(subject, userId, email, role);
    }

    public String generateToken(String uuid, Long userId, String email, String role) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + properties.getJwt().getExpirationMs());
        String normalizedRole = (role != null && role.startsWith("ROLE_")) ? role : "ROLE_" + (role != null ? role : "RIDER");

        return Jwts.builder()
                .issuer("LEXOR")
                .audience().add("LEXOR").and()
                .subject(uuid)
                .claim("userId", userId)
                .claim("email", email)
                .claim("role", normalizedRole)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }
}
