package com.lexor.notification.repository;

import com.lexor.notification.entity.NotificationDeliveryEntity;
import com.lexor.notification.enums.DeliveryStatus;
import com.lexor.notification.enums.NotificationChannel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationDeliveryRepository extends JpaRepository<NotificationDeliveryEntity, Long> {

    Optional<NotificationDeliveryEntity> findByDeliveryUuid(String deliveryUuid);

    List<NotificationDeliveryEntity> findByNotificationUuid(String notificationUuid);

    long countByStatus(DeliveryStatus status);

    long countByChannel(NotificationChannel channel);

    long countByChannelAndStatus(NotificationChannel channel, DeliveryStatus status);
}
