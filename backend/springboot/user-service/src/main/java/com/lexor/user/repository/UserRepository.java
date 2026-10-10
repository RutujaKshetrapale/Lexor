package com.lexor.user.repository;

import com.lexor.user.entity.Role;
import com.lexor.user.entity.User;
import com.lexor.user.entity.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByUuid(String uuid);

    Optional<User> findByEmail(String email);

    Optional<User> findByPhoneNumber(String phoneNumber);

    boolean existsByEmail(String email);

    boolean existsByPhoneNumber(String phoneNumber);

    boolean existsByEmailAndUuidNot(String email, String uuid);

    boolean existsByPhoneNumberAndUuidNot(String phoneNumber, String uuid);

    long countByRoleAndStatus(Role role, UserStatus status);
}
