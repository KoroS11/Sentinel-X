package com.sentinelx.risk.service;

import com.sentinelx.activity.entity.Activity;
import com.sentinelx.activity.repository.ActivityRepository;
import com.sentinelx.risk.dto.RiskScoreResponse;
import com.sentinelx.risk.entity.RiskScore;
import com.sentinelx.risk.repository.RiskScoreRepository;
import com.sentinelx.risk.strategy.RiskScoringStrategy;
import com.sentinelx.user.entity.User;
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

    private static final String REASON_NO_RECENT_ACTIVITY = "No recent activity detected.";
    private static final String REASON_ELEVATED_ACTIVITY =
        "Elevated risk due to frequent and off-hours activity.";
    private static final String REASON_NORMAL_ACTIVITY =
        "Risk calculated from recent user activity patterns.";

    private final ActivityRepository activityRepository;
    private final RiskScoreRepository riskScoreRepository;
    private final RiskScoringStrategy riskScoringStrategy;

    public RiskScoreService(
        ActivityRepository activityRepository,
        RiskScoreRepository riskScoreRepository,
        RiskScoringStrategy riskScoringStrategy
    ) {
        this.activityRepository = activityRepository;
        this.riskScoreRepository = riskScoreRepository;
        this.riskScoringStrategy = riskScoringStrategy;
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

        return RiskScoreResponse.fromEntity(riskScoreRepository.save(riskScore));
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
