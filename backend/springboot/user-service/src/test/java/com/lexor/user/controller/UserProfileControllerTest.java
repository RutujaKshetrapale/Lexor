package com.lexor.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexor.user.dto.request.PatchProfileRequest;
import com.lexor.user.dto.request.UpdateProfileRequest;
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

import java.util.Collections;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    private User riderUser;
    private User adminUser;
    private String riderToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        riderUser = User.builder()
                .uuid("rider-uuid-11111")
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

        adminUser = User.builder()
                .uuid("admin-uuid-22222")
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

        UserPrincipal riderPrincipal = UserPrincipal.create(riderUser);
        riderToken = jwtTokenProvider.generateTokenFromUserPrincipal(riderPrincipal);

        UserPrincipal adminPrincipal = UserPrincipal.create(adminUser);
        adminToken = jwtTokenProvider.generateTokenFromUserPrincipal(adminPrincipal);
    }

    @Test
    @DisplayName("GET /api/v1/users/me - Should return authenticated rider profile")
    void testGetCurrentUserProfileSuccess() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + riderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uuid", is("rider-uuid-11111")))
                .andExpect(jsonPath("$.firstName", is("Rajesh")))
                .andExpect(jsonPath("$.lastName", is("Kumar")))
                .andExpect(jsonPath("$.email", is("rajesh.kumar@example.com")))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/v1/users/me - Should reject unauthenticated request with 401")
    void testGetCurrentUserProfileUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.error", is("UNAUTHORIZED")));
    }

    @Test
    @DisplayName("PUT /api/v1/users/me - Should fully update editable profile fields")
    void testUpdateCurrentUserProfileSuccess() throws Exception {
        UpdateProfileRequest request = UpdateProfileRequest.builder()
                .firstName("Rajeshkumar")
                .lastName("Verma")
                .phoneNumber("+919876543999")
                .profilePictureUrl("https://cdn.lexor.mobility/pics/rajesh.jpg")
                .build();

        mockMvc.perform(put("/api/v1/users/me")
                        .header("Authorization", "Bearer " + riderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName", is("Rajeshkumar")))
                .andExpect(jsonPath("$.lastName", is("Verma")))
                .andExpect(jsonPath("$.phoneNumber", is("+919876543999")))
                .andExpect(jsonPath("$.profilePictureUrl", is("https://cdn.lexor.mobility/pics/rajesh.jpg")))
                .andExpect(jsonPath("$.role", is("RIDER")))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("PATCH /api/v1/users/me - Should partially update profile and preserve omitted fields")
    void testPatchCurrentUserProfileSuccess() throws Exception {
        PatchProfileRequest request = PatchProfileRequest.builder()
                .firstName("Raj")
                .build();

        mockMvc.perform(patch("/api/v1/users/me")
                        .header("Authorization", "Bearer " + riderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName", is("Raj")))
                .andExpect(jsonPath("$.lastName", is("Kumar")))
                .andExpect(jsonPath("$.phoneNumber", is("+919876543211")));
    }

    @Test
    @DisplayName("DELETE /api/v1/users/me - Should soft-deactivate user account")
    void testDeactivateCurrentUserSuccess() throws Exception {
        mockMvc.perform(delete("/api/v1/users/me")
                        .header("Authorization", "Bearer " + riderToken))
                .andExpect(status().isNoContent());

        User deactivatedUser = userRepository.findByUuid("rider-uuid-11111").orElseThrow();
        is(UserStatus.DEACTIVATED).matches(deactivatedUser.getStatus());
    }

    @Test
    @DisplayName("DELETE /api/v1/users/me - Should reject admin self-deactivation with 403")
    void testDeactivateCurrentUserAdminForbidden() throws Exception {
        mockMvc.perform(delete("/api/v1/users/me")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error", is("FORBIDDEN")));
    }
}
