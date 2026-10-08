package com.lexor.auth.service;

import com.lexor.auth.dto.request.LoginRequest;
import com.lexor.auth.dto.request.RegisterRequest;
import com.lexor.auth.dto.response.AuthResponse;
import com.lexor.auth.dto.response.UserResponse;
import com.lexor.auth.security.UserPrincipal;

public interface AuthService {

    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    UserResponse getCurrentUser(UserPrincipal currentUser);
}
