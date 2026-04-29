package com.sentinelx.risk.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sentinelx.activity.repository.ActivityRepository;
import com.sentinelx.alert.service.AlertService;
import com.sentinelx.risk.dto.RiskScoreResponse;
import com.sentinelx.risk.entity.RiskScore;
import com.sentinelx.risk.repository.RiskScoreRepository;
import com.sentinelx.risk.strategy.BasicRiskScoringStrategy;
import com.sentinelx.user.entity.Role;
import com.sentinelx.user.entity.RoleType;
import com.sentinelx.user.entity.User;
import com.sentinelx.user.exception.ResourceNotFoundException;
import com.sentinelx.user.repository.RoleRepository;
import com.sentinelx.user.repository.UserRepository;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({RiskScoreService.class, BasicRiskScoringStrategy.class, AlertService.class})
class RiskScoreServiceTest {

    private static final long UNKNOWN_USER_ID = 9999L;

    @Autowired
    private RiskScoreService riskScoreService;

    @Autowired
    private RiskScoreRepository riskScoreRepository;

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Test
    void evaluateRiskSavesScoreRecord() {
        User user = createUser("alice", "alice@example.com");

        RiskScoreResponse response = riskScoreService.evaluateRisk(user);

        assertTrue(response.id() != null);
        assertEquals(1, riskScoreRepository.count());
    }

    @Test
    void getLatestRiskScoreReturnsMostRecentNotOldest() {
        User user = createUser("bob", "bob@example.com");

        RiskScore oldScore = new RiskScore();
        oldScore.setUser(user);
        oldScore.setScore(20);
        oldScore.setReason("old");
        oldScore.setCalculatedAt(LocalDateTime.now().minusHours(2));
        riskScoreRepository.save(oldScore);

        RiskScore newScore = new RiskScore();
        newScore.setUser(user);
        newScore.setScore(80);
        newScore.setReason("new");
        newScore.setCalculatedAt(LocalDateTime.now());
        riskScoreRepository.save(newScore);

        RiskScoreResponse latest = riskScoreService.getLatestRiskScore(user).orElseThrow();

        assertEquals(80, latest.score());
        assertEquals("new", latest.reason());
    }

    @Test
    void getLatestRiskScoreByUserIdWithUnknownUserIdThrowsResourceNotFoundException() {
        assertThrows(
            ResourceNotFoundException.class,
            () -> riskScoreService.getLatestRiskScoreByUserId(UNKNOWN_USER_ID)
        );
    }

    @Test
    void getLatestRiskScoreByUserIdWithoutExistingScoreEvaluatesRisk() {
        User user = createUser("no-score", "no-score@example.com");

        RiskScoreResponse response = riskScoreService.getLatestRiskScoreByUserId(user.getId());

        assertTrue(response.id() != null);
    }

    @Test
    void getRiskHistoryByUserIdWithUnknownUserIdThrowsResourceNotFoundException() {
        assertThrows(
            ResourceNotFoundException.class,
            () -> riskScoreService.getRiskHistoryByUserId(UNKNOWN_USER_ID, PageRequest.of(0, 10))
        );
    }

    @Test
    void getRiskHistoryByUserIdReturnsPagedHistory() {
        User user = createUser("history-user", "history-user@example.com");

        RiskScore first = new RiskScore();
        first.setUser(user);
        first.setScore(35);
        first.setReason("first");
        first.setCalculatedAt(LocalDateTime.now().minusHours(1));
        riskScoreRepository.save(first);

        RiskScore second = new RiskScore();
        second.setUser(user);
        second.setScore(65);
        second.setReason("second");
        second.setCalculatedAt(LocalDateTime.now());
        riskScoreRepository.save(second);

        Page<RiskScoreResponse> history = riskScoreService.getRiskHistoryByUserId(user.getId(), PageRequest.of(0, 10));

        assertEquals(2, history.getTotalElements());
        assertEquals(65, history.getContent().get(0).score());
    }

    private User createUser(String username, String email) {
        Role role = roleRepository.findByName(RoleType.EMPLOYEE).orElseGet(() -> {
            Role newRole = new Role();
            newRole.setName(RoleType.EMPLOYEE);
            return roleRepository.saveAndFlush(newRole);
        });

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash("hashed-password");
        user.setRole(role);
        user.setActive(true);
        return userRepository.save(user);
    }
}
