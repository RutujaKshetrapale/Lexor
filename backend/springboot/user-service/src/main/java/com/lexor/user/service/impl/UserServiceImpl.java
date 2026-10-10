package com.lexor.user.service.impl;

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
import com.lexor.user.service.UserService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserServiceImpl.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(readOnly = true)
    public UserResponse getCurrentUserProfile(UserPrincipal currentUser) {
        User user = findUserByUuidOrThrow(currentUser.getUuid());
        return mapToUserResponse(user);
    }

    @Override
    @Transactional
    public UserResponse updateCurrentUserProfile(UserPrincipal currentUser, UpdateProfileRequest request) {
        User user = findUserByUuidOrThrow(currentUser.getUuid());

        if (!user.getPhoneNumber().equals(request.getPhoneNumber())) {
            if (userRepository.existsByPhoneNumberAndUuidNot(request.getPhoneNumber(), user.getUuid())) {
                throw new DuplicateResourceException("Phone number already registered by another user");
            }
        }

        user.setFirstName(request.getFirstName().trim());
        user.setLastName(request.getLastName().trim());
        user.setPhoneNumber(request.getPhoneNumber().trim());
        user.setProfilePictureUrl(request.getProfilePictureUrl());

        User updatedUser = userRepository.save(user);
        return mapToUserResponse(updatedUser);
    }

    @Override
    @Transactional
    public UserResponse patchCurrentUserProfile(UserPrincipal currentUser, PatchProfileRequest request) {
        User user = findUserByUuidOrThrow(currentUser.getUuid());

        if (StringUtils.hasText(request.getFirstName())) {
            user.setFirstName(request.getFirstName().trim());
        }

        if (StringUtils.hasText(request.getLastName())) {
            user.setLastName(request.getLastName().trim());
        }

        if (StringUtils.hasText(request.getPhoneNumber()) && !user.getPhoneNumber().equals(request.getPhoneNumber().trim())) {
            String newPhone = request.getPhoneNumber().trim();
            if (userRepository.existsByPhoneNumberAndUuidNot(newPhone, user.getUuid())) {
                throw new DuplicateResourceException("Phone number already registered by another user");
            }
            user.setPhoneNumber(newPhone);
        }

        if (request.getProfilePictureUrl() != null) {
            user.setProfilePictureUrl(request.getProfilePictureUrl());
        }

        User updatedUser = userRepository.save(user);
        return mapToUserResponse(updatedUser);
    }

    @Override
    @Transactional
    public void deactivateCurrentUser(UserPrincipal currentUser) {
        User user = findUserByUuidOrThrow(currentUser.getUuid());

        if (user.getRole() == Role.ADMIN) {
            throw new ForbiddenException("Admin accounts cannot be self-deactivated via this endpoint");
        }

        user.setStatus(UserStatus.DEACTIVATED);
        userRepository.save(user);
        logger.info("User [{}] self-deactivated their account", user.getUuid());
    }

    @Override
    @Transactional
    public UserResponse createUser(AdminCreateUserRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        String phoneNumber = request.getPhoneNumber().trim();

        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("Email address already registered");
        }

        if (userRepository.existsByPhoneNumber(phoneNumber)) {
            throw new DuplicateResourceException("Phone number already registered");
        }

        UserStatus initialStatus = request.getStatus() != null ? request.getStatus() : UserStatus.ACTIVE;

        User user = User.builder()
                .uuid(UUID.randomUUID().toString())
                .firstName(request.getFirstName().trim())
                .lastName(request.getLastName().trim())
                .email(email)
                .phoneNumber(phoneNumber)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .status(initialStatus)
                .profilePictureUrl(request.getProfilePictureUrl())
                .emailVerified(false)
                .phoneVerified(false)
                .build();

        User savedUser = userRepository.save(user);
        logger.info("Admin created new user with UUID [{}] and role [{}]", savedUser.getUuid(), savedUser.getRole());

        return mapToUserResponse(savedUser);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<UserResponse> getUsers(
            Role role,
            UserStatus status,
            String search,
            int page,
            int size,
            String sortBy,
            String sortDir
    ) {
        int clampedSize = Math.max(1, Math.min(size, 100));
        int clampedPage = Math.max(0, page);

        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String validSortBy = validateSortProperty(sortBy);
        Sort sort = Sort.by(direction, validSortBy);

        Pageable pageable = PageRequest.of(clampedPage, clampedSize, sort);

        Specification<User> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (role != null) {
                predicates.add(cb.equal(root.get("role"), role));
            }

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            if (StringUtils.hasText(search)) {
                String searchTerm = "%" + search.trim().toLowerCase() + "%";
                Predicate firstNameMatch = cb.like(cb.lower(root.get("firstName")), searchTerm);
                Predicate lastNameMatch = cb.like(cb.lower(root.get("lastName")), searchTerm);
                Predicate emailMatch = cb.like(cb.lower(root.get("email")), searchTerm);

                predicates.add(cb.or(firstNameMatch, lastNameMatch, emailMatch));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<User> userPage = userRepository.findAll(spec, pageable);
        List<UserResponse> content = userPage.getContent().stream()
                .map(this::mapToUserResponse)
                .toList();

        return PagedResponse.<UserResponse>builder()
                .content(content)
                .page(userPage.getNumber())
                .size(userPage.getSize())
                .totalElements(userPage.getTotalElements())
                .totalPages(userPage.getTotalPages())
                .last(userPage.isLast())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserByUuid(String uuid) {
        User user = findUserByUuidOrThrow(uuid);
        return mapToUserResponse(user);
    }

    @Override
    @Transactional
    public UserResponse updateUserByUuid(String uuid, AdminUpdateUserRequest request) {
        User user = findUserByUuidOrThrow(uuid);

        String newEmail = request.getEmail().trim().toLowerCase();
        String newPhone = request.getPhoneNumber().trim();

        if (!user.getEmail().equalsIgnoreCase(newEmail)) {
            if (userRepository.existsByEmailAndUuidNot(newEmail, uuid)) {
                throw new DuplicateResourceException("Email address already registered by another user");
            }
        }

        if (!user.getPhoneNumber().equals(newPhone)) {
            if (userRepository.existsByPhoneNumberAndUuidNot(newPhone, uuid)) {
                throw new DuplicateResourceException("Phone number already registered by another user");
            }
        }

        user.setFirstName(request.getFirstName().trim());
        user.setLastName(request.getLastName().trim());
        user.setEmail(newEmail);
        user.setPhoneNumber(newPhone);
        user.setProfilePictureUrl(request.getProfilePictureUrl());

        User updatedUser = userRepository.save(user);
        logger.info("Admin updated user profile for UUID [{}]", uuid);

        return mapToUserResponse(updatedUser);
    }

    @Override
    @Transactional
    public UserResponse patchUserByUuid(String uuid, AdminPatchUserRequest request) {
        User user = findUserByUuidOrThrow(uuid);

        if (StringUtils.hasText(request.getFirstName())) {
            user.setFirstName(request.getFirstName().trim());
        }

        if (StringUtils.hasText(request.getLastName())) {
            user.setLastName(request.getLastName().trim());
        }

        if (StringUtils.hasText(request.getEmail())) {
            String newEmail = request.getEmail().trim().toLowerCase();
            if (!user.getEmail().equalsIgnoreCase(newEmail)) {
                if (userRepository.existsByEmailAndUuidNot(newEmail, uuid)) {
                    throw new DuplicateResourceException("Email address already registered by another user");
                }
                user.setEmail(newEmail);
            }
        }

        if (StringUtils.hasText(request.getPhoneNumber())) {
            String newPhone = request.getPhoneNumber().trim();
            if (!user.getPhoneNumber().equals(newPhone)) {
                if (userRepository.existsByPhoneNumberAndUuidNot(newPhone, uuid)) {
                    throw new DuplicateResourceException("Phone number already registered by another user");
                }
                user.setPhoneNumber(newPhone);
            }
        }

        if (request.getProfilePictureUrl() != null) {
            user.setProfilePictureUrl(request.getProfilePictureUrl());
        }

        User updatedUser = userRepository.save(user);
        logger.info("Admin patched user profile for UUID [{}]", uuid);

        return mapToUserResponse(updatedUser);
    }

    @Override
    @Transactional
    public UserResponse updateUserStatus(String adminUuid, String uuid, UpdateUserStatusRequest request) {
        User targetUser = findUserByUuidOrThrow(uuid);

        UserStatus currentStatus = targetUser.getStatus();
        UserStatus newStatus = request.getStatus();

        if (currentStatus == newStatus) {
            return mapToUserResponse(targetUser);
        }

        validateStatusTransition(currentStatus, newStatus);

        if (adminUuid.equals(uuid) && (newStatus == UserStatus.DEACTIVATED || newStatus == UserStatus.SUSPENDED)) {
            throw new BadRequestException("Administrators cannot change their own account status to " + newStatus);
        }

        if (targetUser.getRole() == Role.ADMIN && currentStatus == UserStatus.ACTIVE &&
                (newStatus == UserStatus.DEACTIVATED || newStatus == UserStatus.SUSPENDED)) {
            long activeAdminCount = userRepository.countByRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE);
            if (activeAdminCount <= 1) {
                throw new ForbiddenException("Cannot deactivate or suspend the last active administrator");
            }
        }

        targetUser.setStatus(newStatus);
        User updatedUser = userRepository.save(targetUser);

        logger.info("Admin [{}] changed status of user [{}] from [{}] to [{}]", adminUuid, uuid, currentStatus, newStatus);

        return mapToUserResponse(updatedUser);
    }

    @Override
    @Transactional
    public void deactivateUserByUuid(String adminUuid, String uuid) {
        User targetUser = findUserByUuidOrThrow(uuid);

        if (adminUuid.equals(uuid)) {
            throw new BadRequestException("Administrators cannot deactivate their own account via administrative deletion");
        }

        if (targetUser.getRole() == Role.ADMIN && targetUser.getStatus() == UserStatus.ACTIVE) {
            long activeAdminCount = userRepository.countByRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE);
            if (activeAdminCount <= 1) {
                throw new ForbiddenException("Cannot deactivate the last active administrator");
            }
        }

        targetUser.setStatus(UserStatus.DEACTIVATED);
        userRepository.save(targetUser);

        logger.info("Admin [{}] soft-deactivated user [{}]", adminUuid, uuid);
    }

    private User findUserByUuidOrThrow(String uuid) {
        return userRepository.findByUuid(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with UUID: " + uuid));
    }

    private void validateStatusTransition(UserStatus current, UserStatus target) {
        boolean valid = switch (current) {
            case PENDING -> target == UserStatus.ACTIVE || target == UserStatus.DEACTIVATED;
            case ACTIVE -> target == UserStatus.SUSPENDED || target == UserStatus.DEACTIVATED;
            case SUSPENDED -> target == UserStatus.ACTIVE || target == UserStatus.DEACTIVATED;
            case DEACTIVATED -> target == UserStatus.ACTIVE;
        };

        if (!valid) {
            throw new InvalidStatusTransitionException(
                    String.format("Invalid status transition from %s to %s", current, target)
            );
        }
    }

    private String validateSortProperty(String sortBy) {
        if (!StringUtils.hasText(sortBy)) {
            return "createdAt";
        }
        return switch (sortBy.trim()) {
            case "id", "uuid", "firstName", "lastName", "email", "phoneNumber", "role", "status", "createdAt", "updatedAt" -> sortBy.trim();
            default -> "createdAt";
        };
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
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
