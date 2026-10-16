package com.lexor.riderequest.repository;

import com.lexor.riderequest.entity.RiderProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RiderProfileRepository extends JpaRepository<RiderProfile, Long> {

    Optional<RiderProfile> findByUserId(Long userId);

    @Query("SELECT rp FROM RiderProfile rp JOIN FETCH rp.user u WHERE u.uuid = :userUuid")
    Optional<RiderProfile> findByUserUuid(@Param("userUuid") String userUuid);
}
