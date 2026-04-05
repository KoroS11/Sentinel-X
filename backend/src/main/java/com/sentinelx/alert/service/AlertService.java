package com.sentinelx.alert.service;

import com.sentinelx.alert.dto.AlertResponse;
import com.sentinelx.alert.entity.Alert;
import com.sentinelx.alert.entity.AlertSeverity;
import com.sentinelx.alert.entity.AlertStatus;
import com.sentinelx.alert.exception.AlertAccessDeniedException;
import com.sentinelx.alert.exception.AlertInvalidStatusTransitionException;
import com.sentinelx.alert.repository.AlertRepository;
import com.sentinelx.risk.entity.RiskScore;
import com.sentinelx.user.entity.RoleType;
import com.sentinelx.user.entity.User;
import com.sentinelx.user.exception.ResourceNotFoundException;
import com.sentinelx.user.repository.UserRepository;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AlertService {

    private static final int CRITICAL_SEVERITY_MIN_SCORE = 85;
    private static final int HIGH_SEVERITY_MIN_SCORE = 70;
    private static final int MEDIUM_SEVERITY_MIN_SCORE = 50;
    private static final String ALERT_NOT_FOUND = "Alert not found.";
    private static final String USER_NOT_FOUND = "User not found.";
    private static final String ILLEGAL_STATUS_TRANSITION = "Illegal status transition from %s to %s.";

    private final AlertRepository alertRepository;
    private final UserRepository userRepository;

    public AlertService(AlertRepository alertRepository, UserRepository userRepository) {
        this.alertRepository = alertRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public AlertResponse generateAlert(User user, RiskScore score) {
        Alert alert = new Alert();
        alert.setUser(user);
        alert.setRiskScore(score);
        alert.setSeverity(determineSeverity(score.getScore()));
        alert.setStatus(AlertStatus.OPEN);
        alert.setMessage(buildMessage(score));
        Alert saved = alertRepository.save(alert);
        return AlertResponse.fromEntity(saved);
    }

    @Transactional
    public AlertResponse acknowledgeAlert(Long alertId, User requestingUser) {
        return updateAlertStatus(alertId, AlertStatus.ACKNOWLEDGED, requestingUser);
    }

    @Transactional
    public AlertResponse resolveAlert(Long alertId, User requestingUser) {
        Alert alert = findAlert(alertId);
        if (alert.getStatus() == AlertStatus.OPEN) {
            updateAlertStatus(alertId, AlertStatus.UNDER_INVESTIGATION, requestingUser);
        }
        return updateAlertStatus(alertId, AlertStatus.RESOLVED, requestingUser);
    }

    @Transactional(readOnly = true)
    public AlertResponse getAlertById(Long id, User requestingUser) {
        Alert alert = findAlert(id);
        assertViewAccess(alert, requestingUser);
        return AlertResponse.fromEntity(alert);
    }

    @Transactional
    public AlertResponse updateAlertStatus(Long id, AlertStatus newStatus, User requestingUser) {
        Alert alert = findAlert(id);
        assertModifyAccess(alert, requestingUser);

        validateStatusTransition(alert.getStatus(), newStatus);

        alert.setStatus(newStatus);
        alert.setUpdatedAt(LocalDateTime.now());
        return AlertResponse.fromEntity(alertRepository.save(alert));
    }

    @Transactional
    public AlertResponse assignAlert(Long id, Long assigneeUserId, User requestingUser) {
        assertAdminOrAnalyst(requestingUser);

        Alert alert = findAlert(id);
        User assignee = userRepository.findById(assigneeUserId)
            .orElseThrow(() -> new ResourceNotFoundException(USER_NOT_FOUND));

        alert.setAssignedTo(assignee);
        alert.setUpdatedAt(LocalDateTime.now());
        return AlertResponse.fromEntity(alertRepository.save(alert));
    }

    @Transactional
    public void deleteAlert(Long id, User requestingUser) {
        assertAdmin(requestingUser);
        Alert alert = findAlert(id);
        alertRepository.delete(alert);
    }

    @Transactional(readOnly = true)
    public Page<AlertResponse> getAlertsForUser(User user, AlertStatus status, Pageable pageable) {
        if (status == null) {
            return alertRepository.findAllByUser(user, pageable).map(AlertResponse::fromEntity);
        }
        return alertRepository.findAllByUserAndStatus(user, status, pageable).map(AlertResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public Page<AlertResponse> getAllAlerts(AlertStatus status, Pageable pageable) {
        if (status == null) {
            return alertRepository.findAll(pageable).map(AlertResponse::fromEntity);
        }
        return alertRepository.findAllByStatus(status, pageable).map(AlertResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public long countAlertsForUserByStatus(User user, AlertStatus status) {
        return alertRepository.countByUserAndStatus(user, status);
    }

    @Transactional(readOnly = true)
    public long countAlertsForUserBySeverity(User user, AlertSeverity severity) {
        return alertRepository.countByUserAndSeverity(user, severity);
    }

    private Alert findAlert(Long alertId) {
        return alertRepository.findById(alertId)
            .orElseThrow(() -> new ResourceNotFoundException(ALERT_NOT_FOUND));
    }

    private void assertViewAccess(Alert alert, User requestingUser) {
        if (isAdminOrAnalyst(requestingUser)) {
            return;
        }

        boolean isOwner = alert.getUser().getId().equals(requestingUser.getId());
        if (!isOwner) {
            throw new AccessDeniedException("You are not authorized to access this alert.");
        }
    }

    private void assertModifyAccess(Alert alert, User requestingUser) {
        if (isAdminOrAnalyst(requestingUser)) {
            return;
        }

        boolean isOwner = alert.getUser().getId().equals(requestingUser.getId());
        if (!isOwner) {
            throw new AlertAccessDeniedException("You are not authorized to modify this alert.");
        }
    }

    private void assertAdminOrAnalyst(User requestingUser) {
        if (!isAdminOrAnalyst(requestingUser)) {
            throw new AccessDeniedException("Only ADMIN or ANALYST can assign alerts.");
        }
    }

    private void assertAdmin(User requestingUser) {
        if (requestingUser.getRole().getName() != RoleType.ADMIN) {
            throw new AccessDeniedException("Only ADMIN can delete alerts.");
        }
    }

    private boolean isAdminOrAnalyst(User requestingUser) {
        return requestingUser.getRole().getName() == RoleType.ADMIN
            || requestingUser.getRole().getName() == RoleType.ANALYST;
    }

    private void validateStatusTransition(AlertStatus currentStatus, AlertStatus newStatus) {
        if (currentStatus == newStatus) {
            return;
        }

        if (currentStatus == AlertStatus.OPEN) {
            if (newStatus == AlertStatus.UNDER_INVESTIGATION || newStatus == AlertStatus.ACKNOWLEDGED) {
                return;
            }
            throw illegalTransition(currentStatus, newStatus);
        }

        if (currentStatus == AlertStatus.UNDER_INVESTIGATION || currentStatus == AlertStatus.ACKNOWLEDGED) {
            if (newStatus == AlertStatus.RESOLVED) {
                return;
            }
            throw illegalTransition(currentStatus, newStatus);
        }

        if (currentStatus == AlertStatus.RESOLVED) {
            throw illegalTransition(currentStatus, newStatus);
        }

        throw illegalTransition(currentStatus, newStatus);
    }

    private AlertInvalidStatusTransitionException illegalTransition(AlertStatus currentStatus, AlertStatus newStatus) {
        return new AlertInvalidStatusTransitionException(
            String.format(ILLEGAL_STATUS_TRANSITION, currentStatus, newStatus)
        );
    }

    private AlertSeverity determineSeverity(int scoreValue) {
        if (scoreValue >= CRITICAL_SEVERITY_MIN_SCORE) {
            return AlertSeverity.CRITICAL;
        }
        if (scoreValue >= HIGH_SEVERITY_MIN_SCORE) {
            return AlertSeverity.HIGH;
        }
        if (scoreValue >= MEDIUM_SEVERITY_MIN_SCORE) {
            return AlertSeverity.MEDIUM;
        }
        return AlertSeverity.LOW;
    }

    private String buildMessage(RiskScore score) {
        return "Risk score " + score.getScore() + " detected for user " + score.getUser().getUsername() + ".";
    }
}
