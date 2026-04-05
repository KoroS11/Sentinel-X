package com.sentinelx.risk.service;

import com.sentinelx.activity.entity.Activity;
import com.sentinelx.activity.repository.ActivityRepository;
import com.sentinelx.alert.service.AlertService;
import com.sentinelx.risk.dto.RiskScoreResponse;
import com.sentinelx.risk.entity.RiskScore;
import com.sentinelx.risk.repository.RiskScoreRepository;
import com.sentinelx.risk.strategy.RiskScoringStrategy;
import com.sentinelx.user.entity.User;
import com.sentinelx.user.exception.ResourceNotFoundException;
import com.sentinelx.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RiskScoreService {

    private static final int RECENT_ACTIVITIES_FETCH_SIZE = 50;
    private static final String SORT_BY_CREATED_AT = "createdAt";
    private static final String SORT_BY_CALCULATED_AT = "calculatedAt";
    private static final int HIGH_RISK_THRESHOLD = 60;
    private static final int ALERT_TRIGGER_SCORE_THRESHOLD = 60;

    private static final String REASON_NO_RECENT_ACTIVITY = "No recent activity detected.";
    private static final String REASON_ELEVATED_ACTIVITY =
        "Elevated risk due to frequent and off-hours activity.";
    private static final String REASON_NORMAL_ACTIVITY =
        "Risk calculated from recent user activity patterns.";
    private static final String USER_NOT_FOUND = "User not found.";

    private final ActivityRepository activityRepository;
    private final RiskScoreRepository riskScoreRepository;
    private final RiskScoringStrategy riskScoringStrategy;
    private final AlertService alertService;
    private final UserRepository userRepository;

    public RiskScoreService(
        ActivityRepository activityRepository,
        RiskScoreRepository riskScoreRepository,
        RiskScoringStrategy riskScoringStrategy,
        AlertService alertService,
        UserRepository userRepository
    ) {
        this.activityRepository = activityRepository;
        this.riskScoreRepository = riskScoreRepository;
        this.riskScoringStrategy = riskScoringStrategy;
        this.alertService = alertService;
        this.userRepository = userRepository;
    }

    @Transactional
    public RiskScoreResponse evaluateRisk(User user) {
        Page<Activity> page = activityRepository.findAllByUser(
            user,
            PageRequest.of(
                0,
                RECENT_ACTIVITIES_FETCH_SIZE,
                Sort.by(Sort.Direction.DESC, SORT_BY_CREATED_AT)
            )
        );

        List<Activity> recentActivities = page.getContent();
        int score = riskScoringStrategy.calculateScore(user, recentActivities);

        RiskScore riskScore = new RiskScore();
        riskScore.setUser(user);
        riskScore.setScore(score);
        riskScore.setReason(resolveReason(score, recentActivities));

        RiskScore savedRiskScore = riskScoreRepository.save(riskScore);

        if (savedRiskScore.getScore() >= ALERT_TRIGGER_SCORE_THRESHOLD) {
            alertService.generateAlert(user, savedRiskScore);
        }

        return RiskScoreResponse.fromEntity(savedRiskScore);
    }

    @Transactional(readOnly = true)
    public Optional<RiskScoreResponse> getLatestRiskScore(User user) {
        return riskScoreRepository.findTopByUserOrderByCalculatedAtDesc(user)
            .map(RiskScoreResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public Page<RiskScoreResponse> getRiskHistory(User user, Pageable pageable) {
        Pageable sortedPageable = PageRequest.of(
            pageable.getPageNumber(),
            pageable.getPageSize(),
            Sort.by(Sort.Direction.DESC, SORT_BY_CALCULATED_AT)
        );
        return riskScoreRepository.findAllByUser(user, sortedPageable)
            .map(RiskScoreResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public RiskScoreResponse getLatestRiskScoreByUserId(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException(USER_NOT_FOUND));
        return getLatestRiskScore(user).orElseGet(() -> evaluateRisk(user));
    }

    @Transactional(readOnly = true)
    public Page<RiskScoreResponse> getRiskHistoryByUserId(Long userId, Pageable pageable) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException(USER_NOT_FOUND));
        return getRiskHistory(user, pageable);
    }

    private String resolveReason(int score, List<Activity> recentActivities) {
        if (recentActivities.isEmpty()) {
            return REASON_NO_RECENT_ACTIVITY;
        }

        if (score >= HIGH_RISK_THRESHOLD) {
            return REASON_ELEVATED_ACTIVITY;
        }

        return REASON_NORMAL_ACTIVITY;
    }
}
