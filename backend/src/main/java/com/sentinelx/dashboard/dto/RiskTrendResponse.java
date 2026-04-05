package com.sentinelx.dashboard.dto;

public record RiskTrendResponse(
    String period,
    double averageScore,
    long highRiskCount
) {}