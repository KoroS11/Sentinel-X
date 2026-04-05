package com.sentinelx.activity.controller;

import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelx.auth.repository.EmailVerificationTokenRepository;
import com.sentinelx.auth.repository.PasswordResetTokenRepository;
import com.sentinelx.auth.repository.RefreshTokenRepository;
import com.sentinelx.activity.repository.ActivityRepository;
import com.sentinelx.activity.service.ActivityService;
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
class ActivityControllerTest {

    private static final String TEST_SECRET =
        UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
    private static final String DEFAULT_PASSWORD = "Password@123";
    private static final long SAMPLE_USER_ID_QUERY = 1L;
    private static final long UNKNOWN_ACTIVITY_ID = 9999L;
    private static final long UNKNOWN_USER_ID = 9999L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ActivityService activityService;

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:activitycontrollertest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
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
        activityRepository.deleteAll();
        emailVerificationTokenRepository.deleteAll();
        passwordResetTokenRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void getMyActivitiesWithoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/activities/me"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void getMyActivitiesWithValidTokenReturnsPaginatedResults() throws Exception {
        registerUser("alice", "alice@example.com", DEFAULT_PASSWORD);
        registerUser("other", "other@example.com", DEFAULT_PASSWORD);

        String aliceToken = loginAndGetAccessToken("alice@example.com", DEFAULT_PASSWORD);
        User alice = userRepository.findByEmail("alice@example.com").orElseThrow();
        User other = userRepository.findByEmail("other@example.com").orElseThrow();

        activityService.logActivity(alice, "LOGIN", "USER", "alice", "meta-1");
        activityService.logActivity(alice, "VIEW", "DASHBOARD", "d-1", "meta-2");
        activityService.logActivity(other, "DELETE", "ALERT", "a-1", "meta-3");

        mockMvc.perform(get("/api/activities/me")
                .param("page", "0")
                .param("size", "10")
                .header("Authorization", "Bearer " + aliceToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(2));
    }

