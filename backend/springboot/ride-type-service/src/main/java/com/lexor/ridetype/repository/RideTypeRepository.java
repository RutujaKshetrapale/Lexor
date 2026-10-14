package com.lexor.ridetype.repository;

import com.lexor.ridetype.entity.RideType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RideTypeRepository extends JpaRepository<RideType, Long> {

    Optional<RideType> findByUuid(String uuid);

    Optional<RideType> findByUuidAndIsActiveTrue(String uuid);

    Optional<RideType> findByCodeIgnoreCase(String code);

    Optional<RideType> findByNameIgnoreCase(String name);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByCodeIgnoreCaseAndUuidNot(String code, String uuid);

    boolean existsByNameIgnoreCaseAndUuidNot(String name, String uuid);

    List<RideType> findByIsActiveTrueOrderByIdAsc();

    List<RideType> findAllByOrderByIdAsc();

    long countByIsActiveTrue();
}
