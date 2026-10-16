package com.lexor.riderequest.repository;

import com.lexor.riderequest.entity.RideType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RideTypeRepository extends JpaRepository<RideType, Long> {

    Optional<RideType> findByCode(String code);

    Optional<RideType> findByName(String name);
}
