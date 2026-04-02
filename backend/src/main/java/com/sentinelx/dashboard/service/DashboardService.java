package com.sentinelx.dashboard.service;

import com.sentinelx.activity.dto.ActivityResponse;
import com.sentinelx.activity.service.ActivityService;
import com.sentinelx.alert.entity.AlertSeverity;
import com.sentinelx.alert.entity.AlertStatus;
import com.sentinelx.alert.service.AlertService;
import com.sentinelx.dashboard.dto.AdminDashboardResponse;
import com.sentinelx.dashboard.dto.DashboardSummaryResponse;
import com.sentinelx.risk.dto.RiskScoreResponse;
import com.sentinelx.risk.repository.RiskScoreRepository;
import com.sentinelx.risk.service.RiskScoreService;
import com.sentinelx.alert.repository.AlertRepository;
import com.sentinelx.user.entity.User;
import com.sentinelx.user.repository.UserRepository;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private static final int RECENT_ACTIVITY_LIMIT = 5;
    private static final String SORT_BY_CREATED_AT = "createdAt";
    private static final int HIGH_RISK_USER_THRESHOLD = 60;

    private final ActivityService activityService;
    private final RiskScoreService riskScoreService;
    private final AlertService alertService;
    private final UserRepository userRepository;
    private final AlertRepository alertRepository;
    private final RiskScoreRepository riskScoreRepository;

    public DashboardService(
        ActivityService activityService,
        RiskScoreService riskScoreService,
        AlertService alertService,
        UserRepository userRepository,
        AlertRepository alertRepository,
        RiskScoreRepository riskScoreRepository
    ) {
        this.activityService = activityService;
        this.riskScoreService = riskScoreService;
        this.alertService = alertService;
        this.userRepository = userRepository;
        this.alertRepository = alertRepository;
        this.riskScoreRepository = riskScoreRepository;
    }

    @Transactional(readOnly = true)
    public DashboardSummaryResponse getUserDashboard(User user) {
        Page<ActivityResponse> recentActivitiesPage = activityService.getActivitiesForUser(
            user,
            PageRequest.of(
                0,
                RECENT_ACTIVITY_LIMIT,
                Sort.by(Sort.Direction.DESC, SORT_BY_CREATED_AT)
            )
        );

        Integer latestRiskScore = riskScoreService.getLatestRiskScore(user)
            .map(RiskScoreResponse::score)
            .orElse(null);

        long openAlertsCount = alertService.countAlertsForUserByStatus(user, AlertStatus.OPEN);
        long criticalAlertsCount = alertService.countAlertsForUserBySeverity(user, AlertSeverity.CRITICAL);
        List<ActivityResponse> recentActivities = recentActivitiesPage.getContent();

        return new DashboardSummaryResponse(
            recentActivitiesPage.getTotalElements(),
            latestRiskScore,
            openAlertsCount,
            criticalAlertsCount,
            recentActivities
        );
    }

    @Transactional(readOnly = true)
    public AdminDashboardResponse getAdminDashboard() {
        long totalUsers = userRepository.count();
        long totalOpenAlerts = alertRepository.countByStatusAggregated(AlertStatus.OPEN);
        double averageRiskScore = normalizeDouble(riskScoreRepository.findAverageLatestRiskScore());
        long highRiskUserCount = riskScoreRepository.countUsersWithLatestScoreAtLeast(HIGH_RISK_USER_THRESHOLD);

        return new AdminDashboardResponse(
            totalUsers,
            totalOpenAlerts,
            averageRiskScore,
            highRiskUserCount
        );
    }

    private double normalizeDouble(Double value) {
        return value == null ? 0.0d : value;
    }
}
