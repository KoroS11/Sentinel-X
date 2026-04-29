package com.sentinelx.risk.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelx.activity.repository.ActivityRepository;
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
@org.springframework.transaction.annotation.Transactional
class RiskControllerTest {

    private static final String TEST_SECRET =
        UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
    private static final String DEFAULT_PASSWORD = "Password@123";
    private static final long UNKNOWN_USER_ID = 9999L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private RiskScoreRepository riskScoreRepository;

    @Autowired
    private ActivityRepository activityRepository;

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
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:riskcontrollertest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
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
        riskScoreRepository.deleteAll();
        activityRepository.deleteAll();
        emailVerificationTokenRepository.deleteAll();
        passwordResetTokenRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void getRiskMeWithoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/risk/me"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void getRiskUserWithNonAdminReturnsForbidden() throws Exception {
        registerUser("employee", "employee@example.com", DEFAULT_PASSWORD);
        registerUser("target", "target@example.com", DEFAULT_PASSWORD);

        String employeeToken = loginAndGetAccessToken("employee@example.com", DEFAULT_PASSWORD);
        Long targetId = userRepository.findByEmail("target@example.com").orElseThrow().getId();

        mockMvc.perform(get("/api/risk/user/{userId}", targetId)
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void getRiskUserWithAdminReturnsOk() throws Exception {
        User admin = createUserWithRole("admin", "admin@example.com", DEFAULT_PASSWORD, RoleType.ADMIN);
        User target = createUserWithRole("target", "target@example.com", DEFAULT_PASSWORD, RoleType.EMPLOYEE);

        String adminToken = loginAndGetAccessToken(admin.getEmail(), DEFAULT_PASSWORD);

        mockMvc.perform(get("/api/risk/user/{userId}", target.getId())
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());
    }

    @Test
    void getRiskByUserIdWithOwnEmployeeTokenReturnsOk() throws Exception {
        User employee = createUserWithRole("employee-own", "employee-own@example.com", DEFAULT_PASSWORD, RoleType.EMPLOYEE);
        String employeeToken = loginAndGetAccessToken(employee.getEmail(), DEFAULT_PASSWORD);

        mockMvc.perform(get("/api/risk/{userId}", employee.getId())
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isOk());
    }

    @Test
    void getRiskByUserIdWithDifferentEmployeeTokenReturnsForbidden() throws Exception {
        User requester = createUserWithRole("employee-r", "employee-r@example.com", DEFAULT_PASSWORD, RoleType.EMPLOYEE);
        User target = createUserWithRole("employee-t", "employee-t@example.com", DEFAULT_PASSWORD, RoleType.EMPLOYEE);
        String requesterToken = loginAndGetAccessToken(requester.getEmail(), DEFAULT_PASSWORD);

        mockMvc.perform(get("/api/risk/{userId}", target.getId())
                .header("Authorization", "Bearer " + requesterToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void getRiskByUserIdWithAnalystTokenReturnsOk() throws Exception {
        User analyst = createUserWithRole("analyst", "analyst@example.com", DEFAULT_PASSWORD, RoleType.ANALYST);
        User target = createUserWithRole("employee-z", "employee-z@example.com", DEFAULT_PASSWORD, RoleType.EMPLOYEE);
        String analystToken = loginAndGetAccessToken(analyst.getEmail(), DEFAULT_PASSWORD);

        mockMvc.perform(get("/api/risk/{userId}", target.getId())
                .header("Authorization", "Bearer " + analystToken))
            .andExpect(status().isOk());
    }

    @Test
    void getRiskByUserIdWithoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/risk/{userId}", 1L))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void getRiskByUserIdWithInvalidInputReturnsBadRequest() throws Exception {
        User admin = createUserWithRole("admin-bad", "admin-bad@example.com", DEFAULT_PASSWORD, RoleType.ADMIN);
        String adminToken = loginAndGetAccessToken(admin.getEmail(), DEFAULT_PASSWORD);

        mockMvc.perform(get("/api/risk/{userId}", "invalid")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isBadRequest());
    }

    @Test
    void getRiskByUserIdWithUnknownUserReturnsNotFound() throws Exception {
        User admin = createUserWithRole("admin-nf", "admin-nf@example.com", DEFAULT_PASSWORD, RoleType.ADMIN);
        String adminToken = loginAndGetAccessToken(admin.getEmail(), DEFAULT_PASSWORD);

        mockMvc.perform(get("/api/risk/{userId}", UNKNOWN_USER_ID)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isNotFound());
    }

    @Test
    void getRiskHistoryByUserIdWithAdminTokenReturnsOkPaginated() throws Exception {
        User admin = createUserWithRole("admin-h", "admin-h@example.com", DEFAULT_PASSWORD, RoleType.ADMIN);
        User target = createUserWithRole("employee-h", "employee-h@example.com", DEFAULT_PASSWORD, RoleType.EMPLOYEE);
        String adminToken = loginAndGetAccessToken(admin.getEmail(), DEFAULT_PASSWORD);

        mockMvc.perform(get("/api/risk/{userId}/history", target.getId())
                .param("page", "0")
                .param("size", "10")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());
    }

    @Test
    void getRiskHistoryByUserIdWithoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/risk/{userId}/history", 1L))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void getRiskHistoryByUserIdWithDifferentEmployeeReturnsForbidden() throws Exception {
        User requester = createUserWithRole("employee-h-req", "employee-h-req@example.com", DEFAULT_PASSWORD, RoleType.EMPLOYEE);
        User target = createUserWithRole("employee-h-target", "employee-h-target@example.com", DEFAULT_PASSWORD, RoleType.EMPLOYEE);
        String requesterToken = loginAndGetAccessToken(requester.getEmail(), DEFAULT_PASSWORD);

        mockMvc.perform(get("/api/risk/{userId}/history", target.getId())
                .param("page", "0")
                .param("size", "10")
                .header("Authorization", "Bearer " + requesterToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void getRiskHistoryByUserIdWithUnknownUserReturnsNotFound() throws Exception {
        User admin = createUserWithRole("admin-h-nf", "admin-h-nf@example.com", DEFAULT_PASSWORD, RoleType.ADMIN);
        String adminToken = loginAndGetAccessToken(admin.getEmail(), DEFAULT_PASSWORD);

        mockMvc.perform(get("/api/risk/{userId}/history", UNKNOWN_USER_ID)
                .param("page", "0")
                .param("size", "10")
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
            return roleRepository.saveAndFlush(newRole);
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
