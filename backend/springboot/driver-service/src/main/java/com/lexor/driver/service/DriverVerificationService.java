package com.lexor.driver.service;

import com.lexor.driver.dto.request.DriverSuspensionRequest;
import com.lexor.driver.dto.request.ReviewDocumentRequest;
import com.lexor.driver.dto.request.VerifyDriverRequest;
import com.lexor.driver.dto.response.DocumentResponse;
import com.lexor.driver.dto.response.DriverProfileResponse;
import com.lexor.driver.dto.response.PagedResponse;
import com.lexor.driver.dto.response.VerificationHistoryResponse;
import com.lexor.driver.entity.DocumentType;
import com.lexor.driver.entity.DocumentVerificationStatus;
import com.lexor.driver.entity.DriverStatus;
import com.lexor.driver.entity.OnboardingStatus;
import com.lexor.driver.entity.UserStatus;
import com.lexor.driver.security.UserPrincipal;
import org.springframework.core.io.Resource;

import java.util.List;

public interface DriverVerificationService {

    PagedResponse<DriverProfileResponse> listDrivers(
            OnboardingStatus verificationStatus,
            UserStatus accountStatus,
            DriverStatus availabilityStatus,
            String search,
            int page,
            int size,
            String sortBy,
            String sortDir
    );

    DriverProfileResponse getDriverByUuid(String driverUuid);

    List<DocumentResponse> getDriverDocumentsByUuid(String driverUuid);

    PagedResponse<DocumentResponse> listAwaitingDocuments(
            DocumentVerificationStatus status,
            DocumentType documentType,
            String driverUuid,
            int page,
            int size
    );

    Resource downloadDocumentForAdmin(Long documentId);

    DocumentResponse reviewDocument(UserPrincipal adminUser, Long documentId, ReviewDocumentRequest request);

    DriverProfileResponse reviewDriverVerification(UserPrincipal adminUser, String driverUuid, VerifyDriverRequest request);

    DriverProfileResponse updateDriverSuspension(UserPrincipal adminUser, String driverUuid, DriverSuspensionRequest request);

    List<VerificationHistoryResponse> getDriverVerificationHistory(String driverUuid);
}
