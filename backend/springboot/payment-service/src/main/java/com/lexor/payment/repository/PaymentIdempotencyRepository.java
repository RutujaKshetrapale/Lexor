package com.lexor.payment.repository;

import com.lexor.payment.entity.PaymentIdempotencyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentIdempotencyRepository extends JpaRepository<PaymentIdempotencyEntity, Long> {

    Optional<PaymentIdempotencyEntity> findByCallerUuidAndIdempotencyKey(String callerUuid, String idempotencyKey);
}
