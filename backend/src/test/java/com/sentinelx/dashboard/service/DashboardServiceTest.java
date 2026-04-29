package com.sentinelx.dashboard.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sentinelx.activity.dto.ActivityResponse;
import com.sentinelx.activity.service.ActivityService;
import com.sentinelx.alert.dto.AlertResponse;
import com.sentinelx.alert.entity.Alert;
import com.sentinelx.alert.entity.AlertSeverity;
import com.sentinelx.alert.entity.AlertStatus;
import com.sentinelx.alert.service.AlertService;
import com.sentinelx.dashboard.dto.AlertStatsResponse;
import com.sentinelx.dashboard.dto.AdminDashboardResponse;
import com.sentinelx.dashboard.dto.DashboardSummaryResponse;
import com.sentinelx.dashboard.dto.RiskTrendResponse;
import com.sentinelx.risk.entity.RiskScore;
import com.sentinelx.risk.repository.RiskScoreRepository;
import com.sentinelx.user.entity.Role;
import com.sentinelx.user.entity.RoleType;
import com.sentinelx.user.entity.User;
import com.sentinelx.user.repository.RoleRepository;
import com.sentinelx.user.repository.UserRepository;
import com.sentinelx.auth.repository.EmailVerificationTokenRepository;
import com.sentinelx.auth.repository.PasswordResetTokenRepository;
import com.sentinelx.auth.repository.RefreshTokenRepository;
import com.sentinelx.alert.repository.AlertRepository;
import com.sentinelx.activity.repository.ActivityRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class DashboardServiceTest {

    private static final String TEST_SECRET =
        "dashboard_service_test_secret_with_sufficient_length_123456";

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private ActivityService activityService;

    @Autowired
    private AlertService alertService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private RiskScoreRepository riskScoreRepository;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:dashboardservicetest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("jwt.secret", () -> TEST_SECRET);
        registry.add("jwt.expiration-ms", () -> "3600000");
        registry.add("jwt.refresh-expiration-ms", () -> "604800000");
    }

    @BeforeEach
    void setUp() {
        alertRepository.deleteAll();
        riskScoreRepository.deleteAll();
        activityRepository.deleteAll();
        emailVerificationTokenRepository.deleteAll();
        passwordResetTokenRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void getUserDashboardReturnsCorrectCountsForKnownData() {
        User user = createUserWithRole("alice", "alice@example.com", RoleType.EMPLOYEE);

        for (int index = 0; index < 6; index++) {
            activityService.logActivity(
                user,
                "ACTION_" + index,
                "ENTITY",
                "ID_" + index,
                "{}"
            );
        }

        RiskScore mediumRisk = createRiskScore(user, 55, "moderate", LocalDateTime.now().minusMinutes(5));
        RiskScore criticalRisk = createRiskScore(user, 90, "critical", LocalDateTime.now());

        AlertResponse mediumAlert = alertService.generateAlert(user, mediumRisk);
        alertService.generateAlert(user, criticalRisk);
        alertService.resolveAlert(mediumAlert.id(), user);

        DashboardSummaryResponse dashboard = dashboardService.getUserDashboard(user);

        assertEquals(6L, dashboard.totalActivities());
        assertEquals(90, dashboard.latestRiskScore());
        assertEquals(1L, dashboard.openAlertsCount());
        assertEquals(1L, dashboard.criticalAlertsCount());
        assertEquals(5, dashboard.recentActivities().size());

        ActivityResponse firstRecentActivity = dashboard.recentActivities().get(0);
        assertEquals(user.getId(), firstRecentActivity.userId());
    }

    @Test
    void getAdminDashboardReturnsCorrectAggregateCounts() {
        User admin = createUserWithRole("admin", "admin@example.com", RoleType.ADMIN);
        User userOne = createUserWithRole("bob", "bob@example.com", RoleType.EMPLOYEE);
        User userTwo = createUserWithRole("carol", "carol@example.com", RoleType.EMPLOYEE);

        RiskScore userOneOld = createRiskScore(userOne, 40, "old", LocalDateTime.now().minusHours(2));
        RiskScore userOneLatest = createRiskScore(userOne, 80, "latest", LocalDateTime.now().minusMinutes(5));
        RiskScore userTwoLatest = createRiskScore(userTwo, 50, "latest", LocalDateTime.now());

        AlertResponse openAlert = alertService.generateAlert(userOne, userOneLatest);
        AlertResponse closedAlert = alertService.generateAlert(userTwo, userTwoLatest);
        alertService.resolveAlert(closedAlert.id(), userTwo);

        AdminDashboardResponse dashboard = dashboardService.getAdminDashboard();

        assertEquals(3L, dashboard.totalUsers());
        assertEquals(1L, dashboard.totalOpenAlerts());
        assertEquals(65.0d, dashboard.averageRiskScore());
        assertEquals(1L, dashboard.highRiskUserCount());

        // Avoid unused variable warning in strict analysis configurations
        assertEquals(AlertStatus.OPEN, alertRepository.findById(openAlert.id()).orElseThrow().getStatus());
        assertEquals(40, userOneOld.getScore());
        assertEquals(admin.getId(), userRepository.findByEmail("admin@example.com").orElseThrow().getId());
    }

    @Test
    void getDashboardSummaryForEmployeeReturnsOwnDataOnly() {
        User employee = createUserWithRole("employee", "employee@example.com", RoleType.EMPLOYEE);
        User otherEmployee = createUserWithRole("other", "other@example.com", RoleType.EMPLOYEE);

        activityService.logActivity(employee, "FILE_ACCESS", "DOC", "E-1", "{}");
        activityService.logActivity(otherEmployee, "FILE_ACCESS", "DOC", "O-1", "{}");

        createRiskScore(employee, 72, "employee risk", LocalDateTime.now());
        RiskScore otherRisk = createRiskScore(otherEmployee, 88, "other risk", LocalDateTime.now());
        alertService.generateAlert(employee, createRiskScore(employee, 74, "employee alert", LocalDateTime.now().minusMinutes(1)));
        alertService.generateAlert(otherEmployee, otherRisk);

        Object response = dashboardService.getDashboardSummary(employee);

        DashboardSummaryResponse summary = (DashboardSummaryResponse) response;
        assertEquals(1L, summary.totalActivities());
        assertEquals(72, summary.latestRiskScore());
        assertEquals(1L, summary.openAlertsCount());
    }

    @Test
    void getDashboardSummaryForAdminReturnsSystemWideData() {
        User admin = createUserWithRole("admin2", "admin2@example.com", RoleType.ADMIN);
        User employee = createUserWithRole("emp2", "emp2@example.com", RoleType.EMPLOYEE);

        createRiskScore(employee, 82, "high risk", LocalDateTime.now());
        alertService.generateAlert(employee, createRiskScore(employee, 85, "alert", LocalDateTime.now().minusMinutes(1)));

        Object response = dashboardService.getDashboardSummary(admin);

        AdminDashboardResponse summary = (AdminDashboardResponse) response;
        assertEquals(2L, summary.totalUsers());
        assertEquals(1L, summary.totalOpenAlerts());
        assertEquals(1L, summary.highRiskUserCount());
    }

    @Test
    void getRiskTrendsReturnsEightEntriesMaximum() {
        User user = createUserWithRole("trend-user", "trend-user@example.com", RoleType.EMPLOYEE);
        LocalDateTime now = LocalDateTime.now();

        for (int weekOffset = 0; weekOffset <= 8; weekOffset++) {
            createRiskScore(
                user,
                40 + weekOffset,
                "risk week " + weekOffset,
                now.minusWeeks(weekOffset)
            );
        }

        List<RiskTrendResponse> trends = dashboardService.getRiskTrends();

        assertEquals(true, trends.size() <= 8);
    }

    @Test
    void getAlertStatsTotalsMatchKnownSeededData() {
        User user = createUserWithRole("alert-user", "alert-user@example.com", RoleType.EMPLOYEE);

        saveAlert(user, AlertStatus.OPEN, AlertSeverity.HIGH, "Open high");
        saveAlert(user, AlertStatus.UNDER_INVESTIGATION, AlertSeverity.LOW, "Investigating low");
        saveAlert(user, AlertStatus.RESOLVED, AlertSeverity.CRITICAL, "Resolved critical");
        saveAlert(user, AlertStatus.RESOLVED, AlertSeverity.HIGH, "Resolved high");

        AlertStatsResponse stats = dashboardService.getAlertStats();

        assertEquals(1L, stats.totalOpen());
        assertEquals(1L, stats.totalUnderInvestigation());
        assertEquals(2L, stats.totalResolved());

        Map<String, Long> bySeverity = stats.bySeverity();
        assertEquals(1L, bySeverity.get("LOW"));
        assertEquals(2L, bySeverity.get("HIGH"));
        assertEquals(1L, bySeverity.get("CRITICAL"));
    }

    private User createUserWithRole(String username, String email, RoleType roleType) {
        Role role = roleRepository.findByName(roleType).orElseGet(() -> {
            Role createdRole = new Role();
            createdRole.setName(roleType);
            return roleRepository.saveAndFlush(createdRole);
        });

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash("hashed-password");
        user.setRole(role);
        user.setActive(true);
        user.setEmailVerified(true);
        return userRepository.save(user);
    }

    private RiskScore createRiskScore(User user, int scoreValue, String reason, LocalDateTime calculatedAt) {
        RiskScore score = new RiskScore();
        score.setUser(user);
        score.setScore(scoreValue);
        score.setReason(reason);
        score.setCalculatedAt(calculatedAt);
        return riskScoreRepository.save(score);
    }

    private Alert saveAlert(User user, AlertStatus status, AlertSeverity severity, String message) {
        Alert alert = new Alert();
        alert.setUser(user);
        alert.setStatus(status);
        alert.setSeverity(severity);
        alert.setMessage(message);
        alert.setCreatedAt(LocalDateTime.now());
        alert.setUpdatedAt(LocalDateTime.now());
        return alertRepository.save(alert);
    }
}
