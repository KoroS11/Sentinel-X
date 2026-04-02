package com.sentinelx.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NewPasswordRequest(
    @NotBlank String token,
    @NotBlank @Size(min = 8) String newPassword
) {
}
