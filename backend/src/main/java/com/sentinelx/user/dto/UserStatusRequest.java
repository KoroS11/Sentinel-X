package com.sentinelx.user.dto;

import com.sentinelx.user.entity.UserStatus;
import jakarta.validation.constraints.NotNull;

public record UserStatusRequest(
    @NotNull UserStatus status
) {
}