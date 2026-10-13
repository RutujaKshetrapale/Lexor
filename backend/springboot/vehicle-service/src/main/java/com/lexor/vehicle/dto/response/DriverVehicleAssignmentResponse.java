package com.lexor.vehicle.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lexor.vehicle.entity.AssignmentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DriverVehicleAssignmentResponse {
    private Long id;
    private String vehicleUuid;
    private String vehiclePlate;
    private String driverUuid;
    private String driverName;
    private String assignedByAdminName;
    private AssignmentStatus status;
    private LocalDateTime assignedAt;
    private LocalDateTime unassignedAt;
    private String reason;
}
