package com.sentinelx.auth.controller;

import com.sentinelx.auth.dto.AuthResponse;
import com.sentinelx.auth.dto.LoginRequest;
import com.sentinelx.auth.dto.RefreshRequest;
import com.sentinelx.auth.dto.RegisterRequest;
import com.sentinelx.auth.entity.RefreshToken;
import com.sentinelx.auth.exception.InvalidCredentialsException;
import com.sentinelx.auth.refresh.RefreshTokenService;
import com.sentinelx.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;

    public AuthController(AuthService authService, RefreshTokenService refreshTokenService) {
        this.authService = authService;
        this.refreshTokenService = refreshTokenService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        RefreshToken rotatedRefreshToken = refreshTokenService.rotateRefreshToken(request.refreshToken());
        AuthResponse authResponse = authService.createAuthResponse(
            rotatedRefreshToken.getUser(),
            rotatedRefreshToken.getToken()
        );
        return ResponseEntity.ok(authResponse);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new InvalidCredentialsException("Authentication is required for logout.");
        }
        authService.logout(authentication.getName());
        return ResponseEntity.ok().build();
    }
}
