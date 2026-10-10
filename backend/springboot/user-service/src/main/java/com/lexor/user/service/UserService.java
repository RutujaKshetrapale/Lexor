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
import com.lexor.user.entity.UserStatus;
import com.lexor.user.security.UserPrincipal;

public interface UserService {

    UserResponse getCurrentUserProfile(UserPrincipal currentUser);

    UserResponse updateCurrentUserProfile(UserPrincipal currentUser, UpdateProfileRequest request);

    UserResponse patchCurrentUserProfile(UserPrincipal currentUser, PatchProfileRequest request);

    void deactivateCurrentUser(UserPrincipal currentUser);

    UserResponse createUser(AdminCreateUserRequest request);

    PagedResponse<UserResponse> getUsers(Role role, UserStatus status, String search, int page, int size, String sortBy, String sortDir);

    UserResponse getUserByUuid(String uuid);

    UserResponse updateUserByUuid(String uuid, AdminUpdateUserRequest request);

    UserResponse patchUserByUuid(String uuid, AdminPatchUserRequest request);

    UserResponse updateUserStatus(String adminUuid, String uuid, UpdateUserStatusRequest request);

    void deactivateUserByUuid(String adminUuid, String uuid);
}
