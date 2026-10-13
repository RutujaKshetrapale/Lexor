package com.lexor.vehicle.service;

import com.lexor.vehicle.dto.request.RegisterVehicleRequest;
import com.lexor.vehicle.dto.request.UpdateVehicleRequest;
import com.lexor.vehicle.dto.response.DriverVehicleAssignmentResponse;
import com.lexor.vehicle.dto.response.VehicleResponse;
import com.lexor.vehicle.dto.response.VehicleVerificationHistoryResponse;
import com.lexor.vehicle.dto.response.VehicleVerificationStatusResponse;
import com.lexor.vehicle.security.UserPrincipal;

import java.util.List;

public interface VehicleService {

    VehicleResponse registerVehicle(UserPrincipal currentUser, RegisterVehicleRequest request);

    List<VehicleResponse> getMyVehicles(UserPrincipal currentUser);

    VehicleResponse getVehicleByUuid(UserPrincipal currentUser, String vehicleUuid);

    VehicleResponse updateVehicle(UserPrincipal currentUser, String vehicleUuid, UpdateVehicleRequest request);

    VehicleResponse updateVehicleStatus(UserPrincipal currentUser, String vehicleUuid, boolean isActive);

    VehicleResponse submitForVerification(UserPrincipal currentUser, String vehicleUuid);

    VehicleVerificationStatusResponse getVerificationStatus(UserPrincipal currentUser, String vehicleUuid);

    List<VehicleVerificationHistoryResponse> getVehicleVerificationHistory(UserPrincipal currentUser, String vehicleUuid);

    List<DriverVehicleAssignmentResponse> getVehicleAssignments(UserPrincipal currentUser, String vehicleUuid);
}
