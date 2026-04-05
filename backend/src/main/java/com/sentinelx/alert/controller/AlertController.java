package com.sentinelx.alert.controller;

import com.sentinelx.alert.dto.AlertResponse;
import com.sentinelx.alert.dto.AlertAssignRequest;
import com.sentinelx.alert.dto.AlertStatusRequest;
import com.sentinelx.alert.entity.AlertStatus;
import com.sentinelx.alert.service.AlertService;
import com.sentinelx.auth.exception.InvalidCredentialsException;
import com.sentinelx.user.entity.User;
import com.sentinelx.user.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;
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
    @PreAuthorize("hasAnyAuthority(T(com.sentinelx.auth.security.RoleConstants).EMPLOYEE, T(com.sentinelx.auth.security.RoleConstants).ANALYST, T(com.sentinelx.auth.security.RoleConstants).ADMIN)")
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
    @PreAuthorize("hasAnyAuthority(T(com.sentinelx.auth.security.RoleConstants).EMPLOYEE, T(com.sentinelx.auth.security.RoleConstants).ANALYST, T(com.sentinelx.auth.security.RoleConstants).ADMIN)")
    public AlertResponse acknowledgeAlert(@PathVariable Long id, Authentication authentication) {
        User user = resolveCurrentUser(authentication);
        return alertService.acknowledgeAlert(id, user);
    }

    @PatchMapping("/{id}/resolve")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyAuthority(T(com.sentinelx.auth.security.RoleConstants).EMPLOYEE, T(com.sentinelx.auth.security.RoleConstants).ANALYST, T(com.sentinelx.auth.security.RoleConstants).ADMIN)")
    public AlertResponse resolveAlert(@PathVariable Long id, Authentication authentication) {
        User user = resolveCurrentUser(authentication);
        return alertService.resolveAlert(id, user);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority(T(com.sentinelx.auth.security.RoleConstants).ANALYST, T(com.sentinelx.auth.security.RoleConstants).ADMIN)")
    public Page<AlertResponse> getAllAlerts(
        @RequestParam(value = "status", required = false) AlertStatus status,
        Pageable pageable
    ) {
        return alertService.getAllAlerts(status, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority(T(com.sentinelx.auth.security.RoleConstants).EMPLOYEE, T(com.sentinelx.auth.security.RoleConstants).ANALYST, T(com.sentinelx.auth.security.RoleConstants).ADMIN)")
    public AlertResponse getAlertById(@PathVariable Long id, Authentication authentication) {
        User user = resolveCurrentUser(authentication);
        return alertService.getAlertById(id, user);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyAuthority(T(com.sentinelx.auth.security.RoleConstants).EMPLOYEE, T(com.sentinelx.auth.security.RoleConstants).ANALYST, T(com.sentinelx.auth.security.RoleConstants).ADMIN)")
    public AlertResponse updateAlertStatus(
        @PathVariable Long id,
        @Valid @RequestBody AlertStatusRequest request,
        Authentication authentication
    ) {
        User user = resolveCurrentUser(authentication);
        return alertService.updateAlertStatus(id, request.status(), user);
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAnyAuthority(T(com.sentinelx.auth.security.RoleConstants).ANALYST, T(com.sentinelx.auth.security.RoleConstants).ADMIN)")
    public AlertResponse assignAlert(
        @PathVariable Long id,
        @Valid @RequestBody AlertAssignRequest request,
        Authentication authentication
    ) {
        User user = resolveCurrentUser(authentication);
        return alertService.assignAlert(id, request.assigneeUserId(), user);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.sentinelx.auth.security.RoleConstants).ADMIN)")
    public ResponseEntity<Void> deleteAlert(@PathVariable Long id, Authentication authentication) {
        User user = resolveCurrentUser(authentication);
        alertService.deleteAlert(id, user);
        return ResponseEntity.noContent().build();
    }

    private User resolveCurrentUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new InvalidCredentialsException("Authentication is required.");
        }

        return userRepository.findByUsername(authentication.getName())
            .orElseThrow(() -> new InvalidCredentialsException("Invalid user context."));
    }
}
