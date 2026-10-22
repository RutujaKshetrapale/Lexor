package com.lexor.feedback.repository;

import com.lexor.feedback.entity.DriverRatingSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DriverRatingSummaryRepository extends JpaRepository<DriverRatingSummary, Long> {

    Optional<DriverRatingSummary> findByDriverUuid(String driverUuid);
}
