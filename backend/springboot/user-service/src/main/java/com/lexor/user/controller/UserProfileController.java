package com.lexor.user.controller;

import com.lexor.user.dto.request.PatchProfileRequest;
import com.lexor.user.dto.request.UpdateProfileRequest;
import com.lexor.user.dto.response.ErrorResponse;
import com.lexor.user.dto.response.UserResponse;
import com.lexor.user.security.UserPrincipal;
import com.lexor.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
@Tag(name = "User Self Profile", description = "Endpoints for authenticated users to view and update their own profile")
@SecurityRequirement(name = "BearerAuthentication")
public class UserProfileController {

    private final UserService userService;

    @Operation(summary = "Get current authenticated user profile", description = "Returns profile details for the user identified by JWT subject.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile retrieved successfully",
                    content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "User profile not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<UserResponse> getCurrentUserProfile(@AuthenticationPrincipal UserPrincipal currentUser) {
        UserResponse response = userService.getCurrentUserProfile(currentUser);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Fully update current user profile", description = "Fully updates permitted profile fields (firstName, lastName, phoneNumber, profilePictureUrl). Protected fields like role, email, status, and password are rejected.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile updated successfully",
                    content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request - Validation error",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Conflict - Phone number already in use",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping
    public ResponseEntity<UserResponse> updateCurrentUserProfile(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        UserResponse response = userService.updateCurrentUserProfile(currentUser, request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Partially update current user profile", description = "Partially updates supplied permitted profile fields. Omitted fields remain unchanged.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile patched successfully",
                    content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request - Validation error",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Conflict - Phone number already in use",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping
    public ResponseEntity<UserResponse> patchCurrentUserProfile(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody PatchProfileRequest request
    ) {
        UserResponse response = userService.patchCurrentUserProfile(currentUser, request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Deactivate current user account", description = "Soft-deactivates the currently authenticated user account (status=DEACTIVATED). ADMIN accounts cannot self-deactivate through this endpoint.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Account soft-deactivated successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin accounts cannot self-deactivate",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping
    public ResponseEntity<Void> deactivateCurrentUser(@AuthenticationPrincipal UserPrincipal currentUser) {
        userService.deactivateCurrentUser(currentUser);
        return ResponseEntity.noContent().build();
    }
}
