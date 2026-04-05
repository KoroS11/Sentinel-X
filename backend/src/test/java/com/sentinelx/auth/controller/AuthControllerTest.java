package com.sentinelx.auth.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelx.auth.entity.RefreshToken;
import com.sentinelx.auth.repository.RefreshTokenRepository;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    private static final String TEST_SECRET =
        UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:authcontrollertest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("jwt.secret", () -> TEST_SECRET);
        registry.add("jwt.expiration-ms", () -> "3600000");
        registry.add("jwt.refresh-expiration-ms", () -> "604800000");
    }

    @Test
    void registerWithValidBodyReturnsToken() throws Exception {
        Map<String, String> request = registerRequest("alice", "alice@example.com", "Password@123");

        mockMvc.perform(post("/api/auth/register")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").isNotEmpty())
            .andExpect(jsonPath("$.username").value("alice"));
    }

    @Test
    void registerWithDuplicateEmailReturnsConflict() throws Exception {
        Map<String, String> first = registerRequest("user1", "dup@example.com", "Password@123");
        Map<String, String> duplicate = registerRequest("user2", "dup@example.com", "Password@123");

        mockMvc.perform(post("/api/auth/register")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(first)))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/register")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(duplicate)))
            .andExpect(status().isConflict());
    }

    @Test
    void loginWithCorrectCredentialsReturnsToken() throws Exception {
        Map<String, String> register = registerRequest("bob", "bob@example.com", "Password@123");
        Map<String, String> login = loginRequest("bob@example.com", "Password@123");

        mockMvc.perform(post("/api/auth/register")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(register)))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(login)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").isNotEmpty())
            .andExpect(jsonPath("$.refreshToken").isNotEmpty())
            .andExpect(jsonPath("$.username").value("bob"));
    }

    @Test
    void refreshWithValidTokenReturnsNewAccessAndRefreshTokens() throws Exception {
        Map<String, String> register = registerRequest("refreshUser", "refresh@example.com", "Password@123");
        Map<String, String> login = loginRequest("refresh@example.com", "Password@123");

        mockMvc.perform(post("/api/auth/register")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(register)))
            .andExpect(status().isOk());

        String oldRefreshToken = extractFieldFromResponse(
            mockMvc.perform(post("/api/auth/login")
                    .contentType("application/json")
                    .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "refreshToken"
        );

        mockMvc.perform(post("/api/auth/refresh")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(refreshRequest(oldRefreshToken))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").isNotEmpty())
            .andExpect(jsonPath("$.refreshToken").isNotEmpty());
    }

    @Test
    void refreshWithExpiredTokenReturnsUnauthorized() throws Exception {
        String refreshTokenValue = issueRefreshTokenFor("expiredUser", "expired@example.com");
        RefreshToken refreshToken = refreshTokenRepository.findByToken(refreshTokenValue).orElseThrow();
        refreshToken.setExpiryDate(LocalDateTime.now().minusMinutes(1));
        refreshTokenRepository.save(refreshToken);

        mockMvc.perform(post("/api/auth/refresh")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(refreshRequest(refreshTokenValue))))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshWithRevokedTokenReturnsUnauthorized() throws Exception {
        String refreshTokenValue = issueRefreshTokenFor("revokedUser", "revoked@example.com");
        RefreshToken refreshToken = refreshTokenRepository.findByToken(refreshTokenValue).orElseThrow();
        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);

        mockMvc.perform(post("/api/auth/refresh")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(refreshRequest(refreshTokenValue))))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshWithNonexistentTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(refreshRequest("missing-token"))))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesAllTokensAndSubsequentRefreshReturnsUnauthorized() throws Exception {
        Map<String, String> register = registerRequest("logoutUser", "logout@example.com", "Password@123");
        Map<String, String> login = loginRequest("logout@example.com", "Password@123");

        mockMvc.perform(post("/api/auth/register")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(register)))
            .andExpect(status().isOk());

        String loginResponse = mockMvc.perform(post("/api/auth/login")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(login)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        String accessToken = extractFieldFromResponse(loginResponse, "token");
        String refreshToken = extractFieldFromResponse(loginResponse, "refreshToken");

        mockMvc.perform(post("/api/auth/logout")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/refresh")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(refreshRequest(refreshToken))))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void loginWithWrongPasswordReturnsUnauthorized() throws Exception {
        Map<String, String> register = registerRequest("charlie", "charlie@example.com", "Password@123");
        Map<String, String> login = loginRequest("charlie@example.com", "WrongPassword");

        mockMvc.perform(post("/api/auth/register")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(register)))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(login)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void registerWithMissingFieldsReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType("application/json")
                .content("{}"))
            .andExpect(status().isBadRequest());
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
        request.put("refreshToken", refreshToken);
        return request;
    }

    private String issueRefreshTokenFor(String username, String email) throws Exception {
        Map<String, String> register = registerRequest(username, email, "Password@123");
        Map<String, String> login = loginRequest(email, "Password@123");

        mockMvc.perform(post("/api/auth/register")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(register)))
            .andExpect(status().isOk());

        String loginResponse = mockMvc.perform(post("/api/auth/login")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(login)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        return extractFieldFromResponse(loginResponse, "refreshToken");
    }

    private String extractFieldFromResponse(String content, String fieldName) throws Exception {
        return objectMapper.readTree(content).path(fieldName).asText();
    }
}
