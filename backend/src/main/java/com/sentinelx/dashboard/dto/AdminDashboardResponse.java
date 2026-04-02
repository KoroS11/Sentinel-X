package com.sentinelx.dashboard.dto;

public record AdminDashboardResponse(
    long totalUsers,
    long totalOpenAlerts,
    double averageRiskScore,
    long highRiskUserCount
) {}
