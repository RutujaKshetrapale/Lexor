package com.lexor.auth.service.impl;

import com.lexor.auth.dto.request.LoginRequest;
import com.lexor.auth.dto.request.RegisterRequest;
import com.lexor.auth.dto.response.AuthResponse;
import com.lexor.auth.dto.response.UserResponse;
import com.lexor.auth.entity.Role;
import com.lexor.auth.entity.User;
import com.lexor.auth.entity.UserStatus;
import com.lexor.auth.exception.DuplicateResourceException;
import com.lexor.auth.exception.ForbiddenException;
import com.lexor.auth.exception.InvalidCredentialsException;
import com.lexor.auth.exception.ResourceNotFoundException;
import com.lexor.auth.repository.UserRepository;
import com.lexor.auth.security.JwtTokenProvider;
import com.lexor.auth.security.UserPrincipal;
import com.lexor.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        Role targetRole = request.getRole() != null ? request.getRole() : Role.RIDER;

        if (targetRole == Role.ADMIN) {
            throw new ForbiddenException("Public registration for ADMIN role is not permitted");
        }

        String normalizedEmail = request.getEmail().toLowerCase().trim();
        String normalizedPhone = request.getPhoneNumber().trim();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new DuplicateResourceException("Email is already registered: " + normalizedEmail);
        }

        if (userRepository.existsByPhoneNumber(normalizedPhone)) {
            throw new DuplicateResourceException("Phone number is already registered: " + normalizedPhone);
        }

        User user = User.builder()
                .uuid(UUID.randomUUID().toString())
                .firstName(request.getFirstName().trim())
                .lastName(request.getLastName().trim())
                .email(normalizedEmail)
                .phoneNumber(normalizedPhone)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(targetRole)
                .status(UserStatus.ACTIVE)
                .emailVerified(false)
                .phoneVerified(false)
                .build();

        User savedUser = userRepository.save(user);

        UserPrincipal userPrincipal = UserPrincipal.create(savedUser);
        String token = tokenProvider.generateTokenFromUserPrincipal(userPrincipal);

        return AuthResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresIn(tokenProvider.getExpirationMs() / 1000)
                .user(mapToUserResponse(savedUser))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.getEmail().toLowerCase().trim();

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ForbiddenException("User account is " + user.getStatus().name().toLowerCase());
        }

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(normalizedEmail, request.getPassword())
        );

        String token = tokenProvider.generateToken(authentication);

        return AuthResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresIn(tokenProvider.getExpirationMs() / 1000)
                .user(mapToUserResponse(user))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(UserPrincipal currentUser) {
        User user = userRepository.findByUuid(currentUser.getUuid())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with UUID: " + currentUser.getUuid()));
        return mapToUserResponse(user);
    }

    private UserResponse mapToUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .uuid(user.getUuid())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .role(user.getRole())
                .status(user.getStatus())
                .profilePictureUrl(user.getProfilePictureUrl())
                .emailVerified(user.isEmailVerified())
                .phoneVerified(user.isPhoneVerified())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
