package com.lexor.driver.security;

import com.lexor.driver.entity.Role;
import com.lexor.driver.entity.User;
import com.lexor.driver.entity.UserStatus;
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

    private User driverUser;
    private User adminUser;

    @BeforeEach
    void setUp() {
        driverUser = User.builder()
                .id(1L)
                .uuid("jwt-driver-uuid")
                .firstName("Vikram")
                .lastName("Singh")
                .email("vikram.jwt@lexor.mobility")
                .role(Role.DRIVER)
                .status(UserStatus.ACTIVE)
                .build();

        adminUser = User.builder()
                .id(2L)
                .uuid("jwt-admin-uuid")
                .firstName("Rutuja")
                .lastName("Admin")
                .email("admin.jwt@lexor.mobility")
                .role(Role.ADMIN)
                .status(UserStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("Should generate valid JWT with correct claims for driver")
    void testGenerateToken_Driver() {
        UserPrincipal principal = UserPrincipal.create(driverUser);
        String token = jwtTokenProvider.generateTokenFromUserPrincipal(principal);

        assertNotNull(token);
        assertTrue(jwtTokenProvider.validateToken(token));
        assertEquals("jwt-driver-uuid", jwtTokenProvider.getUuidFromToken(token));
        assertEquals("vikram.jwt@lexor.mobility", jwtTokenProvider.getEmailFromToken(token));
        assertEquals("ROLE_DRIVER", jwtTokenProvider.getRoleFromToken(token));
    }

    @Test
    @DisplayName("Should generate valid JWT with correct claims for admin")
    void testGenerateToken_Admin() {
        UserPrincipal principal = UserPrincipal.create(adminUser);
        String token = jwtTokenProvider.generateTokenFromUserPrincipal(principal);

        assertNotNull(token);
        assertTrue(jwtTokenProvider.validateToken(token));
        assertEquals("jwt-admin-uuid", jwtTokenProvider.getUuidFromToken(token));
        assertEquals("ROLE_ADMIN", jwtTokenProvider.getRoleFromToken(token));
    }

    @Test
    @DisplayName("Should reject tampered token")
    void testValidateToken_Tampered() {
        UserPrincipal principal = UserPrincipal.create(driverUser);
        String token = jwtTokenProvider.generateTokenFromUserPrincipal(principal);
        String tamperedToken = token + "xyz";

        assertFalse(jwtTokenProvider.validateToken(tamperedToken));
    }

    @Test
    @DisplayName("Should reject malformed token")
    void testValidateToken_Malformed() {
        assertFalse(jwtTokenProvider.validateToken("not.a.valid.jwt"));
    }
}
