package com.sentinelx.dashboard.dto;

import java.util.Map;

public record AlertStatsResponse(
    long totalOpen,
    long totalUnderInvestigation,
    long totalResolved,
    Map<String, Long> bySeverity
) {}