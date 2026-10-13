package com.lexor.vehicle.service;

import com.lexor.vehicle.dto.request.AssignDriverRequest;
import com.lexor.vehicle.dto.request.ReviewVehicleDocumentRequest;
import com.lexor.vehicle.dto.request.ReviewVehicleRequest;
import com.lexor.vehicle.dto.request.SuspendVehicleRequest;
import com.lexor.vehicle.dto.response.DriverVehicleAssignmentResponse;
import com.lexor.vehicle.dto.response.PagedResponse;
import com.lexor.vehicle.dto.response.VehicleDocumentResponse;
import com.lexor.vehicle.dto.response.VehicleResponse;
import com.lexor.vehicle.dto.response.VehicleVerificationHistoryResponse;
import com.lexor.vehicle.entity.VehicleVerificationStatus;
import com.lexor.vehicle.security.UserPrincipal;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface VehicleAdminService {

    PagedResponse<VehicleResponse> listVehicles(
            VehicleVerificationStatus verificationStatus,
            Boolean isActive,
            String rideTypeCategory,
            String search,
            Pageable pageable
    );

    PagedResponse<VehicleResponse> listPendingVehicles(Pageable pageable);

    VehicleResponse getVehicleDetails(String vehicleUuid);

    List<VehicleDocumentResponse> getVehicleDocuments(String vehicleUuid);

    Resource downloadDocument(String vehicleUuid, Long documentId);

    VehicleDocumentResponse reviewDocument(
            UserPrincipal admin,
            String vehicleUuid,
            Long documentId,
            ReviewVehicleDocumentRequest request
    );

    VehicleResponse reviewVehicle(
            UserPrincipal admin,
            String vehicleUuid,
            ReviewVehicleRequest request
    );

    VehicleResponse suspendOrReinstateVehicle(
            UserPrincipal admin,
            String vehicleUuid,
            SuspendVehicleRequest request
    );

    List<VehicleVerificationHistoryResponse> getVerificationHistory(String vehicleUuid);

    DriverVehicleAssignmentResponse assignDriver(
            UserPrincipal admin,
            String vehicleUuid,
            AssignDriverRequest request
    );

    DriverVehicleAssignmentResponse unassignDriver(
            UserPrincipal admin,
            String vehicleUuid,
            Long assignmentId,
            String reason
    );

    List<DriverVehicleAssignmentResponse> getAssignmentHistory(String vehicleUuid);
}
