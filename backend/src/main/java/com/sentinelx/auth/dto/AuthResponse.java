package com.sentinelx.auth.dto;

public record AuthResponse(
    String token,
    String username,
    String refreshToken
) {
}
