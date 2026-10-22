package com.lexor.admin.service;

import com.lexor.admin.dto.response.ServiceHealthStatusResponse;

public interface IServiceHealthMonitorService {

    ServiceHealthStatusResponse getSystemHealth();
}
