package com.lexor.vehicle.controller;

import com.lexor.vehicle.dto.request.AssignDriverRequest;
import com.lexor.vehicle.dto.request.ReviewVehicleDocumentRequest;
import com.lexor.vehicle.dto.request.ReviewVehicleRequest;
import com.lexor.vehicle.dto.request.SuspendVehicleRequest;
import com.lexor.vehicle.dto.response.DriverVehicleAssignmentResponse;
import com.lexor.vehicle.dto.response.ErrorResponse;
import com.lexor.vehicle.dto.response.PagedResponse;
import com.lexor.vehicle.dto.response.VehicleDocumentResponse;
import com.lexor.vehicle.dto.response.VehicleResponse;
import com.lexor.vehicle.dto.response.VehicleVerificationHistoryResponse;
import com.lexor.vehicle.entity.VehicleVerificationStatus;
import com.lexor.vehicle.security.UserPrincipal;
import com.lexor.vehicle.service.VehicleAdminService;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/vehicles")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Fleet & Vehicle Management", description = "Endpoints for administrators to review vehicles, documents, decisions, and driver assignments")
@SecurityRequirement(name = "BearerAuthentication")
public class AdminVehicleController {

    private final VehicleAdminService adminService;

    @Operation(summary = "List vehicles with pagination and filtering", description = "Retrieves paginated vehicle fleet with filters for verification status, active status, vehicle category, and keyword search.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Vehicles retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires ADMIN role",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<PagedResponse<VehicleResponse>> listVehicles(
            @RequestParam(value = "verificationStatus", required = false) VehicleVerificationStatus verificationStatus,
            @RequestParam(value = "isActive", required = false) Boolean isActive,
            @RequestParam(value = "rideTypeCategory", required = false) String rideTypeCategory,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "sortBy", defaultValue = "createdAt") String sortBy,
            @RequestParam(value = "sortDir", defaultValue = "desc") String sortDir
    ) {
        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        PagedResponse<VehicleResponse> response = adminService.listVehicles(
                verificationStatus, isActive, rideTypeCategory, search, pageable
        );
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "List vehicles pending verification", description = "Retrieves paginated vehicles currently awaiting administrative review and approval.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pending vehicles retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires ADMIN role",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/pending")
    public ResponseEntity<PagedResponse<VehicleResponse>> listPendingVehicles(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "sortBy", defaultValue = "createdAt") String sortBy,
            @RequestParam(value = "sortDir", defaultValue = "asc") String sortDir
    ) {
        Sort sort = sortDir.equalsIgnoreCase("desc") ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);

