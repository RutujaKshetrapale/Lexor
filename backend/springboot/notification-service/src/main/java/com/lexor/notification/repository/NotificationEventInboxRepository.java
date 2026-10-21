package com.lexor.notification.repository;

import com.lexor.notification.entity.NotificationEventInboxEntity;
import com.lexor.notification.enums.EventInboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationEventInboxRepository extends JpaRepository<NotificationEventInboxEntity, Long> {

    Optional<NotificationEventInboxEntity> findByEventId(String eventId);

    boolean existsByEventId(String eventId);

    List<NotificationEventInboxEntity> findByStatusOrderByReceivedAtAsc(EventInboxStatus status);

    long countByStatus(EventInboxStatus status);
}
