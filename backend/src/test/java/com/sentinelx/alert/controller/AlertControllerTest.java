package com.sentinelx.alert.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelx.alert.dto.AlertResponse;
import com.sentinelx.alert.entity.AlertStatus;
import com.sentinelx.alert.repository.AlertRepository;
import com.sentinelx.alert.service.AlertService;
import com.sentinelx.auth.repository.EmailVerificationTokenRepository;
import com.sentinelx.auth.repository.PasswordResetTokenRepository;
import com.sentinelx.auth.repository.RefreshTokenRepository;
import com.sentinelx.risk.entity.RiskScore;
import com.sentinelx.risk.repository.RiskScoreRepository;
import com.sentinelx.user.entity.Role;
import com.sentinelx.user.entity.RoleType;
import com.sentinelx.user.entity.User;
import com.sentinelx.user.repository.RoleRepository;
import com.sentinelx.user.repository.UserRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AlertControllerTest {

    private static final String TEST_SECRET =
        UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
    private static final String DEFAULT_PASSWORD = "Password@123";
    private static final long NON_EXISTENT_USER_ID = 9999L;
    private static final long NON_EXISTENT_ALERT_ID = 9999L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AlertService alertService;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private RiskScoreRepository riskScoreRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:alertcontrollertest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
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
        emailVerificationTokenRepository.deleteAll();
        passwordResetTokenRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void getAlertsMeWithoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/alerts/me"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void patchAcknowledgeByWrongUserReturnsForbidden() throws Exception {
        User owner = createUserWithRole("owner", "owner@example.com", DEFAULT_PASSWORD, RoleType.EMPLOYEE);
        User other = createUserWithRole("other", "other@example.com", DEFAULT_PASSWORD, RoleType.EMPLOYEE);

        RiskScore ownerScore = createRiskScore(owner, 85, "elevated");
        AlertResponse alert = alertService.generateAlert(owner, ownerScore);

        String otherUserToken = loginAndGetAccessToken(other.getEmail(), DEFAULT_PASSWORD);

        mockMvc.perform(patch("/api/alerts/{id}/acknowledge", alert.id())
                .header("Authorization", "Bearer " + otherUserToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void assignAlertByEmployeeReturnsForbidden() throws Exception {
        User employee = createUserWithRole("employee-a", "employee-a@example.com", DEFAULT_PASSWORD, RoleType.EMPLOYEE);
        User owner = createUserWithRole("owner-a", "owner-a@example.com", DEFAULT_PASSWORD, RoleType.EMPLOYEE);
        AlertResponse alert = alertService.generateAlert(owner, createRiskScore(owner, 76, "elevated"));
        String employeeToken = loginAndGetAccessToken(employee.getEmail(), DEFAULT_PASSWORD);

        mockMvc.perform(post("/api/alerts/{id}/assign", alert.id())
                .header("Authorization", "Bearer " + employeeToken)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(assignRequest(owner.getId()))))
            .andExpect(status().isForbidden());
    }

    @Test
    void assignAlertWithNonexistentAssigneeThrowsResourceNotFound() throws Exception {
        User analyst = createUserWithRole("analyst-a", "analyst-a@example.com", DEFAULT_PASSWORD, RoleType.ANALYST);
        User owner = createUserWithRole("owner-b", "owner-b@example.com", DEFAULT_PASSWORD, RoleType.EMPLOYEE);
        AlertResponse alert = alertService.generateAlert(owner, createRiskScore(owner, 86, "elevated"));
        String analystToken = loginAndGetAccessToken(analyst.getEmail(), DEFAULT_PASSWORD);

        mockMvc.perform(post("/api/alerts/{id}/assign", alert.id())
                .header("Authorization", "Bearer " + analystToken)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(assignRequest(NON_EXISTENT_USER_ID))))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteAlertByNonAdminReturnsForbidden() throws Exception {
        User analyst = createUserWithRole("analyst-b", "analyst-b@example.com", DEFAULT_PASSWORD, RoleType.ANALYST);
        User owner = createUserWithRole("owner-c", "owner-c@example.com", DEFAULT_PASSWORD, RoleType.EMPLOYEE);
        AlertResponse alert = alertService.generateAlert(owner, createRiskScore(owner, 82, "elevated"));
        String analystToken = loginAndGetAccessToken(analyst.getEmail(), DEFAULT_PASSWORD);

        mockMvc.perform(delete("/api/alerts/{id}", alert.id())
                .header("Authorization", "Bearer " + analystToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void deleteAlertByAdminReturnsNoContent() throws Exception {
        User admin = createUserWithRole("admin-a", "admin-a@example.com", DEFAULT_PASSWORD, RoleType.ADMIN);
        User owner = createUserWithRole("owner-d", "owner-d@example.com", DEFAULT_PASSWORD, RoleType.EMPLOYEE);
        AlertResponse alert = alertService.generateAlert(owner, createRiskScore(owner, 83, "elevated"));
        String adminToken = loginAndGetAccessToken(admin.getEmail(), DEFAULT_PASSWORD);

        mockMvc.perform(delete("/api/alerts/{id}", alert.id())
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isNoContent());
    }

    @Test
    void getAlertByIdByOwnerReturnsOk() throws Exception {
        User owner = createUserWithRole("owner-e", "owner-e@example.com", DEFAULT_PASSWORD, RoleType.EMPLOYEE);
        AlertResponse alert = alertService.generateAlert(owner, createRiskScore(owner, 88, "elevated"));
        String ownerToken = loginAndGetAccessToken(owner.getEmail(), DEFAULT_PASSWORD);

        mockMvc.perform(get("/api/alerts/{id}", alert.id())
                .header("Authorization", "Bearer " + ownerToken))
            .andExpect(status().isOk());
    }

    @Test
    void getAlertByIdByDifferentEmployeeReturnsForbidden() throws Exception {
        User owner = createUserWithRole("owner-f", "owner-f@example.com", DEFAULT_PASSWORD, RoleType.EMPLOYEE);
        User other = createUserWithRole("other-f", "other-f@example.com", DEFAULT_PASSWORD, RoleType.EMPLOYEE);
        AlertResponse alert = alertService.generateAlert(owner, createRiskScore(owner, 89, "elevated"));
        String otherToken = loginAndGetAccessToken(other.getEmail(), DEFAULT_PASSWORD);

        mockMvc.perform(get("/api/alerts/{id}", alert.id())
                .header("Authorization", "Bearer " + otherToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void getAlertByIdWithoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/alerts/{id}", NON_EXISTENT_ALERT_ID))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void getAlertByIdWithAdminAndUnknownIdReturnsNotFound() throws Exception {
        User admin = createUserWithRole("admin-not-found", "admin-not-found@example.com", DEFAULT_PASSWORD, RoleType.ADMIN);
        String adminToken = loginAndGetAccessToken(admin.getEmail(), DEFAULT_PASSWORD);

        mockMvc.perform(get("/api/alerts/{id}", NON_EXISTENT_ALERT_ID)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isNotFound());
    }

    @Test
    void updateAlertStatusOpenToUnderInvestigationSucceeds() throws Exception {
        User owner = createUserWithRole("owner-g", "owner-g@example.com", DEFAULT_PASSWORD, RoleType.EMPLOYEE);
        AlertResponse alert = alertService.generateAlert(owner, createRiskScore(owner, 91, "elevated"));
        String ownerToken = loginAndGetAccessToken(owner.getEmail(), DEFAULT_PASSWORD);

        mockMvc.perform(patch("/api/alerts/{id}/status", alert.id())
                .header("Authorization", "Bearer " + ownerToken)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(statusRequest(AlertStatus.UNDER_INVESTIGATION))))
            .andExpect(status().isOk());
    }

    @Test
    void updateAlertStatusOpenToResolvedThrowsIllegalTransition() throws Exception {
        User owner = createUserWithRole("owner-h", "owner-h@example.com", DEFAULT_PASSWORD, RoleType.EMPLOYEE);
        AlertResponse alert = alertService.generateAlert(owner, createRiskScore(owner, 93, "elevated"));
        String ownerToken = loginAndGetAccessToken(owner.getEmail(), DEFAULT_PASSWORD);

        mockMvc.perform(patch("/api/alerts/{id}/status", alert.id())
                .header("Authorization", "Bearer " + ownerToken)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(statusRequest(AlertStatus.RESOLVED))))
            .andExpect(status().isConflict());
    }

            @Test
            void updateAlertStatusWithInvalidBodyReturnsBadRequest() throws Exception {
            User owner = createUserWithRole("owner-invalid", "owner-invalid@example.com", DEFAULT_PASSWORD, RoleType.EMPLOYEE);
            AlertResponse alert = alertService.generateAlert(owner, createRiskScore(owner, 80, "elevated"));
            String ownerToken = loginAndGetAccessToken(owner.getEmail(), DEFAULT_PASSWORD);

            mockMvc.perform(patch("/api/alerts/{id}/status", alert.id())
                .header("Authorization", "Bearer " + ownerToken)
                .contentType("application/json")
                .content("{}"))
                .andExpect(status().isBadRequest());
            }

            @Test
            void assignAlertWithInvalidBodyReturnsBadRequest() throws Exception {
            User analyst = createUserWithRole("analyst-invalid", "analyst-invalid@example.com", DEFAULT_PASSWORD, RoleType.ANALYST);
            User owner = createUserWithRole("owner-invalid-assign", "owner-invalid-assign@example.com", DEFAULT_PASSWORD, RoleType.EMPLOYEE);
            AlertResponse alert = alertService.generateAlert(owner, createRiskScore(owner, 87, "elevated"));
            String analystToken = loginAndGetAccessToken(analyst.getEmail(), DEFAULT_PASSWORD);

            mockMvc.perform(post("/api/alerts/{id}/assign", alert.id())
                .header("Authorization", "Bearer " + analystToken)
                .contentType("application/json")
                .content("{}"))
                .andExpect(status().isBadRequest());
            }

    private String loginAndGetAccessToken(String email, String password) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(loginRequest(email, password))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        return objectMapper.readTree(response).path("token").asText();
    }

    private User createUserWithRole(String username, String email, String password, RoleType roleType) {
        Role role = roleRepository.findByName(roleType).orElseGet(() -> {
            Role newRole = new Role();
            newRole.setName(roleType);
            return roleRepository.save(newRole);
        });

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(role);
        user.setActive(true);
        user.setEmailVerified(true);
        return userRepository.save(user);
    }

    private RiskScore createRiskScore(User user, int value, String reason) {
        RiskScore score = new RiskScore();
        score.setUser(user);
        score.setScore(value);
        score.setReason(reason);
        return riskScoreRepository.save(score);
    }

    private Map<String, String> loginRequest(String email, String password) {
        Map<String, String> request = new HashMap<>();
        request.put("email", email);
        request.put("password", password);
        return request;
    }

    private Map<String, Object> statusRequest(AlertStatus status) {
        Map<String, Object> request = new HashMap<>();
        request.put("status", status.name());
        return request;
    }

    private Map<String, Object> assignRequest(Long assigneeUserId) {
        Map<String, Object> request = new HashMap<>();
        request.put("assigneeUserId", assigneeUserId);
        return request;
    }
}
