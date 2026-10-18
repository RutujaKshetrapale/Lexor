package com.lexor.booking.repository;

import com.lexor.booking.entity.MatchingAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MatchingAttemptRepository extends JpaRepository<MatchingAttempt, Long> {

    Optional<MatchingAttempt> findFirstByRideRequestUuidOrderByAttemptNumberDesc(String rideRequestUuid);

    Optional<MatchingAttempt> findByAttemptUuid(String attemptUuid);
}
