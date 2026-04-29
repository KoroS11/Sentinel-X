package com.sentinelx.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelx.activity.entity.Activity;
import com.sentinelx.activity.repository.ActivityRepository;
import com.sentinelx.activity.service.ActivityService;
import com.sentinelx.alert.entity.Alert;
import com.sentinelx.alert.entity.AlertStatus;
import com.sentinelx.alert.repository.AlertRepository;
import com.sentinelx.auth.email.EmailService;
import com.sentinelx.auth.repository.EmailVerificationTokenRepository;
import com.sentinelx.auth.repository.PasswordResetTokenRepository;
import com.sentinelx.auth.repository.RefreshTokenRepository;
import com.sentinelx.risk.repository.RiskScoreRepository;
import com.sentinelx.user.entity.Role;
import com.sentinelx.user.entity.RoleType;
import com.sentinelx.user.entity.User;
import com.sentinelx.user.repository.RoleRepository;
import com.sentinelx.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.transaction.annotation.Transactional
class E2EProtectedModulesTest {

    private static final String DB_URL = "jdbc:h2:mem:e2eprotectedmodulestest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
    private static final String DB_DRIVER = "org.h2.Driver";
    private static final String DB_USER = "sa";
    private static final String DB_PASSWORD = "";
    private static final String DDL_MODE = "create-drop";
    private static final String FLYWAY_DISABLED = "false";
    private static final String JWT_EXPIRATION_MS = "3600000";
    private static final String JWT_REFRESH_EXPIRATION_MS = "604800000";

    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private static final String AUTH_LOGIN = "/api/auth/login";
    private static final String ACTIVITIES_ME = "/api/activities/me";
    private static final String ACTIVITIES_BY_ENTITY = "/api/activities/entity/{entityType}";
    private static final String DASHBOARD_ME = "/api/dashboard/me";
    private static final String DASHBOARD_ADMIN = "/api/dashboard/admin";
    private static final String RISK_ME = "/api/risk/me";
    private static final String ALERTS_ME = "/api/alerts/me";
    private static final String ALERT_ACK = "/api/alerts/{id}/acknowledge";
    private static final String ALERT_RESOLVE = "/api/alerts/{id}/resolve";

    private static final String SAMPLE_ACTION = "LOGIN";
    private static final String SAMPLE_ENTITY_TYPE = "LOGIN";
    private static final String SAMPLE_ENTITY_ID = "entity-1";
    private static final String SAMPLE_METADATA = "e2e-flow";

    private static final int ACTIVITY_COUNT_FOR_HIGH_RISK = 10;
    private static final int OFF_HOUR_FOR_HIGH_RISK = 23;

