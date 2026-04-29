package com.sentinelx.e2e;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelx.auth.entity.EmailVerificationToken;
import com.sentinelx.auth.entity.PasswordResetToken;
import com.sentinelx.auth.email.EmailService;
import com.sentinelx.auth.repository.EmailVerificationTokenRepository;
import com.sentinelx.auth.repository.PasswordResetTokenRepository;
import com.sentinelx.auth.repository.RefreshTokenRepository;
import com.sentinelx.user.entity.User;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.transaction.annotation.Transactional
class E2EAuthFlowTest {

    private static final String DB_URL = "jdbc:h2:mem:e2eauthflowtest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
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
    private static final String PROTECTED_ENDPOINT = "/api/dashboard/me";

    private static final String AUTH_REGISTER = "/api/auth/register";
    private static final String AUTH_LOGIN = "/api/auth/login";
    private static final String AUTH_REFRESH = "/api/auth/refresh";
    private static final String AUTH_LOGOUT = "/api/auth/logout";
    private static final String AUTH_FORGOT_PASSWORD = "/api/auth/forgot-password";
    private static final String AUTH_RESET_PASSWORD = "/api/auth/reset-password";
    private static final String AUTH_VERIFY_EMAIL = "/api/auth/verify-email";

    private static final String TOKEN_FIELD = "token";
    private static final String REFRESH_TOKEN_FIELD = "refreshToken";

    private static final String JWT_SECRET =
        UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

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

    @Autowired
    private com.sentinelx.user.service.RoleService roleService;

