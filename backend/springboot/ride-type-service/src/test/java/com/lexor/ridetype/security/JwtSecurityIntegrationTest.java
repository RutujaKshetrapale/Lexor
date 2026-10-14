package com.lexor.ridetype.security;

import com.lexor.ridetype.entity.Role;
import com.lexor.ridetype.entity.User;
import com.lexor.ridetype.entity.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class JwtSecurityIntegrationTest {

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private User adminUser;
    private User riderUser;

    @BeforeEach
    void setUp() {
        adminUser = User.builder()
                .id(1L)
                .uuid("admin-jwt-uuid-1111")
                .firstName("Rutuja")
                .lastName("Admin")
                .email("admin@lexor.mobility")
                .phoneNumber("+919876543210")
                .passwordHash("$2a$12$e0MYzXyjpJS7Pd0RVvHwHeFj5/5l.K1N6r7F0WpE0yP1O2Q3R4S5T")
                .role(Role.ADMIN)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .phoneVerified(true)
                .build();

        riderUser = User.builder()
                .id(2L)
                .uuid("rider-jwt-uuid-2222")
                .firstName("Rajesh")
                .lastName("Kumar")
                .email("rajesh@lexor.mobility")
                .phoneNumber("+919876543211")
                .passwordHash("$2a$12$e0MYzXyjpJS7Pd0RVvHwHeFj5/5l.K1N6r7F0WpE0yP1O2Q3R4S5T")
                .role(Role.RIDER)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .phoneVerified(true)
                .build();
    }

    @Test
    @DisplayName("Generate and validate JWT token for ADMIN role")
    void generateAndValidateToken_Admin() {
        UserPrincipal principal = UserPrincipal.create(adminUser);
        String token = jwtTokenProvider.generateTokenFromUserPrincipal(principal);

        assertNotNull(token);
        assertTrue(jwtTokenProvider.validateToken(token));
        assertEquals("admin-jwt-uuid-1111", jwtTokenProvider.getUuidFromToken(token));
        assertEquals("admin@lexor.mobility", jwtTokenProvider.getEmailFromToken(token));
        assertEquals("ROLE_ADMIN", jwtTokenProvider.getRoleFromToken(token));
    }

    @Test
    @DisplayName("Generate and validate JWT token for RIDER role")
    void generateAndValidateToken_Rider() {
        UserPrincipal principal = UserPrincipal.create(riderUser);
        String token = jwtTokenProvider.generateTokenFromUserPrincipal(principal);

        assertNotNull(token);
        assertTrue(jwtTokenProvider.validateToken(token));
        assertEquals("rider-jwt-uuid-2222", jwtTokenProvider.getUuidFromToken(token));
        assertEquals("rajesh@lexor.mobility", jwtTokenProvider.getEmailFromToken(token));
        assertEquals("ROLE_RIDER", jwtTokenProvider.getRoleFromToken(token));
    }

    @Test
    @DisplayName("Validate token returns false for invalid or tampered token")
    void validateToken_TamperedToken_ReturnsFalse() {
        UserPrincipal principal = UserPrincipal.create(riderUser);
        String token = jwtTokenProvider.generateTokenFromUserPrincipal(principal);
        String tamperedToken = token.substring(0, token.length() - 5) + "ABCDE";

        assertFalse(jwtTokenProvider.validateToken(tamperedToken));
    }

    @Test
    @DisplayName("Validate token returns false for malformed token string")
    void validateToken_MalformedToken_ReturnsFalse() {
        assertFalse(jwtTokenProvider.validateToken("not-a-valid-jwt-token"));
    }
}
