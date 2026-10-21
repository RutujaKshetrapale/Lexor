package com.lexor.payment.repository;

import com.lexor.payment.entity.FinancialLedgerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FinancialLedgerRepository extends JpaRepository<FinancialLedgerEntity, Long> {

    List<FinancialLedgerEntity> findByPaymentUuidOrderByCreatedAtAsc(String paymentUuid);

    List<FinancialLedgerEntity> findByBookingUuidOrderByCreatedAtAsc(String bookingUuid);

    List<FinancialLedgerEntity> findByDriverUuidOrderByCreatedAtAsc(String driverUuid);
}
