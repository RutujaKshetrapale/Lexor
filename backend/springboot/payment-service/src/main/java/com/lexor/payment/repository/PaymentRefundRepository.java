package com.lexor.payment.repository;

import com.lexor.payment.entity.PaymentRefundEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRefundRepository extends JpaRepository<PaymentRefundEntity, Long> {

    List<PaymentRefundEntity> findByPaymentUuidOrderByCreatedAtDesc(String paymentUuid);

    Optional<PaymentRefundEntity> findByRefundUuid(String refundUuid);
}