        PagedResponse<VehicleResponse> response = adminService.listPendingVehicles(pageable);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get full vehicle details by UUID", description = "Retrieves comprehensive vehicle details for administrative review.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Vehicle details retrieved successfully",
                    content = @Content(schema = @Schema(implementation = VehicleResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires ADMIN role",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Vehicle not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{vehicleUuid}")
    public ResponseEntity<VehicleResponse> getVehicleDetails(@PathVariable String vehicleUuid) {
        VehicleResponse response = adminService.getVehicleDetails(vehicleUuid);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "List vehicle compliance documents", description = "Retrieves all current compliance documents submitted for this vehicle.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Documents retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires ADMIN role",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Vehicle not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{vehicleUuid}/documents")
    public ResponseEntity<List<VehicleDocumentResponse>> getVehicleDocuments(@PathVariable String vehicleUuid) {
        List<VehicleDocumentResponse> documents = adminService.getVehicleDocuments(vehicleUuid);
        return ResponseEntity.ok(documents);
    }

    @Operation(summary = "Securely download a vehicle document as admin", description = "Streams document binary content for verification review.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Document downloaded successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires ADMIN role",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Document not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{vehicleUuid}/documents/{documentId}/download")
    public ResponseEntity<Resource> downloadDocument(
            @PathVariable String vehicleUuid,
            @PathVariable Long documentId
    ) throws IOException {
        VehicleDocumentResponse doc = adminService.getVehicleDocuments(vehicleUuid).stream()
                .filter(d -> d.getId().equals(documentId))
                .findFirst()
                .orElse(null);

        Resource resource = adminService.downloadDocument(vehicleUuid, documentId);

        String contentType = doc != null ? doc.getContentType() : null;
        if (contentType == null) {
            contentType = Files.probeContentType(Paths.get(resource.getFile().getAbsolutePath()));
            if (contentType == null) {
                contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
            }
        }

        String fileName = doc != null ? doc.getFileName() : "document";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .body(resource);
    }

    @Operation(summary = "Review an individual vehicle document", description = "Approves or rejects an individual compliance document (RC, Insurance, PUC) and records audit trail.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Document reviewed successfully",
                    content = @Content(schema = @Schema(implementation = VehicleDocumentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request - Validation error",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires ADMIN role",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Document not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{vehicleUuid}/documents/{documentId}/review")
    public ResponseEntity<VehicleDocumentResponse> reviewDocument(
            @AuthenticationPrincipal UserPrincipal admin,
            @PathVariable String vehicleUuid,
            @PathVariable Long documentId,
            @Valid @RequestBody ReviewVehicleDocumentRequest request
    ) {
        VehicleDocumentResponse response = adminService.reviewDocument(admin, vehicleUuid, documentId, request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Review and verify/reject a vehicle", description = "Makes an administrative decision on vehicle verification. Approval requires all mandatory documents to be VERIFIED.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Vehicle verification decision recorded",
                    content = @Content(schema = @Schema(implementation = VehicleResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request - Missing documents or invalid state transition",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires ADMIN role",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Vehicle not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{vehicleUuid}/verification")
    public ResponseEntity<VehicleResponse> reviewVehicle(
            @AuthenticationPrincipal UserPrincipal admin,
            @PathVariable String vehicleUuid,
            @Valid @RequestBody ReviewVehicleRequest request
    ) {
        VehicleResponse response = adminService.reviewVehicle(admin, vehicleUuid, request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Suspend or reinstate a vehicle", description = "Suspends an operational vehicle or reinstates a previously suspended vehicle.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Vehicle suspension/reinstatement applied",
                    content = @Content(schema = @Schema(implementation = VehicleResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request - Invalid action or reason",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires ADMIN role",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Vehicle not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{vehicleUuid}/suspension")
    public ResponseEntity<VehicleResponse> suspendOrReinstateVehicle(
            @AuthenticationPrincipal UserPrincipal admin,
            @PathVariable String vehicleUuid,
            @Valid @RequestBody SuspendVehicleRequest request
    ) {
        VehicleResponse response = adminService.suspendOrReinstateVehicle(admin, vehicleUuid, request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "View vehicle verification audit history", description = "Retrieves complete audit trail of verification actions and reviewer notes.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Verification history retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires ADMIN role",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Vehicle not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{vehicleUuid}/verification-history")
    public ResponseEntity<List<VehicleVerificationHistoryResponse>> getVerificationHistory(@PathVariable String vehicleUuid) {
        List<VehicleVerificationHistoryResponse> response = adminService.getVerificationHistory(vehicleUuid);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Assign a driver to a vehicle", description = "Assigns an eligible, verified driver to a verified vehicle. Deactivates any previous active assignment.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Driver assigned successfully",
                    content = @Content(schema = @Schema(implementation = DriverVehicleAssignmentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request - Ineligible driver or vehicle",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires ADMIN role",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Vehicle or driver not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{vehicleUuid}/assignments")
    public ResponseEntity<DriverVehicleAssignmentResponse> assignDriver(
            @AuthenticationPrincipal UserPrincipal admin,
            @PathVariable String vehicleUuid,
            @Valid @RequestBody AssignDriverRequest request
    ) {
        DriverVehicleAssignmentResponse response = adminService.assignDriver(admin, vehicleUuid, request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Terminate a driver-vehicle assignment", description = "Terminates an active assignment for this vehicle.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Assignment terminated successfully",
                    content = @Content(schema = @Schema(implementation = DriverVehicleAssignmentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request - Assignment does not belong to vehicle",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires ADMIN role",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Assignment not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{vehicleUuid}/assignments/{assignmentId}")
    public ResponseEntity<DriverVehicleAssignmentResponse> unassignDriver(
            @AuthenticationPrincipal UserPrincipal admin,
            @PathVariable String vehicleUuid,
            @PathVariable Long assignmentId,
            @RequestParam(value = "reason", required = false) String reason
    ) {
        DriverVehicleAssignmentResponse response = adminService.unassignDriver(admin, vehicleUuid, assignmentId, reason);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "View driver-vehicle assignment history", description = "Retrieves complete history of driver assignments for this vehicle.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Assignment history retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires ADMIN role",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Vehicle not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{vehicleUuid}/assignments")
    public ResponseEntity<List<DriverVehicleAssignmentResponse>> getAssignmentHistory(@PathVariable String vehicleUuid) {
        List<DriverVehicleAssignmentResponse> response = adminService.getAssignmentHistory(vehicleUuid);
        return ResponseEntity.ok(response);
    }
}
