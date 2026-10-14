package com.lexor.ridetype.service;

import com.lexor.ridetype.dto.request.CreateRideTypeRequest;
import com.lexor.ridetype.dto.request.UpdateRideTypeRequest;
import com.lexor.ridetype.dto.request.UpdateRideTypeStatusRequest;
import com.lexor.ridetype.dto.response.RideTypeResponse;

import java.util.List;

public interface RideTypeService {

    List<RideTypeResponse> getEnabledRideTypes();

    RideTypeResponse getEnabledRideTypeByUuid(String uuid);

    List<RideTypeResponse> getAllRideTypesAdmin();

    RideTypeResponse getRideTypeByUuidAdmin(String uuid);

    RideTypeResponse createRideType(CreateRideTypeRequest request);

    RideTypeResponse updateRideType(String uuid, UpdateRideTypeRequest request);

    RideTypeResponse updateRideTypeStatus(String uuid, UpdateRideTypeStatusRequest request);

    void deleteRideTypeSafely(String uuid);
}
