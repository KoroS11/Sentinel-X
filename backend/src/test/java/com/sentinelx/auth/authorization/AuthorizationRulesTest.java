package com.sentinelx.auth.authorization;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelx.activity.repository.ActivityRepository;
import com.sentinelx.alert.repository.AlertRepository;
import com.sentinelx.auth.repository.EmailVerificationTokenRepository;
import com.sentinelx.auth.repository.PasswordResetTokenRepository;
import com.sentinelx.auth.repository.RefreshTokenRepository;
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
class AuthorizationRulesTest {

    private static final String TEST_SECRET =
        UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private RiskScoreRepository riskScoreRepository;

    @Autowired
    private AlertRepository alertRepository;

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
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:authorizationrulestest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
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
    void employeeAccessingOwnResourcesReturnsOk() throws Exception {
        User employee = createUserWithRole("employee", "employee@example.com", "Password@123", RoleType.EMPLOYEE);
        String employeeToken = loginAndGetAccessToken(employee.getEmail(), "Password@123");

        mockMvc.perform(get("/api/dashboard/me")
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isOk());
    }

    @Test
    void employeeAccessingAnotherUsersResourcesReturnsForbidden() throws Exception {
        User employee = createUserWithRole("employee", "employee2@example.com", "Password@123", RoleType.EMPLOYEE);
        User target = createUserWithRole("target", "target@example.com", "Password@123", RoleType.EMPLOYEE);
        String employeeToken = loginAndGetAccessToken(employee.getEmail(), "Password@123");

        mockMvc.perform(get("/api/risk/user/{userId}", target.getId())
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void analystAccessingAllActivitiesReturnsOk() throws Exception {
        User analyst = createUserWithRole("analyst", "analyst@example.com", "Password@123", RoleType.ANALYST);
        String analystToken = loginAndGetAccessToken(analyst.getEmail(), "Password@123");

        mockMvc.perform(get("/api/activities/entity/ALERT")
                .header("Authorization", "Bearer " + analystToken))
            .andExpect(status().isOk());
    }

    @Test
    void analystTryingToAccessAdminDashboardReturnsForbidden() throws Exception {
        User analyst = createUserWithRole("analyst", "analyst2@example.com", "Password@123", RoleType.ANALYST);
        String analystToken = loginAndGetAccessToken(analyst.getEmail(), "Password@123");

        mockMvc.perform(get("/api/dashboard/admin")
                .header("Authorization", "Bearer " + analystToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void adminAccessingEverythingReturnsOk() throws Exception {
        User admin = createUserWithRole("admin", "admin@example.com", "Password@123", RoleType.ADMIN);
        User target = createUserWithRole("target", "target2@example.com", "Password@123", RoleType.EMPLOYEE);
        String adminToken = loginAndGetAccessToken(admin.getEmail(), "Password@123");

        mockMvc.perform(get("/api/activities/entity/ALERT")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/risk/user/{userId}", target.getId())
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/alerts")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/dashboard/admin")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());
    }

    private User createUserWithRole(String username, String email, String rawPassword, RoleType roleType) {
        Role role = roleRepository.findByName(roleType).orElseGet(() -> {
            Role createdRole = new Role();
            createdRole.setName(roleType);
            return roleRepository.save(createdRole);
        });

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        user.setActive(true);
        user.setEmailVerified(true);
        return userRepository.save(user);
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

    private Map<String, String> loginRequest(String email, String password) {
        Map<String, String> request = new HashMap<>();
        request.put("email", email);
        request.put("password", password);
        return request;
    }
}
