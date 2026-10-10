package com.lexor.user.service;

import com.lexor.user.dto.request.AdminCreateUserRequest;
import com.lexor.user.dto.request.AdminPatchUserRequest;
import com.lexor.user.dto.request.AdminUpdateUserRequest;
import com.lexor.user.dto.request.PatchProfileRequest;
import com.lexor.user.dto.request.UpdateProfileRequest;
import com.lexor.user.dto.request.UpdateUserStatusRequest;
import com.lexor.user.dto.response.PagedResponse;
import com.lexor.user.dto.response.UserResponse;
import com.lexor.user.entity.Role;
import com.lexor.user.entity.User;
import com.lexor.user.entity.UserStatus;
import com.lexor.user.exception.BadRequestException;
import com.lexor.user.exception.DuplicateResourceException;
import com.lexor.user.exception.ForbiddenException;
import com.lexor.user.exception.InvalidStatusTransitionException;
import com.lexor.user.exception.ResourceNotFoundException;
import com.lexor.user.repository.UserRepository;
import com.lexor.user.security.UserPrincipal;
import com.lexor.user.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserServiceImpl userService;

    private User sampleUser;
    private User adminUser;
    private UserPrincipal riderPrincipal;
    private UserPrincipal adminPrincipal;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .id(1L)
                .uuid("rider-uuid-111")
                .firstName("John")
                .lastName("Doe")
                .email("john.doe@example.com")
                .phoneNumber("+919876543211")
                .passwordHash("hashedpass")
                .role(Role.RIDER)
                .status(UserStatus.ACTIVE)
                .profilePictureUrl("https://example.com/pic.jpg")
                .emailVerified(true)
                .phoneVerified(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        adminUser = User.builder()
                .id(2L)
                .uuid("admin-uuid-222")
                .firstName("Rutuja")
                .lastName("Admin")
                .email("admin@lexor.mobility")
                .phoneNumber("+919876543210")
                .passwordHash("hashedpass")
                .role(Role.ADMIN)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .phoneVerified(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        riderPrincipal = new UserPrincipal(
                1L, "rider-uuid-111", "John", "Doe", "john.doe@example.com",
                "hashedpass", Role.RIDER, UserStatus.ACTIVE,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_RIDER")), true
        );

        adminPrincipal = new UserPrincipal(
                2L, "admin-uuid-222", "Rutuja", "Admin", "admin@lexor.mobility",
                "hashedpass", Role.ADMIN, UserStatus.ACTIVE,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN")), true
        );
    }

    @Test
    @DisplayName("Should retrieve current user profile successfully")
    void testGetCurrentUserProfile() {
        when(userRepository.findByUuid("rider-uuid-111")).thenReturn(Optional.of(sampleUser));

        UserResponse response = userService.getCurrentUserProfile(riderPrincipal);

        assertNotNull(response);
        assertEquals("rider-uuid-111", response.getUuid());
        assertEquals("John", response.getFirstName());
        assertEquals("john.doe@example.com", response.getEmail());
    }

    @Test
    @DisplayName("Should fully update current user profile")
    void testUpdateCurrentUserProfile() {
        UpdateProfileRequest request = UpdateProfileRequest.builder()
                .firstName("Johnny")
                .lastName("Smith")
                .phoneNumber("+919876543299")
                .profilePictureUrl("https://example.com/newpic.jpg")
                .build();

        when(userRepository.findByUuid("rider-uuid-111")).thenReturn(Optional.of(sampleUser));
        when(userRepository.existsByPhoneNumberAndUuidNot("+919876543299", "rider-uuid-111")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = userService.updateCurrentUserProfile(riderPrincipal, request);

        assertEquals("Johnny", response.getFirstName());
        assertEquals("Smith", response.getLastName());
        assertEquals("+919876543299", response.getPhoneNumber());
        assertEquals("https://example.com/newpic.jpg", response.getProfilePictureUrl());
    }

    @Test
    @DisplayName("Should reject update if phone number belongs to another user")
    void testUpdateCurrentUserProfileDuplicatePhone() {
        UpdateProfileRequest request = UpdateProfileRequest.builder()
                .firstName("Johnny")
                .lastName("Smith")
                .phoneNumber("+919876543210")
                .build();

        when(userRepository.findByUuid("rider-uuid-111")).thenReturn(Optional.of(sampleUser));
        when(userRepository.existsByPhoneNumberAndUuidNot("+919876543210", "rider-uuid-111")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> userService.updateCurrentUserProfile(riderPrincipal, request));
    }

    @Test
    @DisplayName("Should partially update current user profile and preserve omitted fields")
    void testPatchCurrentUserProfile() {
        PatchProfileRequest request = PatchProfileRequest.builder()
                .firstName("Johnathan")
                .build();

        when(userRepository.findByUuid("rider-uuid-111")).thenReturn(Optional.of(sampleUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = userService.patchCurrentUserProfile(riderPrincipal, request);

        assertEquals("Johnathan", response.getFirstName());
        assertEquals("Doe", response.getLastName());
        assertEquals("+919876543211", response.getPhoneNumber());
    }

    @Test
    @DisplayName("Should soft-deactivate current user account")
    void testDeactivateCurrentUser() {
        when(userRepository.findByUuid("rider-uuid-111")).thenReturn(Optional.of(sampleUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.deactivateCurrentUser(riderPrincipal);

        assertEquals(UserStatus.DEACTIVATED, sampleUser.getStatus());
    }

    @Test
    @DisplayName("Should prevent admin self-deactivation via self endpoint")
    void testDeactivateCurrentUserAdminForbidden() {
        when(userRepository.findByUuid("admin-uuid-222")).thenReturn(Optional.of(adminUser));

        assertThrows(ForbiddenException.class, () -> userService.deactivateCurrentUser(adminPrincipal));
    }

    @Test
    @DisplayName("Should create user as admin")
    void testCreateUser() {
        AdminCreateUserRequest request = AdminCreateUserRequest.builder()
                .firstName("Vikram")
                .lastName("Driver")
                .email("vikram@example.com")
                .phoneNumber("+919876543212")
                .password("secret123")
                .role(Role.DRIVER)
                .status(UserStatus.ACTIVE)
                .build();

        when(userRepository.existsByEmail("vikram@example.com")).thenReturn(false);
        when(userRepository.existsByPhoneNumber("+919876543212")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("encodedsecret");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = userService.createUser(request);

        assertNotNull(response);
        assertEquals("Vikram", response.getFirstName());
        assertEquals("vikram@example.com", response.getEmail());
        assertEquals(Role.DRIVER, response.getRole());
    }

    @Test
    @DisplayName("Should reject user creation with duplicate email")
    void testCreateUserDuplicateEmail() {
        AdminCreateUserRequest request = AdminCreateUserRequest.builder()
                .firstName("Vikram")
                .lastName("Driver")
                .email("john.doe@example.com")
                .phoneNumber("+919876543299")
                .password("secret123")
                .role(Role.DRIVER)
                .build();

        when(userRepository.existsByEmail("john.doe@example.com")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> userService.createUser(request));
    }

    @Test
    @DisplayName("Should list users with pagination and filtering")
    void testGetUsers() {
        when(userRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sampleUser)));

        PagedResponse<UserResponse> response = userService.getUsers(Role.RIDER, UserStatus.ACTIVE, "John", 0, 20, "createdAt", "desc");

        assertNotNull(response);
        assertEquals(1, response.getContent().size());
        assertEquals("John", response.getContent().get(0).getFirstName());
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException for unknown UUID")
    void testGetUserByUnknownUuid() {
        when(userRepository.findByUuid("non-existent-uuid")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userService.getUserByUuid("non-existent-uuid"));
    }

    @Test
    @DisplayName("Should update user status successfully for valid transition")
    void testUpdateUserStatus() {
        UpdateUserStatusRequest request = UpdateUserStatusRequest.builder()
                .status(UserStatus.SUSPENDED)
                .build();

        when(userRepository.findByUuid("rider-uuid-111")).thenReturn(Optional.of(sampleUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = userService.updateUserStatus("admin-uuid-222", "rider-uuid-111", request);

        assertEquals(UserStatus.SUSPENDED, response.getStatus());
    }

    @Test
    @DisplayName("Should reject invalid status transition")
    void testUpdateUserStatusInvalidTransition() {
        sampleUser.setStatus(UserStatus.DEACTIVATED);

        UpdateUserStatusRequest request = UpdateUserStatusRequest.builder()
                .status(UserStatus.SUSPENDED)
                .build();

        when(userRepository.findByUuid("rider-uuid-111")).thenReturn(Optional.of(sampleUser));

        assertThrows(InvalidStatusTransitionException.class, () ->
                userService.updateUserStatus("admin-uuid-222", "rider-uuid-111", request));
    }

    @Test
    @DisplayName("Should protect last active administrator from deactivation")
    void testDeactivateLastActiveAdmin() {
        when(userRepository.findByUuid("admin-uuid-222")).thenReturn(Optional.of(adminUser));
        when(userRepository.countByRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE)).thenReturn(1L);

        assertThrows(ForbiddenException.class, () ->
                userService.deactivateUserByUuid("other-admin-uuid", "admin-uuid-222"));
    }
}
