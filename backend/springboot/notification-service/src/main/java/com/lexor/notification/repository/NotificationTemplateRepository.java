package com.lexor.notification.repository;

import com.lexor.notification.entity.NotificationTemplateEntity;
import com.lexor.notification.enums.NotificationChannel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplateEntity, Long> {

    Optional<NotificationTemplateEntity> findByTemplateCodeAndChannelAndLocaleAndIsActiveTrue(
        String templateCode,
        NotificationChannel channel,
        String locale
    );

    Optional<NotificationTemplateEntity> findByTemplateCodeAndChannelAndLocale(
        String templateCode,
        NotificationChannel channel,
        String locale
    );
}