    private static final String JWT_SECRET =
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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ActivityService activityService;

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private RiskScoreRepository riskScoreRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @MockBean
    private EmailService emailService;

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DB_URL);
        registry.add("spring.datasource.driver-class-name", () -> DB_DRIVER);
        registry.add("spring.datasource.username", () -> DB_USER);
        registry.add("spring.datasource.password", () -> DB_PASSWORD);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> DDL_MODE);
        registry.add("spring.flyway.enabled", () -> FLYWAY_DISABLED);
        registry.add("jwt.secret", () -> JWT_SECRET);
        registry.add("jwt.expiration-ms", () -> JWT_EXPIRATION_MS);
        registry.add("jwt.refresh-expiration-ms", () -> JWT_REFRESH_EXPIRATION_MS);
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
    void endToEndProtectedModuleFlows() throws Exception {
        String employeePassword = generatedPassword();
        String analystPassword = generatedPassword();
        String adminPassword = generatedPassword();

        User employee = createUserWithRole(uniqueValue("employee"), uniqueEmail("employee"), employeePassword, RoleType.EMPLOYEE);
        User analyst = createUserWithRole(uniqueValue("analyst"), uniqueEmail("analyst"), analystPassword, RoleType.ANALYST);
        User admin = createUserWithRole(uniqueValue("admin"), uniqueEmail("admin"), adminPassword, RoleType.ADMIN);

        String employeeToken = loginAndGetAccessToken(employee.getEmail(), employeePassword);
        String analystToken = loginAndGetAccessToken(analyst.getEmail(), analystPassword);
        String adminToken = loginAndGetAccessToken(admin.getEmail(), adminPassword);

        activityService.logActivity(employee, SAMPLE_ACTION, SAMPLE_ENTITY_TYPE, SAMPLE_ENTITY_ID, SAMPLE_METADATA);

        mockMvc.perform(get(ACTIVITIES_ME)
                .header(AUTH_HEADER, BEARER_PREFIX + employeeToken))
            .andExpect(status().isOk());

        mockMvc.perform(get(DASHBOARD_ME)
                .header(AUTH_HEADER, BEARER_PREFIX + employeeToken))
            .andExpect(status().isOk());

        mockMvc.perform(get(ALERTS_ME)
                .header(AUTH_HEADER, BEARER_PREFIX + employeeToken))
            .andExpect(status().isOk());

        mockMvc.perform(get(ACTIVITIES_BY_ENTITY, SAMPLE_ENTITY_TYPE)
                .header(AUTH_HEADER, BEARER_PREFIX + analystToken))
            .andExpect(status().isOk());

        mockMvc.perform(get(DASHBOARD_ADMIN)
                .header(AUTH_HEADER, BEARER_PREFIX + analystToken))
            .andExpect(status().isForbidden());

        Long generatedAlertId = createHighRiskAlertFor(employee, employeeToken);

        mockMvc.perform(get(RISK_ME)
                .header(AUTH_HEADER, BEARER_PREFIX + employeeToken))
            .andExpect(status().isOk());

        mockMvc.perform(patch(ALERT_ACK, generatedAlertId)
                .header(AUTH_HEADER, BEARER_PREFIX + employeeToken))
            .andExpect(status().isOk());

        mockMvc.perform(patch(ALERT_RESOLVE, generatedAlertId)
                .header(AUTH_HEADER, BEARER_PREFIX + adminToken))
            .andExpect(status().isOk());

        Alert resolvedAlert = alertRepository.findById(generatedAlertId).orElseThrow();
        assertEquals(AlertStatus.RESOLVED, resolvedAlert.getStatus());

        mockMvc.perform(get(DASHBOARD_ADMIN)
                .header(AUTH_HEADER, BEARER_PREFIX + adminToken))
            .andExpect(status().isOk());
    }

    private Long createHighRiskAlertFor(User employee, String employeeToken) throws Exception {
        for (int index = 0; index < ACTIVITY_COUNT_FOR_HIGH_RISK; index++) {
            activityService.logActivity(
                employee,
                SAMPLE_ACTION,
                SAMPLE_ENTITY_TYPE,
                SAMPLE_ENTITY_ID + "-" + index,
                SAMPLE_METADATA
            );
        }

        List<Activity> activities = activityRepository.findAllByUser(
            employee,
            PageRequest.of(0, ACTIVITY_COUNT_FOR_HIGH_RISK, Sort.by(Sort.Direction.DESC, "createdAt"))
        ).getContent();

        LocalDateTime offHoursTimestamp = LocalDateTime.now().withHour(OFF_HOUR_FOR_HIGH_RISK).withMinute(0).withSecond(0).withNano(0);
        for (Activity activity : activities) {
            activity.setCreatedAt(offHoursTimestamp);
        }
        activityRepository.saveAll(activities);

        mockMvc.perform(get(RISK_ME)
                .header(AUTH_HEADER, BEARER_PREFIX + employeeToken))
            .andExpect(status().isOk());

        String alertsResponse = mockMvc.perform(get(ALERTS_ME)
                .header(AUTH_HEADER, BEARER_PREFIX + employeeToken)
                .param("status", AlertStatus.OPEN.name()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        JsonNode root = objectMapper.readTree(alertsResponse);
        JsonNode content = root.path("content");
        assertFalse(content.isEmpty());

        return content.get(0).path("id").asLong();
    }

    private User createUserWithRole(String username, String email, String rawPassword, RoleType roleType) {
        Role role = roleRepository.findByName(roleType).orElseGet(() -> {
            Role created = new Role();
            created.setName(roleType);
            return roleRepository.saveAndFlush(created);
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
        String response = mockMvc.perform(post(AUTH_LOGIN)
                .contentType(CONTENT_TYPE_JSON)
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

    private String uniqueValue(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String uniqueEmail(String prefix) {
        return uniqueValue(prefix) + "@example.com";
    }

    private String generatedPassword() {
        return "Pw@" + UUID.randomUUID().toString().replace("-", "") + "1";
    }
}
