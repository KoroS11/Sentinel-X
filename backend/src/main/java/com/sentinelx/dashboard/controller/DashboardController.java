package com.sentinelx.dashboard.controller;

import com.sentinelx.auth.exception.InvalidCredentialsException;
import com.sentinelx.dashboard.dto.AdminDashboardResponse;
import com.sentinelx.dashboard.dto.DashboardSummaryResponse;
import com.sentinelx.dashboard.service.DashboardService;
import com.sentinelx.user.entity.User;
import com.sentinelx.user.repository.UserRepository;
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
    public DashboardSummaryResponse getMyDashboard(Authentication authentication) {
        User user = resolveCurrentUser(authentication);
        return dashboardService.getUserDashboard(user);
    }

    @GetMapping("/admin")
    public AdminDashboardResponse getAdminDashboard() {
        return dashboardService.getAdminDashboard();
    }

    private User resolveCurrentUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new InvalidCredentialsException("Authentication is required.");
        }

        return userRepository.findByUsername(authentication.getName())
            .orElseThrow(() -> new InvalidCredentialsException("Invalid user context."));
    }
}
