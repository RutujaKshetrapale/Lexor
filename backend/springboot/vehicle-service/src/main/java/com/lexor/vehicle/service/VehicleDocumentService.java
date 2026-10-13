package com.lexor.vehicle.service;

import com.lexor.vehicle.dto.response.VehicleDocumentResponse;
import com.lexor.vehicle.entity.VehicleDocumentType;
import com.lexor.vehicle.security.UserPrincipal;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

public interface VehicleDocumentService {

    VehicleDocumentResponse uploadDocument(
            UserPrincipal currentUser,
            String vehicleUuid,
            VehicleDocumentType documentType,
            String documentNumber,
            LocalDate issueDate,
            LocalDate expiryDate,
            String issuingAuthority,
            MultipartFile file
    );

    List<VehicleDocumentResponse> getVehicleDocuments(UserPrincipal currentUser, String vehicleUuid);

    VehicleDocumentResponse getDocumentById(UserPrincipal currentUser, String vehicleUuid, Long documentId);

    Resource downloadDocument(UserPrincipal currentUser, String vehicleUuid, Long documentId);

    VehicleDocumentResponse resubmitDocument(
            UserPrincipal currentUser,
            String vehicleUuid,
            Long documentId,
            String documentNumber,
            LocalDate issueDate,
            LocalDate expiryDate,
            String issuingAuthority,
            MultipartFile file
    );

    void deleteDocument(UserPrincipal currentUser, String vehicleUuid, Long documentId);
}
