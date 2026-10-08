package com.lexor.auth.repository;

import com.lexor.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByPhoneNumber(String phoneNumber);

    Optional<User> findByUuid(String uuid);

    boolean existsByEmail(String email);

    boolean existsByPhoneNumber(String phoneNumber);
}
