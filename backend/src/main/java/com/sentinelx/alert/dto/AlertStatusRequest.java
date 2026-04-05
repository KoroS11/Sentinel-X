package com.sentinelx.alert.dto;

import com.sentinelx.alert.entity.AlertStatus;
import jakarta.validation.constraints.NotNull;

public record AlertStatusRequest(
    @NotNull AlertStatus status
) {
}