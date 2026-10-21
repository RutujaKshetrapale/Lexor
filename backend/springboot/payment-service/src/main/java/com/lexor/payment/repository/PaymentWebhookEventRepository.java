package com.lexor.payment.repository;

import com.lexor.payment.entity.PaymentWebhookEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEventEntity, Long> {

    Optional<PaymentWebhookEventEntity> findByProviderAndProviderEventId(String provider, String providerEventId);
}
