package com.lexor.notification.repository;

import com.lexor.notification.entity.NotificationDeviceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationDeviceRepository extends JpaRepository<NotificationDeviceEntity, Long> {

    Optional<NotificationDeviceEntity> findByDeviceUuid(String deviceUuid);

    List<NotificationDeviceEntity> findByUserUuidAndIsActiveTrue(String userUuid);

    Optional<NotificationDeviceEntity> findByUserUuidAndFcmToken(String userUuid, String fcmToken);

    Optional<NotificationDeviceEntity> findByFcmToken(String fcmToken);

    long countByIsActiveTrue();
}
