package com.lexor.matching.repository;

import com.lexor.matching.entity.RideType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RideTypeRepository extends JpaRepository<RideType, Long> {

    Optional<RideType> findByUuid(String uuid);

    Optional<RideType> findByCode(String code);
}
