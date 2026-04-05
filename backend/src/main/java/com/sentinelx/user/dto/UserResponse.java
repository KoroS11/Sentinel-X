package com.sentinelx.user.dto;

import com.sentinelx.user.entity.User;
import com.sentinelx.user.entity.UserStatus;
import java.time.LocalDateTime;
import java.util.List;

public record UserResponse(
    Long id,
    String username,
    String email,
    boolean emailVerified,
    UserStatus status,
    List<String> roles,
    LocalDateTime createdAt
) {
    public static UserResponse fromEntity(User user) {
        return new UserResponse(
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            user.isEmailVerified(),
            user.getStatus(),
            List.of(user.getRole().getName().name()),
            user.getCreatedAt()
        );
    }
}