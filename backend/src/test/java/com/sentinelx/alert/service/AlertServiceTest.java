package com.sentinelx.alert.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sentinelx.activity.entity.Activity;
import com.sentinelx.activity.repository.ActivityRepository;
import com.sentinelx.alert.dto.AlertResponse;
import com.sentinelx.alert.entity.AlertSeverity;
import com.sentinelx.alert.entity.AlertStatus;
import com.sentinelx.alert.exception.AlertAccessDeniedException;
import com.sentinelx.alert.repository.AlertRepository;
import com.sentinelx.risk.dto.RiskScoreResponse;
import com.sentinelx.risk.entity.RiskScore;
import com.sentinelx.risk.repository.RiskScoreRepository;
import com.sentinelx.risk.service.RiskScoreService;
import com.sentinelx.user.entity.Role;
import com.sentinelx.user.entity.RoleType;
import com.sentinelx.user.entity.User;
import com.sentinelx.user.repository.RoleRepository;
import com.sentinelx.user.repository.UserRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
class AlertServiceTest {

    private static final String TEST_SECRET =
        "alert_service_test_secret_with_sufficient_length_123456789";

    @Autowired
    private AlertService alertService;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private RiskScoreRepository riskScoreRepository;

    @Autowired
    private RiskScoreService riskScoreService;

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @MockBean
    private AuthenticationManager authenticationManager;

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:alertservicetest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
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
        userRepository.deleteAll();
    }

    @Test
    void generateAlertCreatesCorrectSeverityForScoreRanges() {
        User user = createUserWithRole("alice", "alice@example.com", RoleType.EMPLOYEE);

        AlertResponse low = alertService.generateAlert(user, createRiskScore(user, 20, "low"));
        AlertResponse medium = alertService.generateAlert(user, createRiskScore(user, 55, "medium"));
        AlertResponse high = alertService.generateAlert(user, createRiskScore(user, 75, "high"));
        AlertResponse critical = alertService.generateAlert(user, createRiskScore(user, 90, "critical"));

        assertEquals(AlertSeverity.LOW, low.severity());
        assertEquals(AlertSeverity.MEDIUM, medium.severity());
        assertEquals(AlertSeverity.HIGH, high.severity());
        assertEquals(AlertSeverity.CRITICAL, critical.severity());
    }

    @Test
    void acknowledgeAlertByOwnerSucceeds() {
        User owner = createUserWithRole("bob", "bob@example.com", RoleType.EMPLOYEE);
        AlertResponse alert = alertService.generateAlert(owner, createRiskScore(owner, 80, "risk"));

        AlertResponse updated = alertService.acknowledgeAlert(alert.id(), owner);

        assertEquals(AlertStatus.ACKNOWLEDGED, updated.status());
    }

    @Test
    void acknowledgeAlertByDifferentNonAdminUserThrows403() {
        User owner = createUserWithRole("charlie", "charlie@example.com", RoleType.EMPLOYEE);
        User otherUser = createUserWithRole("diana", "diana@example.com", RoleType.EMPLOYEE);

        AlertResponse alert = alertService.generateAlert(owner, createRiskScore(owner, 85, "risk"));

        assertThrows(AlertAccessDeniedException.class, () -> alertService.acknowledgeAlert(alert.id(), otherUser));
    }

    @Test
    void resolveAlertByAdminSucceedsRegardlessOfOwner() {
        User owner = createUserWithRole("eve", "eve@example.com", RoleType.EMPLOYEE);
        User admin = createUserWithRole("admin", "admin@example.com", RoleType.ADMIN);

        AlertResponse alert = alertService.generateAlert(owner, createRiskScore(owner, 88, "risk"));

        AlertResponse updated = alertService.resolveAlert(alert.id(), admin);

        assertEquals(AlertStatus.RESOLVED, updated.status());
    }

    @Test
    void evaluateRiskTriggersAlertCreationWhenScoreExceedsThreshold() {
        User user = createUserWithRole("frank", "frank@example.com", RoleType.EMPLOYEE);

        for (int index = 0; index < 12; index++) {
            Activity activity = new Activity();
            activity.setUser(user);
            activity.setAction("LOGIN");
            activity.setEntityType("AUTH");
            activity.setEntityId("session-" + index);
            activity.setMetadata("{}");
            activity.setCreatedAt(LocalDateTime.now().withHour(23).minusMinutes(index));
            activityRepository.save(activity);
        }

        RiskScoreResponse score = riskScoreService.evaluateRisk(user);

        assertTrue(score.score() >= 60);
        assertEquals(1, alertRepository.count());
    }

    private User createUserWithRole(String username, String email, RoleType roleType) {
        Role role = roleRepository.findByName(roleType).orElseGet(() -> {
            Role createdRole = new Role();
            createdRole.setName(roleType);
            return roleRepository.save(createdRole);
        });

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash("hashed-password");
        user.setRole(role);
        user.setActive(true);
        return userRepository.save(user);
    }

    private RiskScore createRiskScore(User user, int value, String reason) {
        RiskScore score = new RiskScore();
        score.setUser(user);
        score.setScore(value);
        score.setReason(reason);
        return riskScoreRepository.save(score);
    }
}
