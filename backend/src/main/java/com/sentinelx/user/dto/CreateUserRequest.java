package com.sentinelx.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
    @NotBlank String username,
    @NotBlank @Email String email,
    @NotBlank @Size(min = 8) String password,
    @NotBlank String role
) {
}