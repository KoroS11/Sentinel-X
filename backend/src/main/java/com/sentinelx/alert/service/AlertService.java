package com.sentinelx.alert.service;

import com.sentinelx.alert.dto.AlertResponse;
import com.sentinelx.alert.entity.Alert;
import com.sentinelx.alert.entity.AlertSeverity;
import com.sentinelx.alert.entity.AlertStatus;
import com.sentinelx.alert.exception.AlertAccessDeniedException;
import com.sentinelx.alert.exception.AlertNotFoundException;
import com.sentinelx.alert.repository.AlertRepository;
import com.sentinelx.risk.entity.RiskScore;
import com.sentinelx.user.entity.RoleType;
import com.sentinelx.user.entity.User;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AlertService {

    private static final int CRITICAL_SEVERITY_MIN_SCORE = 85;
    private static final int HIGH_SEVERITY_MIN_SCORE = 70;
    private static final int MEDIUM_SEVERITY_MIN_SCORE = 50;

    private final AlertRepository alertRepository;

    public AlertService(AlertRepository alertRepository) {
        this.alertRepository = alertRepository;
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
        Alert alert = findAlert(alertId);
        assertOwnershipOrAdmin(alert, requestingUser);
        alert.setStatus(AlertStatus.ACKNOWLEDGED);
        alert.setUpdatedAt(LocalDateTime.now());
        return AlertResponse.fromEntity(alertRepository.save(alert));
    }

    @Transactional
    public AlertResponse resolveAlert(Long alertId, User requestingUser) {
        Alert alert = findAlert(alertId);
        assertOwnershipOrAdmin(alert, requestingUser);
        alert.setStatus(AlertStatus.RESOLVED);
        alert.setUpdatedAt(LocalDateTime.now());
        return AlertResponse.fromEntity(alertRepository.save(alert));
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

    private Alert findAlert(Long alertId) {
        return alertRepository.findById(alertId)
            .orElseThrow(() -> new AlertNotFoundException("Alert not found."));
    }

    private void assertOwnershipOrAdmin(Alert alert, User requestingUser) {
        boolean isOwner = alert.getUser().getId().equals(requestingUser.getId());
        boolean isAdmin = requestingUser.getRole().getName() == RoleType.ADMIN;

        if (!isOwner && !isAdmin) {
            throw new AlertAccessDeniedException("You are not authorized to modify this alert.");
        }
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
