package com.sentinelx.activity.dto;

import com.sentinelx.activity.entity.Activity;
import java.time.LocalDateTime;

public record ActivityResponse(
    Long id,
    Long userId,
    String action,
    String entityType,
    String entityId,
    String metadata,
    LocalDateTime createdAt
) {
    public static ActivityResponse fromEntity(Activity activity) {
        return new ActivityResponse(
            activity.getId(),
            activity.getUser().getId(),
            activity.getAction(),
            activity.getEntityType(),
            activity.getEntityId(),
            activity.getMetadata(),
            activity.getCreatedAt()
        );
    }
}
