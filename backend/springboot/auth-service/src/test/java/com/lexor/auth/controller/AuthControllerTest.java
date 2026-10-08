package com.lexor.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexor.auth.dto.request.LoginRequest;
import com.lexor.auth.dto.request.RegisterRequest;
import com.lexor.auth.dto.response.AuthResponse;
import com.lexor.auth.dto.response.UserResponse;
import com.lexor.auth.entity.Role;
import com.lexor.auth.entity.UserStatus;
import com.lexor.auth.exception.DuplicateResourceException;
import com.lexor.auth.exception.InvalidCredentialsException;
import com.lexor.auth.security.CustomUserDetailsService;
import com.lexor.auth.security.JwtAuthenticationEntryPoint;
import com.lexor.auth.security.JwtTokenProvider;
import com.lexor.auth.security.UserPrincipal;
import com.lexor.auth.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtTokenProvider tokenProvider;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @MockBean
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    private UserResponse sampleUserResponse;
    private AuthResponse sampleAuthResponse;

    @BeforeEach
    void setUp() {
        sampleUserResponse = UserResponse.builder()
                .id(1L)
                .uuid(UUID.randomUUID().toString())
                .firstName("Rajesh")
                .lastName("Kumar")
                .email("rajesh.kumar@example.com")
                .phoneNumber("+919876543211")
                .role(Role.RIDER)
                .status(UserStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();

        sampleAuthResponse = AuthResponse.builder()
                .accessToken("mock.jwt.token")
                .tokenType("Bearer")
                .expiresIn(86400L)
                .user(sampleUserResponse)
                .build();
    }

    @Test
    void testRegisterSuccess() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .firstName("Rajesh")
                .lastName("Kumar")
                .email("rajesh.kumar@example.com")
                .phoneNumber("+919876543211")
                .password("Password123")
                .role(Role.RIDER)
                .build();

        when(authService.register(any(RegisterRequest.class))).thenReturn(sampleAuthResponse);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value("mock.jwt.token"))
                .andExpect(jsonPath("$.user.email").value("rajesh.kumar@example.com"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
    }

    @Test
    void testRegisterDuplicateEmailReturnsConflict() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .firstName("Rajesh")
                .lastName("Kumar")
                .email("rajesh.kumar@example.com")
                .phoneNumber("+919876543211")
                .password("Password123")
                .role(Role.RIDER)
                .build();

        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new DuplicateResourceException("Email is already registered: rajesh.kumar@example.com"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DUPLICATE_RESOURCE"));
    }

    @Test
    void testLoginSuccess() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .email("rajesh.kumar@example.com")
                .password("Password123")
                .build();

        when(authService.login(any(LoginRequest.class))).thenReturn(sampleAuthResponse);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("mock.jwt.token"))
                .andExpect(jsonPath("$.user.email").value("rajesh.kumar@example.com"));
    }

    @Test
    void testLoginInvalidCredentialsReturnsUnauthorized() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .email("rajesh.kumar@example.com")
                .password("WrongPassword")
                .build();

        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new InvalidCredentialsException("Invalid email or password"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"));
    }
}
