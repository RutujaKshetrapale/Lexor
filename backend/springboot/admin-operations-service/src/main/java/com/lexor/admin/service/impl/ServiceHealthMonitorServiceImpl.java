package com.lexor.admin.service.impl;

import com.lexor.admin.client.ServiceHealthProbeClient;
import com.lexor.admin.dto.response.ServiceHealthStatusResponse;
import com.lexor.admin.service.IServiceHealthMonitorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ServiceHealthMonitorServiceImpl implements IServiceHealthMonitorService {

    private final ServiceHealthProbeClient healthProbeClient;

    @Override
    public ServiceHealthStatusResponse getSystemHealth() {
        return healthProbeClient.checkAllServices();
    }
}
