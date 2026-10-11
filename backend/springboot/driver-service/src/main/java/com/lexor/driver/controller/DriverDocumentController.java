package com.lexor.driver.controller;

import com.lexor.driver.dto.response.DocumentResponse;
import com.lexor.driver.dto.response.ErrorResponse;
import com.lexor.driver.entity.DocumentType;
import com.lexor.driver.security.UserPrincipal;
import com.lexor.driver.service.DriverDocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/drivers/me/documents")
@RequiredArgsConstructor
@PreAuthorize("hasRole('DRIVER')")
@Tag(name = "Driver Document Management", description = "Endpoints for drivers to securely upload, inspect, download, and resubmit compliance documents")
@SecurityRequirement(name = "BearerAuthentication")
public class DriverDocumentController {

    private final DriverDocumentService driverDocumentService;

    @Operation(summary = "Upload a driver compliance document", description = "Uploads a document (PDF, PNG, JPEG, WEBP) using multipart form data. Inspects file content and resets previous approval on replacement.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Document uploaded successfully",
                    content = @Content(schema = @Schema(implementation = DocumentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request - Invalid file or validation error",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires DRIVER role",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "413", description = "Payload Too Large - File exceeds maximum size",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> uploadDocument(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Parameter(description = "Document type", required = true)
            @RequestParam("documentType") DocumentType documentType,
            @Parameter(description = "Document identification number (optional)")
            @RequestParam(value = "documentNumber", required = false) String documentNumber,
            @Parameter(description = "Document issue date (optional)")
            @RequestParam(value = "issueDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate issueDate,
            @Parameter(description = "Document expiry date (optional)")
            @RequestParam(value = "expiryDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiryDate,
            @Parameter(description = "Issuing authority (optional)")
            @RequestParam(value = "issuingAuthority", required = false) String issuingAuthority,
            @Parameter(description = "File binary payload (PDF, JPEG, PNG, WEBP)", required = true)
            @RequestParam("file") MultipartFile file
    ) {
        DocumentResponse response = driverDocumentService.uploadDocument(
                currentUser, documentType, documentNumber, issueDate, expiryDate, issuingAuthority, file
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "List driver's current documents", description = "Retrieves all currently active compliance documents submitted by the authenticated driver.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Documents listed successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires DRIVER role",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<List<DocumentResponse>> getDriverDocuments(@AuthenticationPrincipal UserPrincipal currentUser) {
        List<DocumentResponse> documents = driverDocumentService.getDriverDocuments(currentUser);
        return ResponseEntity.ok(documents);
    }

    @Operation(summary = "Get document metadata by ID", description = "Retrieves metadata for a specific document owned by the authenticated driver.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Document metadata retrieved successfully",
                    content = @Content(schema = @Schema(implementation = DocumentResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Access denied to other drivers' documents",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Document not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{documentId}")
    public ResponseEntity<DocumentResponse> getDriverDocumentById(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long documentId
    ) {
        DocumentResponse response = driverDocumentService.getDriverDocumentById(currentUser, documentId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Securely download a document", description = "Streams document binary content with verified ownership and safe HTTP headers.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Document downloaded successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Access denied to other drivers' documents",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Document not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{documentId}/download")
    public ResponseEntity<Resource> downloadDocument(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long documentId
    ) throws IOException {
        DocumentResponse doc = driverDocumentService.getDriverDocumentById(currentUser, documentId);
        Resource resource = driverDocumentService.downloadDocument(currentUser, documentId);

        String contentType = doc.getContentType();
        if (contentType == null) {
            contentType = Files.probeContentType(Paths.get(resource.getFile().getAbsolutePath()));
            if (contentType == null) {
                contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
            }
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + doc.getFileName() + "\"")
                .body(resource);
    }

    @Operation(summary = "Resubmit a corrected document", description = "Uploads a replacement document for a rejected or expired submission. Preserves audit history and marks status as PENDING_REVIEW.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Document resubmitted successfully",
                    content = @Content(schema = @Schema(implementation = DocumentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request - Document already verified or invalid file",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Document not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping(value = "/{documentId}/resubmit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> resubmitDocument(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long documentId,
            @Parameter(description = "Document identification number (optional)")
            @RequestParam(value = "documentNumber", required = false) String documentNumber,
            @Parameter(description = "Document issue date (optional)")
            @RequestParam(value = "issueDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate issueDate,
            @Parameter(description = "Document expiry date (optional)")
            @RequestParam(value = "expiryDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiryDate,
            @Parameter(description = "Issuing authority (optional)")
            @RequestParam(value = "issuingAuthority", required = false) String issuingAuthority,
            @Parameter(description = "File binary payload", required = true)
            @RequestParam("file") MultipartFile file
    ) {
        DocumentResponse response = driverDocumentService.resubmitDocument(
                currentUser, documentId, file, documentNumber, issueDate, expiryDate, issuingAuthority
        );
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Logically remove a document", description = "Logically removes an unverified document without deleting audit records. Verified documents cannot be deleted.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Document logically removed successfully"),
            @ApiResponse(responseCode = "400", description = "Bad Request - Verified document cannot be deleted",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Document not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{documentId}")
    public ResponseEntity<Void> deleteDocument(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long documentId
    ) {
        driverDocumentService.deleteDocument(currentUser, documentId);
        return ResponseEntity.noContent().build();
    }
}
