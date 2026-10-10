package com.lexor.user.security;

import com.lexor.user.entity.Role;
import com.lexor.user.entity.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;
    private static final String SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private static final long EXPIRATION_MS = 3600000;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtExpirationMs", EXPIRATION_MS);
    }

    @Test
    @DisplayName("Should generate valid JWT token with correct claims matching Auth Service")
    void testGenerateAndValidateToken() {
        UserPrincipal principal = new UserPrincipal(
                100L,
                "user-uuid-12345",
                "Jane",
                "Doe",
                "jane.doe@example.com",
                "hashedpassword",
                Role.RIDER,
                UserStatus.ACTIVE,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_RIDER")),
                true
        );

        String token = jwtTokenProvider.generateTokenFromUserPrincipal(principal);

        assertNotNull(token);
        assertTrue(jwtTokenProvider.validateToken(token));
        assertEquals("user-uuid-12345", jwtTokenProvider.getUuidFromToken(token));
        assertEquals("jane.doe@example.com", jwtTokenProvider.getEmailFromToken(token));
        assertEquals("ROLE_RIDER", jwtTokenProvider.getRoleFromToken(token));
    }

    @Test
    @DisplayName("Should reject invalid or malformed token")
    void testInvalidToken() {
        String invalidToken = "invalid.jwt.token";
        assertFalse(jwtTokenProvider.validateToken(invalidToken));
    }
}
