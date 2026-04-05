package com.sentinelx.alert.dto;

import jakarta.validation.constraints.NotNull;

public record AlertAssignRequest(
    @NotNull Long assigneeUserId
) {
}