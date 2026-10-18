package com.lexor.booking.repository;

import com.lexor.booking.entity.RideType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RideTypeRepository extends JpaRepository<RideType, Long> {

    Optional<RideType> findByUuid(String uuid);

    Optional<RideType> findByCode(String code);
}
