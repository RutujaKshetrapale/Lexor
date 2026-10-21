package com.lexor.payment.repository;

import com.lexor.payment.entity.PaymentTransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransactionEntity, Long> {

    List<PaymentTransactionEntity> findByPaymentUuidOrderByCreatedAtDesc(String paymentUuid);

    Optional<PaymentTransactionEntity> findByTransactionUuid(String transactionUuid);
}
