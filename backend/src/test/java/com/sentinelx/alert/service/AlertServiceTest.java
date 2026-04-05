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
import com.sentinelx.alert.exception.AlertInvalidStatusTransitionException;
import com.sentinelx.alert.repository.AlertRepository;
import com.sentinelx.risk.dto.RiskScoreResponse;
import com.sentinelx.risk.entity.RiskScore;
import com.sentinelx.risk.repository.RiskScoreRepository;
import com.sentinelx.risk.service.RiskScoreService;
import com.sentinelx.user.entity.Role;
import com.sentinelx.user.entity.RoleType;
import com.sentinelx.user.entity.User;
import com.sentinelx.user.exception.ResourceNotFoundException;
import com.sentinelx.user.repository.RoleRepository;
import com.sentinelx.user.repository.UserRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
class AlertServiceTest {

    private static final String TEST_SECRET =
        "alert_service_test_secret_with_sufficient_length_123456789";
    private static final String DEFAULT_PASSWORD_HASH = "hashed-password";

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

    @Test
    void updateAlertStatusOpenToUnderInvestigationSucceeds() {
        User owner = createUserWithRole("gina", "gina@example.com", RoleType.EMPLOYEE);
        AlertResponse alert = alertService.generateAlert(owner, createRiskScore(owner, 78, "risk"));

        AlertResponse updated = alertService.updateAlertStatus(
            alert.id(),
            AlertStatus.UNDER_INVESTIGATION,
            owner
        );

        assertEquals(AlertStatus.UNDER_INVESTIGATION, updated.status());
    }

    @Test
    void updateAlertStatusOpenToResolvedThrowsIllegalTransitionException() {
        User owner = createUserWithRole("hank", "hank@example.com", RoleType.EMPLOYEE);
        AlertResponse alert = alertService.generateAlert(owner, createRiskScore(owner, 72, "risk"));

        assertThrows(
            AlertInvalidStatusTransitionException.class,
            () -> alertService.updateAlertStatus(alert.id(), AlertStatus.RESOLVED, owner)
        );
    }

    @Test
    void updateAlertStatusUnderInvestigationToResolvedSucceeds() {
        User owner = createUserWithRole("ivy", "ivy@example.com", RoleType.EMPLOYEE);
        AlertResponse alert = alertService.generateAlert(owner, createRiskScore(owner, 82, "risk"));

        alertService.updateAlertStatus(alert.id(), AlertStatus.UNDER_INVESTIGATION, owner);
        AlertResponse resolved = alertService.updateAlertStatus(alert.id(), AlertStatus.RESOLVED, owner);

        assertEquals(AlertStatus.RESOLVED, resolved.status());
    }

    @Test
    void updateAlertStatusResolvedToOpenThrowsIllegalTransitionException() {
        User owner = createUserWithRole("jack", "jack@example.com", RoleType.EMPLOYEE);
        AlertResponse alert = alertService.generateAlert(owner, createRiskScore(owner, 89, "risk"));

        alertService.updateAlertStatus(alert.id(), AlertStatus.UNDER_INVESTIGATION, owner);
        alertService.updateAlertStatus(alert.id(), AlertStatus.RESOLVED, owner);

        assertThrows(
            AlertInvalidStatusTransitionException.class,
            () -> alertService.updateAlertStatus(alert.id(), AlertStatus.OPEN, owner)
        );
    }

    @Test
    void assignAlertWithNonexistentAssigneeThrowsResourceNotFoundException() {
        User analyst = createUserWithRole("analyst", "analyst@example.com", RoleType.ANALYST);
        User owner = createUserWithRole("owner2", "owner2@example.com", RoleType.EMPLOYEE);
        AlertResponse alert = alertService.generateAlert(owner, createRiskScore(owner, 77, "risk"));

        assertThrows(
            ResourceNotFoundException.class,
            () -> alertService.assignAlert(alert.id(), 9999L, analyst)
        );
    }

    @Test
    void deleteAlertByAdminRemovesAlert() {
        User admin = createUserWithRole("admin2", "admin2@example.com", RoleType.ADMIN);
        User owner = createUserWithRole("owner3", "owner3@example.com", RoleType.EMPLOYEE);
        AlertResponse alert = alertService.generateAlert(owner, createRiskScore(owner, 84, "risk"));

        alertService.deleteAlert(alert.id(), admin);

        assertEquals(0, alertRepository.count());
    }

    @Test
    void getAlertByIdForOwnerReturnsAlert() {
        User owner = createUserWithRole("owner-view", "owner-view@example.com", RoleType.EMPLOYEE);
        AlertResponse created = alertService.generateAlert(owner, createRiskScore(owner, 81, "risk"));

        AlertResponse fetched = alertService.getAlertById(created.id(), owner);

        assertEquals(created.id(), fetched.id());
        assertEquals(owner.getId(), fetched.userId());
    }

    @Test
    void getAlertByIdWithUnknownIdThrowsResourceNotFoundException() {
        User owner = createUserWithRole("owner-view-nf", "owner-view-nf@example.com", RoleType.EMPLOYEE);

        assertThrows(ResourceNotFoundException.class, () -> alertService.getAlertById(9999L, owner));
    }

    @Test
    void updateAlertStatusWithUnknownIdThrowsResourceNotFoundException() {
        User owner = createUserWithRole("owner-update-nf", "owner-update-nf@example.com", RoleType.EMPLOYEE);

        assertThrows(
            ResourceNotFoundException.class,
            () -> alertService.updateAlertStatus(9999L, AlertStatus.UNDER_INVESTIGATION, owner)
        );
    }

    @Test
    void assignAlertByEmployeeThrowsAccessDeniedException() {
        User employee = createUserWithRole("employee-assign", "employee-assign@example.com", RoleType.EMPLOYEE);
        User owner = createUserWithRole("owner-assign", "owner-assign@example.com", RoleType.EMPLOYEE);
        AlertResponse alert = alertService.generateAlert(owner, createRiskScore(owner, 86, "risk"));

        assertThrows(
            AccessDeniedException.class,
            () -> alertService.assignAlert(alert.id(), owner.getId(), employee)
        );
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
        user.setPasswordHash(DEFAULT_PASSWORD_HASH);
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
