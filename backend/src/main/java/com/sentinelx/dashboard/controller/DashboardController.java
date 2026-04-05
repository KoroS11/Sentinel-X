package com.sentinelx.dashboard.controller;

import com.sentinelx.auth.exception.InvalidCredentialsException;
import com.sentinelx.dashboard.dto.AlertStatsResponse;
import com.sentinelx.dashboard.dto.AdminDashboardResponse;
import com.sentinelx.dashboard.dto.DashboardSummaryResponse;
import com.sentinelx.dashboard.dto.RiskTrendResponse;
import com.sentinelx.dashboard.service.DashboardService;
import com.sentinelx.user.entity.User;
import com.sentinelx.user.repository.UserRepository;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;
    private final UserRepository userRepository;

    public DashboardController(DashboardService dashboardService, UserRepository userRepository) {
        this.dashboardService = dashboardService;
        this.userRepository = userRepository;
    }

    @GetMapping("/me")
    @PreAuthorize("hasAnyAuthority(T(com.sentinelx.auth.security.RoleConstants).EMPLOYEE, T(com.sentinelx.auth.security.RoleConstants).ANALYST, T(com.sentinelx.auth.security.RoleConstants).ADMIN)")
    public DashboardSummaryResponse getMyDashboard(Authentication authentication) {
        User user = resolveCurrentUser(authentication);
        return dashboardService.getUserDashboard(user);
    }

    @GetMapping("/admin")
    @PreAuthorize("hasAuthority(T(com.sentinelx.auth.security.RoleConstants).ADMIN)")
    public AdminDashboardResponse getAdminDashboard() {
        return dashboardService.getAdminDashboard();
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyAuthority(T(com.sentinelx.auth.security.RoleConstants).EMPLOYEE, T(com.sentinelx.auth.security.RoleConstants).ANALYST, T(com.sentinelx.auth.security.RoleConstants).ADMIN)")
    public Object getDashboardSummary(Authentication authentication) {
        User user = resolveCurrentUser(authentication);
        return dashboardService.getDashboardSummary(user);
    }

    @GetMapping("/risk-trends")
    @PreAuthorize("hasAnyAuthority(T(com.sentinelx.auth.security.RoleConstants).ANALYST, T(com.sentinelx.auth.security.RoleConstants).ADMIN)")
    public List<RiskTrendResponse> getRiskTrends() {
        return dashboardService.getRiskTrends();
    }

    @GetMapping("/alert-stats")
    @PreAuthorize("hasAnyAuthority(T(com.sentinelx.auth.security.RoleConstants).ANALYST, T(com.sentinelx.auth.security.RoleConstants).ADMIN)")
    public AlertStatsResponse getAlertStats() {
        return dashboardService.getAlertStats();
    }

    private User resolveCurrentUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new InvalidCredentialsException("Authentication is required.");
        }

        return userRepository.findByUsername(authentication.getName())
            .orElseThrow(() -> new InvalidCredentialsException("Invalid user context."));
    }
}
