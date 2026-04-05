package com.sentinelx.user.dto;

import jakarta.validation.constraints.Email;

public record UpdateUserRequest(
    String username,
    @Email String email
) {
}