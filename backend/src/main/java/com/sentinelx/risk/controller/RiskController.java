package com.sentinelx.risk.controller;

import com.sentinelx.auth.exception.InvalidCredentialsException;
import com.sentinelx.risk.dto.RiskScoreResponse;
import com.sentinelx.risk.service.RiskScoreService;
import com.sentinelx.user.entity.User;
import com.sentinelx.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/risk")
public class RiskController {

    private final RiskScoreService riskScoreService;
    private final UserRepository userRepository;

    public RiskController(RiskScoreService riskScoreService, UserRepository userRepository) {
        this.riskScoreService = riskScoreService;
        this.userRepository = userRepository;
    }

    @GetMapping("/me")
    @PreAuthorize("hasAnyAuthority(T(com.sentinelx.auth.security.RoleConstants).EMPLOYEE, T(com.sentinelx.auth.security.RoleConstants).ANALYST, T(com.sentinelx.auth.security.RoleConstants).ADMIN)")
    public RiskScoreResponse getMyRiskScore(Authentication authentication) {
        User user = resolveCurrentUser(authentication);
        return riskScoreService.getLatestRiskScore(user)
            .orElseGet(() -> riskScoreService.evaluateRisk(user));
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("hasAnyAuthority(T(com.sentinelx.auth.security.RoleConstants).ANALYST, T(com.sentinelx.auth.security.RoleConstants).ADMIN)")
    public RiskScoreResponse getRiskForUser(@PathVariable Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));
        return riskScoreService.getLatestRiskScore(user)
            .orElseGet(() -> riskScoreService.evaluateRisk(user));
    }

    @GetMapping("/history/me")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyAuthority(T(com.sentinelx.auth.security.RoleConstants).EMPLOYEE, T(com.sentinelx.auth.security.RoleConstants).ANALYST, T(com.sentinelx.auth.security.RoleConstants).ADMIN)")
    public Page<RiskScoreResponse> getMyRiskHistory(Authentication authentication, Pageable pageable) {
        User user = resolveCurrentUser(authentication);
        return riskScoreService.getRiskHistory(user, pageable);
    }

    private User resolveCurrentUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new InvalidCredentialsException("Authentication is required.");
        }

        return userRepository.findByUsername(authentication.getName())
            .orElseThrow(() -> new InvalidCredentialsException("Invalid user context."));
    }
}
