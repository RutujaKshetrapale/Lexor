package com.lexor.riderequest.repository;

import com.lexor.riderequest.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUuid(String uuid);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
