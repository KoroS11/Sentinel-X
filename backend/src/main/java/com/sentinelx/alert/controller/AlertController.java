package com.sentinelx.alert.controller;

import com.sentinelx.alert.dto.AlertResponse;
import com.sentinelx.alert.entity.AlertStatus;
import com.sentinelx.alert.service.AlertService;
import com.sentinelx.auth.exception.InvalidCredentialsException;
import com.sentinelx.user.entity.User;
import com.sentinelx.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertService alertService;
    private final UserRepository userRepository;

    public AlertController(AlertService alertService, UserRepository userRepository) {
        this.alertService = alertService;
        this.userRepository = userRepository;
    }

    @GetMapping("/me")
    public Page<AlertResponse> getMyAlerts(
        Authentication authentication,
        @RequestParam(value = "status", required = false) AlertStatus status,
        Pageable pageable
    ) {
        User user = resolveCurrentUser(authentication);
        return alertService.getAlertsForUser(user, status, pageable);
    }

    @PatchMapping("/{id}/acknowledge")
    @ResponseStatus(HttpStatus.OK)
    public AlertResponse acknowledgeAlert(@PathVariable Long id, Authentication authentication) {
        User user = resolveCurrentUser(authentication);
        return alertService.acknowledgeAlert(id, user);
    }

    @PatchMapping("/{id}/resolve")
    @ResponseStatus(HttpStatus.OK)
    public AlertResponse resolveAlert(@PathVariable Long id, Authentication authentication) {
        User user = resolveCurrentUser(authentication);
        return alertService.resolveAlert(id, user);
    }

    @GetMapping
    public Page<AlertResponse> getAllAlerts(
        @RequestParam(value = "status", required = false) AlertStatus status,
        Pageable pageable
    ) {
        return alertService.getAllAlerts(status, pageable);
    }

    private User resolveCurrentUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new InvalidCredentialsException("Authentication is required.");
        }

        return userRepository.findByUsername(authentication.getName())
            .orElseThrow(() -> new InvalidCredentialsException("Invalid user context."));
    }
}
