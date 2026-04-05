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

    private User createUser(String username, String email) {
        Role role = roleRepository.findByName(RoleType.EMPLOYEE).orElseGet(() -> {
            Role newRole = new Role();
            newRole.setName(RoleType.EMPLOYEE);
            return roleRepository.save(newRole);
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
