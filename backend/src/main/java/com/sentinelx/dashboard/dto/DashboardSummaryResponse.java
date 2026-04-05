package com.sentinelx.dashboard.dto;

import com.sentinelx.activity.dto.ActivityResponse;
import java.util.List;

public record DashboardSummaryResponse(
    long totalActivities,
    Integer latestRiskScore,
    long openAlertsCount,
    long criticalAlertsCount,
    List<ActivityResponse> recentActivities
) {}
