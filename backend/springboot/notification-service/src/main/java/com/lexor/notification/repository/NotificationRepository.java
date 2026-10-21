package com.lexor.notification.repository;

import com.lexor.notification.entity.NotificationEntity;
import com.lexor.notification.enums.NotificationCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<NotificationEntity, Long> {

    Optional<NotificationEntity> findByNotificationUuid(String notificationUuid);

    Page<NotificationEntity> findByRecipientUuidOrderByCreatedAtDesc(String recipientUuid, Pageable pageable);

    Page<NotificationEntity> findByRecipientUuidAndIsReadOrderByCreatedAtDesc(String recipientUuid, Boolean isRead, Pageable pageable);

    Page<NotificationEntity> findByRecipientUuidAndCategoryOrderByCreatedAtDesc(String recipientUuid, NotificationCategory category, Pageable pageable);

    Page<NotificationEntity> findByRecipientUuidAndCategoryAndIsReadOrderByCreatedAtDesc(String recipientUuid, NotificationCategory category, Boolean isRead, Pageable pageable);

    long countByRecipientUuidAndIsReadFalse(String recipientUuid);

    @Modifying
    @Query("UPDATE NotificationEntity n SET n.isRead = true, n.readAt = :readAt WHERE n.recipientUuid = :recipientUuid AND n.isRead = false")
    int markAllAsReadForUser(@Param("recipientUuid") String recipientUuid, @Param("readAt") LocalDateTime readAt);

    boolean existsBySourceEventId(String sourceEventId);

    boolean existsByIdempotencyKey(String idempotencyKey);
}
