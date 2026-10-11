package com.lexor.driver.service;

import com.lexor.driver.dto.response.DocumentResponse;
import com.lexor.driver.entity.DocumentType;
import com.lexor.driver.security.UserPrincipal;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

public interface DriverDocumentService {

    DocumentResponse uploadDocument(
            UserPrincipal currentUser,
            DocumentType documentType,
            String documentNumber,
            LocalDate issueDate,
            LocalDate expiryDate,
            String issuingAuthority,
            MultipartFile file
    );

    List<DocumentResponse> getDriverDocuments(UserPrincipal currentUser);

    DocumentResponse getDriverDocumentById(UserPrincipal currentUser, Long documentId);

    Resource downloadDocument(UserPrincipal currentUser, Long documentId);

    DocumentResponse resubmitDocument(
            UserPrincipal currentUser,
            Long documentId,
            MultipartFile file,
            String documentNumber,
            LocalDate issueDate,
            LocalDate expiryDate,
            String issuingAuthority
    );

    void deleteDocument(UserPrincipal currentUser, Long documentId);
}
