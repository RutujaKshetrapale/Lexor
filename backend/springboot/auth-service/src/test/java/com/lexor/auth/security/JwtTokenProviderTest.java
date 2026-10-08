package com.lexor.auth.security;

import com.lexor.auth.entity.Role;
import com.lexor.auth.entity.User;
import com.lexor.auth.entity.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider tokenProvider;
    private UserPrincipal userPrincipal;

    @BeforeEach
    void setUp() throws Exception {
        tokenProvider = new JwtTokenProvider();

        // Inject configuration values via reflection for unit test
        Field secretField = JwtTokenProvider.class.getDeclaredField("jwtSecret");
        secretField.setAccessible(true);
        secretField.set(tokenProvider, "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970");

        Field expField = JwtTokenProvider.class.getDeclaredField("jwtExpirationMs");
        expField.setAccessible(true);
        expField.set(tokenProvider, 3600000L);

        User user = User.builder()
                .id(100L)
                .uuid(UUID.randomUUID().toString())
                .firstName("Test")
                .lastName("Rider")
                .email("test.rider@example.com")
                .passwordHash("$2a$12$SampleHashValue")
                .role(Role.RIDER)
                .status(UserStatus.ACTIVE)
                .build();

        userPrincipal = UserPrincipal.create(user);
    }

    @Test
    void testGenerateAndValidateToken() {
        String token = tokenProvider.generateTokenFromUserPrincipal(userPrincipal);
        assertNotNull(token);
        assertTrue(tokenProvider.validateToken(token));
    }

    @Test
    void testExtractClaimsFromToken() {
        String token = tokenProvider.generateTokenFromUserPrincipal(userPrincipal);
        String extractedUuid = tokenProvider.getUuidFromToken(token);
        String extractedEmail = tokenProvider.getEmailFromToken(token);

        assertEquals(userPrincipal.getUuid(), extractedUuid);
        assertEquals(userPrincipal.getEmail(), extractedEmail);
    }

    @Test
    void testInvalidTokenRejection() {
        assertFalse(tokenProvider.validateToken("invalid.jwt.token"));
    }
}
