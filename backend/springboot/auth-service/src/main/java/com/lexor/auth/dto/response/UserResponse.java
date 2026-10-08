package com.lexor.auth.dto.response;

import com.lexor.auth.entity.Role;
import com.lexor.auth.entity.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponse {

    private Long id;
    private String uuid;
    private String firstName;
    private String lastName;
    private String email;
    private String phoneNumber;
    private Role role;
    private UserStatus status;
    private String profilePictureUrl;
    private boolean emailVerified;
    private boolean phoneVerified;
    private LocalDateTime createdAt;
}
