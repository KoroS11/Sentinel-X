package com.sentinelx.user.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelx.auth.email.EmailService;
import com.sentinelx.auth.repository.EmailVerificationTokenRepository;
import com.sentinelx.auth.repository.PasswordResetTokenRepository;
import com.sentinelx.auth.repository.RefreshTokenRepository;
import com.sentinelx.user.entity.Role;
import com.sentinelx.user.entity.RoleType;
import com.sentinelx.user.entity.User;
import com.sentinelx.user.entity.UserStatus;
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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserControllerTest {

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
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private EmailService emailService;

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:usercontrollertest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
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
        emailVerificationTokenRepository.deleteAll();
        passwordResetTokenRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void getUsersWithoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/users"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void getUsersWithEmployeeTokenReturnsForbidden() throws Exception {
        User employee = createUserWithRole("employee", "employee@example.com", RoleType.EMPLOYEE);
        String employeeToken = loginAndGetAccessToken(employee.getEmail(), DEFAULT_PASSWORD);

        mockMvc.perform(get("/api/users")
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isForbidden());
    }

            @Test
            void getUsersWithAdminTokenReturnsOk() throws Exception {
            User admin = createUserWithRole("admin-list", "admin-list@example.com", RoleType.ADMIN);
            createUserWithRole("employee-list", "employee-list@example.com", RoleType.EMPLOYEE);
            String adminToken = loginAndGetAccessToken(admin.getEmail(), DEFAULT_PASSWORD);

            mockMvc.perform(get("/api/users")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
            }

    @Test
    void getUserByIdWithOwnUserTokenReturnsOk() throws Exception {
        User employee = createUserWithRole("self-user", "self@example.com", RoleType.EMPLOYEE);
        String employeeToken = loginAndGetAccessToken(employee.getEmail(), DEFAULT_PASSWORD);

        mockMvc.perform(get("/api/users/{id}", employee.getId())
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(employee.getId()));
    }

    @Test
    void getUserByIdWithDifferentNonAdminUserTokenReturnsForbidden() throws Exception {
        User requester = createUserWithRole("requester", "requester@example.com", RoleType.EMPLOYEE);
        User target = createUserWithRole("target", "target@example.com", RoleType.EMPLOYEE);
        String requesterToken = loginAndGetAccessToken(requester.getEmail(), DEFAULT_PASSWORD);

        mockMvc.perform(get("/api/users/{id}", target.getId())
                .header("Authorization", "Bearer " + requesterToken))
            .andExpect(status().isForbidden());
    }

            @Test
            void getUserByIdWithAdminTokenAndUnknownIdReturnsNotFound() throws Exception {
            User admin = createUserWithRole("admin-unknown", "admin-unknown@example.com", RoleType.ADMIN);
            String adminToken = loginAndGetAccessToken(admin.getEmail(), DEFAULT_PASSWORD);

            mockMvc.perform(get("/api/users/{id}", UNKNOWN_USER_ID)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
            }

    @Test
    void postUsersWithAdminTokenReturnsCreated() throws Exception {
        User admin = createUserWithRole("admin", "admin@example.com", RoleType.ADMIN);
        String adminToken = loginAndGetAccessToken(admin.getEmail(), DEFAULT_PASSWORD);

        mockMvc.perform(post("/api/users")
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(createUserRequest())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.email").value("new-user@example.com"));
    }

    @Test
    void deleteUserWithAdminTokenReturnsNoContent() throws Exception {
        User admin = createUserWithRole("admin-del", "admin-del@example.com", RoleType.ADMIN);
        User employee = createUserWithRole("employee-del", "employee-del@example.com", RoleType.EMPLOYEE);
        String adminToken = loginAndGetAccessToken(admin.getEmail(), DEFAULT_PASSWORD);

        mockMvc.perform(delete("/api/users/{id}", employee.getId())
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isNoContent());
    }

            @Test
            void deleteUserWithAdminTokenAndUnknownIdReturnsNotFound() throws Exception {
            User admin = createUserWithRole("admin-del-unknown", "admin-del-unknown@example.com", RoleType.ADMIN);
            String adminToken = loginAndGetAccessToken(admin.getEmail(), DEFAULT_PASSWORD);

            mockMvc.perform(delete("/api/users/{id}", UNKNOWN_USER_ID)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
            }

    @Test
    void patchUserStatusWithAdminTokenReturnsOk() throws Exception {
        User admin = createUserWithRole("admin-status", "admin-status@example.com", RoleType.ADMIN);
        User employee = createUserWithRole("employee-status", "employee-status@example.com", RoleType.EMPLOYEE);
        String adminToken = loginAndGetAccessToken(admin.getEmail(), DEFAULT_PASSWORD);

        Map<String, String> request = new HashMap<>();
        request.put("status", UserStatus.SUSPENDED.name());

        mockMvc.perform(patch("/api/users/{id}/status", employee.getId())
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(UserStatus.SUSPENDED.name()));
    }

            @Test
            void patchUserStatusWithInvalidBodyReturnsBadRequest() throws Exception {
            User admin = createUserWithRole("admin-status-bad", "admin-status-bad@example.com", RoleType.ADMIN);
            User employee = createUserWithRole("employee-status-bad", "employee-status-bad@example.com", RoleType.EMPLOYEE);
            String adminToken = loginAndGetAccessToken(admin.getEmail(), DEFAULT_PASSWORD);

            mockMvc.perform(patch("/api/users/{id}/status", employee.getId())
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content("{}"))
                .andExpect(status().isBadRequest());
            }

            @Test
            void putUserWithoutTokenReturnsUnauthorized() throws Exception {
            User employee = createUserWithRole("employee-put-unauth", "employee-put-unauth@example.com", RoleType.EMPLOYEE);

            mockMvc.perform(put("/api/users/{id}", employee.getId())
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(updateUserRequest("new-name", "new@example.com"))))
                .andExpect(status().isUnauthorized());
            }

            @Test
            void putOwnUserWithEmployeeTokenReturnsOk() throws Exception {
            User employee = createUserWithRole("employee-put", "employee-put@example.com", RoleType.EMPLOYEE);
            String employeeToken = loginAndGetAccessToken(employee.getEmail(), DEFAULT_PASSWORD);

            mockMvc.perform(put("/api/users/{id}", employee.getId())
                .header("Authorization", "Bearer " + employeeToken)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(updateUserRequest("employee-updated", "employee-updated@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("employee-updated"));
            }

            @Test
            void putOtherUserWithEmployeeTokenReturnsForbidden() throws Exception {
            User requester = createUserWithRole("employee-put-req", "employee-put-req@example.com", RoleType.EMPLOYEE);
            User target = createUserWithRole("employee-put-target", "employee-put-target@example.com", RoleType.EMPLOYEE);
            String requesterToken = loginAndGetAccessToken(requester.getEmail(), DEFAULT_PASSWORD);

            mockMvc.perform(put("/api/users/{id}", target.getId())
                .header("Authorization", "Bearer " + requesterToken)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(updateUserRequest("blocked", "blocked@example.com"))))
                .andExpect(status().isForbidden());
            }

            @Test
            void putUserWithInvalidEmailReturnsBadRequest() throws Exception {
            User employee = createUserWithRole("employee-put-bad", "employee-put-bad@example.com", RoleType.EMPLOYEE);
            String employeeToken = loginAndGetAccessToken(employee.getEmail(), DEFAULT_PASSWORD);

            mockMvc.perform(put("/api/users/{id}", employee.getId())
                .header("Authorization", "Bearer " + employeeToken)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(updateUserRequest("name", "invalid-email"))))
                .andExpect(status().isBadRequest());
            }

            @Test
            void putUnknownUserWithAdminTokenReturnsNotFound() throws Exception {
            User admin = createUserWithRole("admin-put-unknown", "admin-put-unknown@example.com", RoleType.ADMIN);
            String adminToken = loginAndGetAccessToken(admin.getEmail(), DEFAULT_PASSWORD);

            mockMvc.perform(put("/api/users/{id}", UNKNOWN_USER_ID)
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(updateUserRequest("name", "name@example.com"))))
                .andExpect(status().isNotFound());
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
        user.setPasswordHash(passwordEncoder.encode(DEFAULT_PASSWORD));
        user.setRole(role);
        user.setActive(true);
        user.setEmailVerified(true);
        user.setStatus(UserStatus.ACTIVE);
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

    private Map<String, String> createUserRequest() {
        Map<String, String> request = new HashMap<>();
        request.put("username", "new-user");
        request.put("email", "new-user@example.com");
        request.put("password", "NewPassword@123");
        request.put("role", RoleType.EMPLOYEE.name());
        return request;
    }

    private Map<String, String> updateUserRequest(String username, String email) {
        Map<String, String> request = new HashMap<>();
        request.put("username", username);
        request.put("email", email);
        return request;
    }
}