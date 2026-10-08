package com.lexor.auth.service;

import com.lexor.auth.dto.request.LoginRequest;
import com.lexor.auth.dto.request.RegisterRequest;
import com.lexor.auth.dto.response.AuthResponse;
import com.lexor.auth.entity.Role;
import com.lexor.auth.entity.User;
import com.lexor.auth.entity.UserStatus;
import com.lexor.auth.exception.DuplicateResourceException;
import com.lexor.auth.exception.ForbiddenException;
import com.lexor.auth.exception.InvalidCredentialsException;
import com.lexor.auth.repository.UserRepository;
import com.lexor.auth.security.JwtTokenProvider;
import com.lexor.auth.security.UserPrincipal;
import com.lexor.auth.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtTokenProvider tokenProvider;

    @InjectMocks
    private AuthServiceImpl authService;

    private User sampleUser;
    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .id(1L)
                .uuid(UUID.randomUUID().toString())
                .firstName("Rajesh")
                .lastName("Kumar")
                .email("rajesh.kumar@example.com")
                .phoneNumber("+919876543211")
                .passwordHash("$2a$12$SampleBCryptHash")
                .role(Role.RIDER)
                .status(UserStatus.ACTIVE)
                .build();

        registerRequest = RegisterRequest.builder()
                .firstName("Rajesh")
                .lastName("Kumar")
                .email("rajesh.kumar@example.com")
                .phoneNumber("+919876543211")
                .password("Password123")
                .role(Role.RIDER)
                .build();

        loginRequest = LoginRequest.builder()
                .email("rajesh.kumar@example.com")
                .password("Password123")
                .build();
    }

    @Test
    void testRegisterSuccess() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByPhoneNumber(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$12$SampleBCryptHash");
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);
        when(tokenProvider.generateTokenFromUserPrincipal(any(UserPrincipal.class))).thenReturn("sample.jwt.token");
        when(tokenProvider.getExpirationMs()).thenReturn(86400000L);

        AuthResponse response = authService.register(registerRequest);

        assertNotNull(response);
        assertEquals("sample.jwt.token", response.getAccessToken());
        assertEquals("Bearer", response.getTokenType());
        assertEquals("rajesh.kumar@example.com", response.getUser().getEmail());
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void testRegisterDuplicateEmailRejected() {
        when(userRepository.existsByEmail(anyString())).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> authService.register(registerRequest));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void testRegisterDuplicatePhoneRejected() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByPhoneNumber(anyString())).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> authService.register(registerRequest));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void testRegisterAdminRoleForbidden() {
        registerRequest.setRole(Role.ADMIN);

        assertThrows(ForbiddenException.class, () -> authService.register(registerRequest));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void testLoginSuccess() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(null);
        when(tokenProvider.generateToken(any())).thenReturn("sample.jwt.token");
        when(tokenProvider.getExpirationMs()).thenReturn(86400000L);

        AuthResponse response = authService.login(loginRequest);

        assertNotNull(response);
        assertEquals("sample.jwt.token", response.getAccessToken());
        assertEquals("rajesh.kumar@example.com", response.getUser().getEmail());
    }

    @Test
    void testLoginInvalidCredentialsFailed() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> authService.login(loginRequest));
    }
}
