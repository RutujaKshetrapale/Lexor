package com.lexor.driver.controller;

import com.lexor.driver.dto.request.DriverSuspensionRequest;
import com.lexor.driver.dto.request.ReviewDocumentRequest;
import com.lexor.driver.dto.request.VerifyDriverRequest;
import com.lexor.driver.dto.response.DocumentResponse;
import com.lexor.driver.dto.response.DriverProfileResponse;
import com.lexor.driver.dto.response.ErrorResponse;
import com.lexor.driver.dto.response.PagedResponse;
import com.lexor.driver.dto.response.VerificationHistoryResponse;
import com.lexor.driver.entity.DocumentType;
import com.lexor.driver.entity.DocumentVerificationStatus;
import com.lexor.driver.entity.DriverStatus;
import com.lexor.driver.entity.OnboardingStatus;
import com.lexor.driver.entity.UserStatus;
import com.lexor.driver.security.UserPrincipal;
import com.lexor.driver.service.DriverVerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Driver Verification & Review", description = "Endpoints for administrators to review documents, make verification decisions, and manage driver eligibility")
@SecurityRequirement(name = "BearerAuthentication")
public class AdminDriverController {

    private final DriverVerificationService driverVerificationService;

    @Operation(summary = "List drivers with pagination and filtering", description = "Retrieves paginated driver list with filters for onboarding status, account status, availability, and search keyword.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Drivers retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires ADMIN role",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/drivers")
    public ResponseEntity<PagedResponse<DriverProfileResponse>> listDrivers(
            @RequestParam(value = "verificationStatus", required = false) OnboardingStatus verificationStatus,
            @RequestParam(value = "accountStatus", required = false) UserStatus accountStatus,
            @RequestParam(value = "availabilityStatus", required = false) DriverStatus availabilityStatus,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "sortBy", defaultValue = "createdAt") String sortBy,
            @RequestParam(value = "sortDir", defaultValue = "desc") String sortDir
    ) {
        PagedResponse<DriverProfileResponse> response = driverVerificationService.listDrivers(
                verificationStatus, accountStatus, availabilityStatus, search, page, size, sortBy, sortDir
        );
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get driver administrative profile by user UUID", description = "Retrieves a driver's comprehensive profile and onboarding summary by UUID.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Driver profile retrieved successfully",
                    content = @Content(schema = @Schema(implementation = DriverProfileResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires ADMIN role",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Driver profile not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/drivers/{driverUuid}")
    public ResponseEntity<DriverProfileResponse> getDriverByUuid(@PathVariable String driverUuid) {
        DriverProfileResponse response = driverVerificationService.getDriverByUuid(driverUuid);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "List all documents submitted by driver", description = "Retrieves current and historical versions of compliance documents submitted by the driver.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Documents retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires ADMIN role",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Driver not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/drivers/{driverUuid}/documents")
    public ResponseEntity<List<DocumentResponse>> getDriverDocumentsByUuid(@PathVariable String driverUuid) {
        List<DocumentResponse> documents = driverVerificationService.getDriverDocumentsByUuid(driverUuid);
        return ResponseEntity.ok(documents);
    }

    @Operation(summary = "List compliance documents awaiting review", description = "Retrieves documents awaiting administrative inspection with pagination and optional filters.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Documents listed successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires ADMIN role",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/driver-documents")
    public ResponseEntity<PagedResponse<DocumentResponse>> listAwaitingDocuments(
            @RequestParam(value = "status", required = false) DocumentVerificationStatus status,
            @RequestParam(value = "documentType", required = false) DocumentType documentType,
            @RequestParam(value = "driverUuid", required = false) String driverUuid,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size
    ) {
        PagedResponse<DocumentResponse> response = driverVerificationService.listAwaitingDocuments(
                status, documentType, driverUuid, page, size
        );
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Securely retrieve a document file for admin review", description = "Authorizes and streams the document binary for administrative verification.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Document binary retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires ADMIN role",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Document not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/driver-documents/{documentId}/download")
    public ResponseEntity<Resource> downloadDocumentForAdmin(@PathVariable Long documentId) throws IOException {
        Resource resource = driverVerificationService.downloadDocumentForAdmin(documentId);
        String contentType = Files.probeContentType(Paths.get(resource.getFile().getAbsolutePath()));
        if (contentType == null) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }

    @Operation(summary = "Review and verify/reject a document", description = "Performs an administrative decision (VERIFIED, REJECTED, RESUBMISSION_REQUIRED) on a document. Rejection requires a mandatory reason.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Document status updated successfully",
                    content = @Content(schema = @Schema(implementation = DocumentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request - Missing mandatory rejection reason",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires ADMIN role",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Document not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/driver-documents/{documentId}/status")
    public ResponseEntity<DocumentResponse> reviewDocument(
            @AuthenticationPrincipal UserPrincipal adminUser,
            @PathVariable Long documentId,
            @Valid @RequestBody ReviewDocumentRequest request
    ) {
        DocumentResponse response = driverVerificationService.reviewDocument(adminUser, documentId, request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get driver verification audit history", description = "Retrieves full audit log of all administrative decisions for a specific driver.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Verification history retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires ADMIN role",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/drivers/{driverUuid}/verification-history")
    public ResponseEntity<List<VerificationHistoryResponse>> getDriverVerificationHistory(@PathVariable String driverUuid) {
        List<VerificationHistoryResponse> history = driverVerificationService.getDriverVerificationHistory(driverUuid);
        return ResponseEntity.ok(history);
    }

    @Operation(summary = "Perform driver-level verification decision", description = "Approves (VERIFIED) or rejects driver onboarding. Verifying requires all mandatory documents to be in VERIFIED state.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Driver verification decision applied successfully",
                    content = @Content(schema = @Schema(implementation = DriverProfileResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request - Incomplete profile or unverified documents",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires ADMIN role",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Driver not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/drivers/{driverUuid}/verification-status")
    public ResponseEntity<DriverProfileResponse> reviewDriverVerification(
            @AuthenticationPrincipal UserPrincipal adminUser,
            @PathVariable String driverUuid,
            @Valid @RequestBody VerifyDriverRequest request
    ) {
        DriverProfileResponse response = driverVerificationService.reviewDriverVerification(adminUser, driverUuid, request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Suspend or reinstate driver eligibility", description = "Administratively suspends or reinstates driver eligibility and updates availability status accordingly.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Suspension status updated successfully",
                    content = @Content(schema = @Schema(implementation = DriverProfileResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request - Missing reason",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires ADMIN role",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Driver not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/drivers/{driverUuid}/suspension")
    public ResponseEntity<DriverProfileResponse> updateDriverSuspension(
            @AuthenticationPrincipal UserPrincipal adminUser,
            @PathVariable String driverUuid,
            @Valid @RequestBody DriverSuspensionRequest request
    ) {
        DriverProfileResponse response = driverVerificationService.updateDriverSuspension(adminUser, driverUuid, request);
        return ResponseEntity.ok(response);
    }
}
