package com.lexor.user.controller;

import com.lexor.user.dto.request.AdminCreateUserRequest;
import com.lexor.user.dto.request.AdminPatchUserRequest;
import com.lexor.user.dto.request.AdminUpdateUserRequest;
import com.lexor.user.dto.request.UpdateUserStatusRequest;
import com.lexor.user.dto.response.ErrorResponse;
import com.lexor.user.dto.response.PagedResponse;
import com.lexor.user.dto.response.UserResponse;
import com.lexor.user.entity.Role;
import com.lexor.user.entity.UserStatus;
import com.lexor.user.security.UserPrincipal;
import com.lexor.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "User Administration", description = "Endpoints for administrators to provision, list, inspect, update, and manage users")
@SecurityRequirement(name = "BearerAuthentication")
public class AdminUserController {

    private final UserService userService;

    @Operation(summary = "Provision a new user (ADMIN only)", description = "Creates a new user account through administrative provisioning. Hashes password with BCrypt. Does not issue login tokens.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User provisioned successfully",
                    content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request - Validation failure",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires ADMIN role",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Conflict - Email or phone already registered",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody AdminCreateUserRequest request) {
        UserResponse response = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "List and filter users (ADMIN only)", description = "Retrieves a paginated list of users filtered by role, status, or search query.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Users list retrieved successfully"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires ADMIN role",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<PagedResponse<UserResponse>> getUsers(
            @Parameter(description = "Filter by user role") @RequestParam(required = false) Role role,
            @Parameter(description = "Filter by account status") @RequestParam(required = false) UserStatus status,
            @Parameter(description = "Search query for name or email") @RequestParam(required = false) String search,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (max 100)") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort property (e.g. createdAt, email, firstName)") @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Sort direction (asc or desc)") @RequestParam(defaultValue = "desc") String sortDir
    ) {
        PagedResponse<UserResponse> response = userService.getUsers(role, status, search, page, size, sortBy, sortDir);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get user profile by UUID (ADMIN only)", description = "Retrieves profile details for a specific user by UUID.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User retrieved successfully",
                    content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires ADMIN role",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{uuid}")
    public ResponseEntity<UserResponse> getUserByUuid(@PathVariable String uuid) {
        UserResponse response = userService.getUserByUuid(uuid);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Fully update user profile by UUID (ADMIN only)", description = "Fully updates permitted administrative profile fields. Role and status changes must use dedicated operations.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User updated successfully",
                    content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request - Validation error",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires ADMIN role",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Conflict - Duplicate email or phone",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/{uuid}")
    public ResponseEntity<UserResponse> updateUserByUuid(
            @PathVariable String uuid,
            @Valid @RequestBody AdminUpdateUserRequest request
    ) {
        UserResponse response = userService.updateUserByUuid(uuid, request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Partially update user profile by UUID (ADMIN only)", description = "Partially updates permitted administrative profile fields. Omitted fields remain unchanged.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User patched successfully",
                    content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request - Validation error",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires ADMIN role",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Conflict - Duplicate email or phone",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{uuid}")
    public ResponseEntity<UserResponse> patchUserByUuid(
            @PathVariable String uuid,
            @Valid @RequestBody AdminPatchUserRequest request
    ) {
        UserResponse response = userService.patchUserByUuid(uuid, request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Update user account status (ADMIN only)", description = "Performs explicit status transitions (ACTIVE, SUSPENDED, DEACTIVATED, PENDING). Validates transition rules and protects against deactivating the last active administrator.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status updated successfully",
                    content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request - Invalid status transition or self-deactivation attempt",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires ADMIN role or deactivating last active admin",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{uuid}/status")
    public ResponseEntity<UserResponse> updateUserStatus(
            @AuthenticationPrincipal UserPrincipal adminUser,
            @PathVariable String uuid,
            @Valid @RequestBody UpdateUserStatusRequest request
    ) {
        UserResponse response = userService.updateUserStatus(adminUser.getUuid(), uuid, request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Soft-deactivate user account (ADMIN only)", description = "Soft-deactivates user account (status=DEACTIVATED). Never physically deletes user row. Prevents self-deactivation and deactivation of the last active administrator.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "User soft-deactivated successfully"),
            @ApiResponse(responseCode = "400", description = "Bad Request - Attempting self-deactivation",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires ADMIN role or last active admin protection",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{uuid}")
    public ResponseEntity<Void> deactivateUserByUuid(
            @AuthenticationPrincipal UserPrincipal adminUser,
            @PathVariable String uuid
    ) {
        userService.deactivateUserByUuid(adminUser.getUuid(), uuid);
        return ResponseEntity.noContent().build();
    }
}