    @Test
    void getActivitiesByEntityWithNonAdminTokenReturnsForbidden() throws Exception {
        registerUser("employee", "employee@example.com", DEFAULT_PASSWORD);
        String employeeToken = loginAndGetAccessToken("employee@example.com", DEFAULT_PASSWORD);

        mockMvc.perform(get("/api/activities/entity/ALERT")
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void getActivitiesByEntityWithAdminTokenReturnsOk() throws Exception {
        User admin = createUserWithRole("admin", "admin@example.com", DEFAULT_PASSWORD, RoleType.ADMIN);
        User employee = createUserWithRole("user", "user@example.com", DEFAULT_PASSWORD, RoleType.EMPLOYEE);
        activityService.logActivity(employee, "CREATE", "ALERT", "A-100", "meta");

        String adminToken = loginAndGetAccessToken(admin.getEmail(), DEFAULT_PASSWORD);

        mockMvc.perform(get("/api/activities/entity/ALERT")
                .param("page", "0")
                .param("size", "10")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(greaterThan(0)))
            .andExpect(jsonPath("$.content[0].entityType").value("ALERT"));
    }

            @Test
            void getActivitiesByUserIdWithEmployeeTokenReturnsForbidden() throws Exception {
            User employee = createUserWithRole("employee-a", "employee-a@example.com", DEFAULT_PASSWORD, RoleType.EMPLOYEE);
            String employeeToken = loginAndGetAccessToken(employee.getEmail(), DEFAULT_PASSWORD);

            mockMvc.perform(get("/api/activities")
                .param("userId", String.valueOf(SAMPLE_USER_ID_QUERY))
                .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isForbidden());
            }

            @Test
            void getActivitiesByUserIdWithoutTokenReturnsUnauthorized() throws Exception {
            mockMvc.perform(get("/api/activities")
                .param("userId", String.valueOf(SAMPLE_USER_ID_QUERY)))
                .andExpect(status().isUnauthorized());
            }

            @Test
            void getActivitiesByUserIdWithInvalidInputReturnsBadRequest() throws Exception {
            User analyst = createUserWithRole("analyst-invalid", "analyst-invalid@example.com", DEFAULT_PASSWORD, RoleType.ANALYST);
            String analystToken = loginAndGetAccessToken(analyst.getEmail(), DEFAULT_PASSWORD);

            mockMvc.perform(get("/api/activities")
                .param("userId", "not-a-number")
                .header("Authorization", "Bearer " + analystToken))
                .andExpect(status().isBadRequest());
            }

            @Test
            void getActivitiesByUserIdWithUnknownUserReturnsNotFound() throws Exception {
            User analyst = createUserWithRole("analyst-unknown", "analyst-unknown@example.com", DEFAULT_PASSWORD, RoleType.ANALYST);
            String analystToken = loginAndGetAccessToken(analyst.getEmail(), DEFAULT_PASSWORD);

            mockMvc.perform(get("/api/activities")
                .param("userId", String.valueOf(UNKNOWN_USER_ID))
                .header("Authorization", "Bearer " + analystToken))
                .andExpect(status().isNotFound());
            }

            @Test
            void getActivitiesByUserIdWithAnalystTokenReturnsOk() throws Exception {
            User analyst = createUserWithRole("analyst-a", "analyst-a@example.com", DEFAULT_PASSWORD, RoleType.ANALYST);
            User employee = createUserWithRole("employee-b", "employee-b@example.com", DEFAULT_PASSWORD, RoleType.EMPLOYEE);
            activityService.logActivity(employee, "VIEW", "DASHBOARD", "D-100", "meta");
            String analystToken = loginAndGetAccessToken(analyst.getEmail(), DEFAULT_PASSWORD);

            mockMvc.perform(get("/api/activities")
                .param("userId", String.valueOf(employee.getId()))
                .header("Authorization", "Bearer " + analystToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(greaterThan(0)));
            }

            @Test
            void getActivityByIdWithOwnerEmployeeTokenReturnsOk() throws Exception {
            User owner = createUserWithRole("owner-a", "owner-a@example.com", DEFAULT_PASSWORD, RoleType.EMPLOYEE);
            String ownerToken = loginAndGetAccessToken(owner.getEmail(), DEFAULT_PASSWORD);
            activityService.logActivity(owner, "LOGIN", "AUTH", "owner-session", "meta");

            Long activityId = activityRepository.findAll().stream().findFirst().orElseThrow().getId();

            mockMvc.perform(get("/api/activities/{id}", activityId)
                .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(owner.getId()));
            }

            @Test
            void getActivityByIdWithDifferentEmployeeTokenReturnsForbidden() throws Exception {
            User owner = createUserWithRole("owner-b", "owner-b@example.com", DEFAULT_PASSWORD, RoleType.EMPLOYEE);
            User otherEmployee = createUserWithRole("other-b", "other-b@example.com", DEFAULT_PASSWORD, RoleType.EMPLOYEE);
            String otherEmployeeToken = loginAndGetAccessToken(otherEmployee.getEmail(), DEFAULT_PASSWORD);
            activityService.logActivity(owner, "LOGIN", "AUTH", "owner-session-b", "meta");

            Long activityId = activityRepository.findAll().stream().findFirst().orElseThrow().getId();

            mockMvc.perform(get("/api/activities/{id}", activityId)
                .header("Authorization", "Bearer " + otherEmployeeToken))
                .andExpect(status().isForbidden());
            }

            @Test
            void getActivityByIdWithAdminTokenReturnsOk() throws Exception {
            User owner = createUserWithRole("owner-c", "owner-c@example.com", DEFAULT_PASSWORD, RoleType.EMPLOYEE);
            User admin = createUserWithRole("admin-c", "admin-c@example.com", DEFAULT_PASSWORD, RoleType.ADMIN);
            String adminToken = loginAndGetAccessToken(admin.getEmail(), DEFAULT_PASSWORD);
            activityService.logActivity(owner, "LOGIN", "AUTH", "owner-session-c", "meta");

            Long activityId = activityRepository.findAll().stream().findFirst().orElseThrow().getId();

            mockMvc.perform(get("/api/activities/{id}", activityId)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
            }

            @Test
            void getActivityByIdWithoutTokenReturnsUnauthorized() throws Exception {
            mockMvc.perform(get("/api/activities/{id}", UNKNOWN_ACTIVITY_ID))
                .andExpect(status().isUnauthorized());
            }

            @Test
            void getActivityByIdWithAdminAndUnknownIdReturnsNotFound() throws Exception {
            User admin = createUserWithRole("admin-notfound", "admin-notfound@example.com", DEFAULT_PASSWORD, RoleType.ADMIN);
            String adminToken = loginAndGetAccessToken(admin.getEmail(), DEFAULT_PASSWORD);

            mockMvc.perform(get("/api/activities/{id}", UNKNOWN_ACTIVITY_ID)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
            }

    private void registerUser(String username, String email, String password) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(registerRequest(username, email, password))))
            .andExpect(status().isOk());
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
        return userRepository.save(user);
    }

    private Map<String, String> registerRequest(String username, String email, String password) {
        Map<String, String> request = new HashMap<>();
        request.put("username", username);
        request.put("email", email);
        request.put("password", password);
        return request;
    }

    private Map<String, String> loginRequest(String email, String password) {
        Map<String, String> request = new HashMap<>();
        request.put("email", email);
        request.put("password", password);
        return request;
    }
}
