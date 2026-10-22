package com.lexor.feedback.repository;

import com.lexor.feedback.entity.RiderRatingSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RiderRatingSummaryRepository extends JpaRepository<RiderRatingSummary, Long> {

    Optional<RiderRatingSummary> findByRiderUuid(String riderUuid);
}
