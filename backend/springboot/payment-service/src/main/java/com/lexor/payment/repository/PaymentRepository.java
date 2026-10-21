package com.lexor.payment.repository;

import com.lexor.payment.entity.PaymentEntity;
import com.lexor.payment.enums.PaymentMethod;
import com.lexor.payment.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<PaymentEntity, Long> {

    Optional<PaymentEntity> findByPaymentUuid(String paymentUuid);

    Optional<PaymentEntity> findByBookingUuid(String bookingUuid);

    Optional<PaymentEntity> findByProviderOrderId(String providerOrderId);

    Optional<PaymentEntity> findByProviderPaymentId(String providerPaymentId);

    Page<PaymentEntity> findByRiderUuid(String riderUuid, Pageable pageable);

    @Query("SELECT p FROM PaymentEntity p WHERE " +
           "(:status IS NULL OR p.status = :status) AND " +
           "(:method IS NULL OR p.paymentMethod = :method) AND " +
           "(:riderUuid IS NULL OR p.riderUuid = :riderUuid) AND " +
           "(:driverUuid IS NULL OR p.driverUuid = :driverUuid)")
    Page<PaymentEntity> findFiltered(
            @Param("status") PaymentStatus status,
            @Param("method") PaymentMethod method,
            @Param("riderUuid") String riderUuid,
            @Param("driverUuid") String driverUuid,
            Pageable pageable
    );
}
