package com.lexor.payment.repository;

import com.lexor.payment.entity.DriverPayoutEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DriverPayoutRepository extends JpaRepository<DriverPayoutEntity, Long> {

    List<DriverPayoutEntity> findByDriverUuidOrderByCreatedAtDesc(String driverUuid);

    Optional<DriverPayoutEntity> findByPayoutUuid(String payoutUuid);
}
