package com.lexor.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexor.user.dto.request.AdminCreateUserRequest;
import com.lexor.user.dto.request.AdminPatchUserRequest;
import com.lexor.user.dto.request.AdminUpdateUserRequest;
import com.lexor.user.dto.request.UpdateUserStatusRequest;
import com.lexor.user.entity.Role;
import com.lexor.user.entity.User;
import com.lexor.user.entity.UserStatus;
import com.lexor.user.repository.UserRepository;
import com.lexor.user.security.JwtTokenProvider;
import com.lexor.user.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    private User adminUser;
    private User riderUser;
    private User driverUser;

    private String adminToken;
    private String riderToken;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        adminUser = User.builder()
                .uuid("admin-uuid-001")
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
        adminUser = userRepository.save(adminUser);

        riderUser = User.builder()
                .uuid("rider-uuid-002")
                .firstName("Rajesh")
                .lastName("Kumar")
                .email("rajesh.kumar@example.com")
                .phoneNumber("+919876543211")
                .passwordHash("$2a$12$e0MYzXyjpJS7Pd0RVvHwHeFj5/5l.K1N6r7F0WpE0yP1O2Q3R4S5T")
                .role(Role.RIDER)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .phoneVerified(true)
                .build();
        riderUser = userRepository.save(riderUser);

        driverUser = User.builder()
                .uuid("driver-uuid-003")
                .firstName("Vikram")
                .lastName("Singh")
                .email("vikram.singh@example.com")
                .phoneNumber("+919876543212")
                .passwordHash("$2a$12$e0MYzXyjpJS7Pd0RVvHwHeFj5/5l.K1N6r7F0WpE0yP1O2Q3R4S5T")
                .role(Role.DRIVER)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .phoneVerified(true)
                .build();
        driverUser = userRepository.save(driverUser);

        adminToken = jwtTokenProvider.generateTokenFromUserPrincipal(UserPrincipal.create(adminUser));
        riderToken = jwtTokenProvider.generateTokenFromUserPrincipal(UserPrincipal.create(riderUser));
    }

    @Test
    @DisplayName("GET /api/v1/users - Non-ADMIN should be rejected with 403 Forbidden")
    void testGetUsersRiderForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + riderToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/v1/users - ADMIN should successfully provision a new user")
    void testCreateUserSuccess() throws Exception {
        AdminCreateUserRequest request = AdminCreateUserRequest.builder()
                .firstName("Suresh")
                .lastName("Patel")
                .email("suresh.patel@example.com")
                .phoneNumber("+919876543999")
                .password("Password123!")
                .role(Role.RIDER)
                .status(UserStatus.ACTIVE)
                .build();

        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uuid", notNullValue()))
                .andExpect(jsonPath("$.firstName", is("Suresh")))
                .andExpect(jsonPath("$.email", is("suresh.patel@example.com")))
                .andExpect(jsonPath("$.role", is("RIDER")))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("POST /api/v1/users - Should reject duplicate email with 409 Conflict")
    void testCreateUserDuplicateEmail() throws Exception {
        AdminCreateUserRequest request = AdminCreateUserRequest.builder()
                .firstName("Suresh")
                .lastName("Patel")
                .email("rajesh.kumar@example.com")
                .phoneNumber("+919876543999")
                .password("Password123!")
                .role(Role.RIDER)
                .build();

        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", is("DUPLICATE_RESOURCE")));
    }

    @Test
    @DisplayName("GET /api/v1/users - ADMIN should list and filter users with pagination")
    void testGetUsersSuccess() throws Exception {
        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("role", "RIDER")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].email", is("rajesh.kumar@example.com")))
                .andExpect(jsonPath("$.totalElements", is(1)));
    }

    @Test
    @DisplayName("GET /api/v1/users/{uuid} - ADMIN should retrieve existing user profile")
    void testGetUserByUuidSuccess() throws Exception {
        mockMvc.perform(get("/api/v1/users/driver-uuid-003")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uuid", is("driver-uuid-003")))
                .andExpect(jsonPath("$.firstName", is("Vikram")))
                .andExpect(jsonPath("$.role", is("DRIVER")));
    }

    @Test
    @DisplayName("GET /api/v1/users/{uuid} - Unknown UUID should return 404 Not Found")
    void testGetUserByUuidNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/users/unknown-uuid-999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", is("RESOURCE_NOT_FOUND")));
    }

    @Test
    @DisplayName("PUT /api/v1/users/{uuid} - ADMIN should update user profile")
    void testUpdateUserByUuidSuccess() throws Exception {
        AdminUpdateUserRequest request = AdminUpdateUserRequest.builder()
                .firstName("Rajesh")
                .lastName("Kumar Updated")
                .email("rajesh.updated@example.com")
                .phoneNumber("+919876543211")
                .build();

        mockMvc.perform(put("/api/v1/users/rider-uuid-002")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName", is("Kumar Updated")))
                .andExpect(jsonPath("$.email", is("rajesh.updated@example.com")));
    }

    @Test
    @DisplayName("PATCH /api/v1/users/{uuid} - ADMIN should partially update user profile")
    void testPatchUserByUuidSuccess() throws Exception {
        AdminPatchUserRequest request = AdminPatchUserRequest.builder()
                .firstName("Vikramaditya")
                .build();

        mockMvc.perform(patch("/api/v1/users/driver-uuid-003")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName", is("Vikramaditya")))
                .andExpect(jsonPath("$.lastName", is("Singh")));
    }

    @Test
    @DisplayName("PATCH /api/v1/users/{uuid}/status - ADMIN should update user status")
    void testUpdateUserStatusSuccess() throws Exception {
        UpdateUserStatusRequest request = UpdateUserStatusRequest.builder()
                .status(UserStatus.SUSPENDED)
                .build();

        mockMvc.perform(patch("/api/v1/users/rider-uuid-002/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SUSPENDED")));
    }

    @Test
    @DisplayName("PATCH /api/v1/users/{uuid}/status - Invalid status transition should return 400 Bad Request")
    void testUpdateUserStatusInvalidTransition() throws Exception {
        riderUser.setStatus(UserStatus.DEACTIVATED);
        userRepository.save(riderUser);

        UpdateUserStatusRequest request = UpdateUserStatusRequest.builder()
                .status(UserStatus.SUSPENDED)
                .build();

        mockMvc.perform(patch("/api/v1/users/rider-uuid-002/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("BAD_REQUEST")));
    }

    @Test
    @DisplayName("DELETE /api/v1/users/{uuid} - ADMIN should soft-deactivate user account")
    void testDeactivateUserByUuidSuccess() throws Exception {
        mockMvc.perform(delete("/api/v1/users/rider-uuid-002")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        User checkUser = userRepository.findByUuid("rider-uuid-002").orElseThrow();
        assertEquals(UserStatus.DEACTIVATED, checkUser.getStatus());
        assertTrue(userRepository.existsById(checkUser.getId()), "Row must remain in database (soft delete)");
    }
}
