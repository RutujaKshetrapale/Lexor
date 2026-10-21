package com.lexor.notification.repository;

import com.lexor.notification.entity.NotificationPreferenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreferenceEntity, Long> {

    Optional<NotificationPreferenceEntity> findByUserUuid(String userUuid);

    Optional<NotificationPreferenceEntity> findByPreferenceUuid(String preferenceUuid);

    boolean existsByUserUuid(String userUuid);
}
