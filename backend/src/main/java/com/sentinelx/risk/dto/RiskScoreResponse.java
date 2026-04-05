package com.sentinelx.risk.dto;

import com.sentinelx.risk.entity.RiskScore;
import java.time.LocalDateTime;

public record RiskScoreResponse(
    Long id,
    Long userId,
    int score,
    String reason,
    LocalDateTime calculatedAt
) {
    public static RiskScoreResponse fromEntity(RiskScore riskScore) {
        return new RiskScoreResponse(
            riskScore.getId(),
            riskScore.getUser().getId(),
            riskScore.getScore(),
            riskScore.getReason(),
            riskScore.getCalculatedAt()
        );
    }
}
