package com.lexor.driver.repository;

import com.lexor.driver.entity.DocumentType;
import com.lexor.driver.entity.DocumentVerificationStatus;
import com.lexor.driver.entity.DriverDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DriverDocumentRepository extends JpaRepository<DriverDocument, Long>, JpaSpecificationExecutor<DriverDocument> {

    List<DriverDocument> findByDriverIdAndIsCurrentTrue(Long driverId);

    List<DriverDocument> findByDriverId(Long driverId);

    Optional<DriverDocument> findByDriverIdAndDocumentTypeAndIsCurrentTrue(Long driverId, DocumentType documentType);

    Optional<DriverDocument> findByIdAndDriverId(Long id, Long driverId);

    List<DriverDocument> findByDriverIdAndDocumentTypeOrderByVersionDesc(Long driverId, DocumentType documentType);
}
