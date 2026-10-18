package com.lexor.booking.repository;

import com.lexor.booking.entity.RiderProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RiderProfileRepository extends JpaRepository<RiderProfile, Long> {

    @Query("SELECT rp FROM RiderProfile rp JOIN rp.user u WHERE u.uuid = :userUuid")
    Optional<RiderProfile> findByUserUuid(@Param("userUuid") String userUuid);

    @Query("SELECT rp FROM RiderProfile rp WHERE rp.user.id = :userId")
    Optional<RiderProfile> findByUserId(@Param("userId") Long userId);
}
