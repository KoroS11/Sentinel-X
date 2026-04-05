package com.sentinelx.alert.dto;

import com.sentinelx.alert.entity.Alert;
import com.sentinelx.alert.entity.AlertSeverity;
import com.sentinelx.alert.entity.AlertStatus;
import java.time.LocalDateTime;

public record AlertResponse(
    Long id,
    Long userId,
    Long riskScoreId,
    AlertSeverity severity,
    String message,
    AlertStatus status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static AlertResponse fromEntity(Alert alert) {
        Long riskScoreId = alert.getRiskScore() != null ? alert.getRiskScore().getId() : null;
        return new AlertResponse(
            alert.getId(),
            alert.getUser().getId(),
            riskScoreId,
            alert.getSeverity(),
            alert.getMessage(),
            alert.getStatus(),
            alert.getCreatedAt(),
            alert.getUpdatedAt()
        );
    }
}