    @BeforeEach
    void setUp() {
        roleService.ensureDefaultRoles();
        emailVerificationTokenRepository.deleteAll();
        passwordResetTokenRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void endToEndAuthFlows() throws Exception {
        String username = uniqueValue("authflow-user");
        String email = uniqueValue("authflow") + "@example.com";
        String password = generatedPassword();

        String registerResponse = mockMvc.perform(post(AUTH_REGISTER)
                .contentType(CONTENT_TYPE_JSON)
                .content(objectMapper.writeValueAsString(registerRequest(username, email, password))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        assertFalse(readField(registerResponse, TOKEN_FIELD).isBlank());

        String loginResponse = mockMvc.perform(post(AUTH_LOGIN)
                .contentType(CONTENT_TYPE_JSON)
                .content(objectMapper.writeValueAsString(loginRequest(email, password))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        String accessToken = readField(loginResponse, TOKEN_FIELD);
        String refreshToken = readField(loginResponse, REFRESH_TOKEN_FIELD);

        mockMvc.perform(get(PROTECTED_ENDPOINT)
                .header(AUTH_HEADER, BEARER_PREFIX + accessToken))
            .andExpect(status().isOk());

        mockMvc.perform(get(PROTECTED_ENDPOINT)
                .header(AUTH_HEADER, BEARER_PREFIX + "invalid-access-token"))
            .andExpect(status().isUnauthorized());

        String refreshResponse = mockMvc.perform(post(AUTH_REFRESH)
                .contentType(CONTENT_TYPE_JSON)
                .content(objectMapper.writeValueAsString(refreshRequest(refreshToken))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        String rotatedRefreshToken = readField(refreshResponse, REFRESH_TOKEN_FIELD);
        assertFalse(rotatedRefreshToken.isBlank());

        mockMvc.perform(post(AUTH_REFRESH)
                .contentType(CONTENT_TYPE_JSON)
                .content(objectMapper.writeValueAsString(refreshRequest(refreshToken))))
            .andExpect(status().isUnauthorized());

        String logoutLoginResponse = mockMvc.perform(post(AUTH_LOGIN)
                .contentType(CONTENT_TYPE_JSON)
                .content(objectMapper.writeValueAsString(loginRequest(email, password))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        String logoutAccessToken = readField(logoutLoginResponse, TOKEN_FIELD);
        String logoutRefreshToken = readField(logoutLoginResponse, REFRESH_TOKEN_FIELD);

        mockMvc.perform(post(AUTH_LOGOUT)
                .header(AUTH_HEADER, BEARER_PREFIX + logoutAccessToken))
            .andExpect(status().isOk());

        mockMvc.perform(post(AUTH_REFRESH)
                .contentType(CONTENT_TYPE_JSON)
                .content(objectMapper.writeValueAsString(refreshRequest(logoutRefreshToken))))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(post(AUTH_FORGOT_PASSWORD)
                .contentType(CONTENT_TYPE_JSON)
                .content(objectMapper.writeValueAsString(forgotPasswordRequest(email))))
            .andExpect(status().isOk());

        PasswordResetToken passwordResetToken = latestPasswordResetTokenFor(email);
        String newPassword = generatedPassword();

        mockMvc.perform(post(AUTH_RESET_PASSWORD)
                .contentType(CONTENT_TYPE_JSON)
                .content(objectMapper.writeValueAsString(resetPasswordRequest(passwordResetToken.getToken(), newPassword))))
            .andExpect(status().isOk());

        mockMvc.perform(post(AUTH_LOGIN)
                .contentType(CONTENT_TYPE_JSON)
                .content(objectMapper.writeValueAsString(loginRequest(email, newPassword))))
            .andExpect(status().isOk());

        mockMvc.perform(post(AUTH_LOGIN)
                .contentType(CONTENT_TYPE_JSON)
                .content(objectMapper.writeValueAsString(loginRequest(email, password))))
            .andExpect(status().isUnauthorized());

        String verifyUsername = uniqueValue("verify-user");
        String verifyEmail = uniqueValue("verify") + "@example.com";
        String verifyPassword = generatedPassword();

        mockMvc.perform(post(AUTH_REGISTER)
                .contentType(CONTENT_TYPE_JSON)
                .content(objectMapper.writeValueAsString(registerRequest(verifyUsername, verifyEmail, verifyPassword))))
            .andExpect(status().isOk());

        User unverifiedUser = userRepository.findByEmail(verifyEmail).orElseThrow();
        assertFalse(unverifiedUser.isEmailVerified());

        EmailVerificationToken verificationToken = latestEmailVerificationTokenFor(verifyEmail);

        mockMvc.perform(get(AUTH_VERIFY_EMAIL)
                .param("token", verificationToken.getToken()))
            .andExpect(status().isOk());

        User verifiedUser = userRepository.findByEmail(verifyEmail).orElseThrow();
        assertTrue(verifiedUser.isEmailVerified());
    }

    private PasswordResetToken latestPasswordResetTokenFor(String email) {
        User user = userRepository.findByEmail(email).orElseThrow();
        return passwordResetTokenRepository.findAll().stream()
            .filter(token -> token.getUser().getId().equals(user.getId()))
            .reduce((first, second) -> second)
            .orElseThrow();
    }

    private EmailVerificationToken latestEmailVerificationTokenFor(String email) {
        User user = userRepository.findByEmail(email).orElseThrow();
        return emailVerificationTokenRepository.findAll().stream()
            .filter(token -> token.getUser().getId().equals(user.getId()))
            .reduce((first, second) -> second)
            .orElseThrow();
    }

    private String readField(String json, String field) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        return root.path(field).asText();
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

    private Map<String, String> refreshRequest(String refreshToken) {
        Map<String, String> request = new HashMap<>();
        request.put(REFRESH_TOKEN_FIELD, refreshToken);
        return request;
    }

    private Map<String, String> forgotPasswordRequest(String email) {
        Map<String, String> request = new HashMap<>();
        request.put("email", email);
        return request;
    }

    private Map<String, String> resetPasswordRequest(String token, String newPassword) {
        Map<String, String> request = new HashMap<>();
        request.put("token", token);
        request.put("newPassword", newPassword);
        return request;
    }

    private String uniqueValue(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String generatedPassword() {
        return "Pw@" + UUID.randomUUID().toString().replace("-", "") + "1";
    }
}
