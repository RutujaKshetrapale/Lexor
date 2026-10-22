package com.lexor.admin.service;

import com.lexor.admin.dto.response.PlatformTrendsResponse;

import java.io.Writer;
import java.time.LocalDateTime;

public interface IPlatformAnalyticsService {

    PlatformTrendsResponse getPlatformTrends(String timeRange);

    void exportCsv(String entityType, String timeRange, LocalDateTime startDate, LocalDateTime endDate, Writer writer);
}
