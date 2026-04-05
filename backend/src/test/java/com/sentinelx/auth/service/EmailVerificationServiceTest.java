package com.sentinelx.auth.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sentinelx.auth.dto.RegisterRequest;
import com.sentinelx.auth.email.EmailService;
import com.sentinelx.auth.entity.EmailVerificationToken;
import com.sentinelx.auth.exception.InvalidEmailVerificationTokenException;
import com.sentinelx.auth.repository.EmailVerificationTokenRepository;
import com.sentinelx.auth.repository.PasswordResetTokenRepository;
import com.sentinelx.auth.repository.RefreshTokenRepository;
import com.sentinelx.user.entity.Role;
import com.sentinelx.user.entity.RoleType;
import com.sentinelx.user.entity.User;
import com.sentinelx.user.repository.RoleRepository;
import com.sentinelx.user.repository.UserRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:emailverificationtest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false",
    "jwt.secret=email_verification_test_secret_which_is_long_enough_12345",
    "jwt.expiration-ms=3600000",
    "jwt.refresh-expiration-ms=604800000"
})
@AutoConfigureMockMvc
class EmailVerificationServiceTest {

    @Autowired
    private EmailVerificationService emailVerificationService;

    @Autowired
    private AuthService authService;

    @Autowired
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EmailService emailService;

    private Role employeeRole;

    @BeforeEach
    void setUp() {
        emailVerificationTokenRepository.deleteAll();
        passwordResetTokenRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        employeeRole = roleRepository.findByName(RoleType.EMPLOYEE).orElseGet(() -> {
            Role role = new Role();
            role.setName(RoleType.EMPLOYEE);
            return roleRepository.save(role);
        });
    }

    @Test
    void sendVerificationSavesTokenAndCallsEmailService() {
        User user = createUser("alice", "alice@example.com");

        emailVerificationService.sendVerification(user);

        EmailVerificationToken token = emailVerificationTokenRepository.findAll().stream().findFirst().orElseThrow();
        assertEquals(user.getId(), token.getUser().getId());
        assertFalse(token.isUsed());
        verify(emailService, times(1)).sendVerificationEmail(org.mockito.ArgumentMatchers.eq(user.getEmail()), org.mockito.ArgumentMatchers.contains(token.getToken()));
    }

    @Test
    void verifyEmailWithValidTokenMarksUserVerifiedAndTokenUsed() {
        User user = createUser("bob", "bob@example.com");
        EmailVerificationToken token = createToken(user, false, LocalDateTime.now().plusHours(1));

        emailVerificationService.verifyEmail(token.getToken());

        User updatedUser = userRepository.findById(user.getId()).orElseThrow();
        assertTrue(updatedUser.isEmailVerified());
        EmailVerificationToken usedToken = emailVerificationTokenRepository.findByToken(token.getToken()).orElseThrow();
        assertTrue(usedToken.isUsed());
    }

    @Test
    void verifyEmailWithExpiredTokenThrows() {
        User user = createUser("charlie", "charlie@example.com");
        EmailVerificationToken token = createToken(user, false, LocalDateTime.now().minusMinutes(1));

        assertThrows(
            InvalidEmailVerificationTokenException.class,
            () -> emailVerificationService.verifyEmail(token.getToken())
        );
    }

    @Test
    void verifyEmailWithAlreadyUsedTokenThrows() {
        User user = createUser("diana", "diana@example.com");
        EmailVerificationToken token = createToken(user, true, LocalDateTime.now().plusHours(1));

        assertThrows(
            InvalidEmailVerificationTokenException.class,
            () -> emailVerificationService.verifyEmail(token.getToken())
        );
    }

    @Test
    void verifyEmailWithNonexistentTokenThrows() {
        assertThrows(
            InvalidEmailVerificationTokenException.class,
            () -> emailVerificationService.verifyEmail("missing-token")
        );
    }

    @Test
    void afterRegistrationUserHasEmailVerifiedFalseByDefault() {
        RegisterRequest request = new RegisterRequest("eve", "eve@example.com", "Password@123");

        assertDoesNotThrow(() -> authService.register(request));

        User savedUser = userRepository.findByEmail("eve@example.com").orElseThrow();
        assertFalse(savedUser.isEmailVerified());
        verify(emailService, times(1)).sendVerificationEmail(org.mockito.ArgumentMatchers.eq("eve@example.com"), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void afterCallingVerifyEndpointUserHasEmailVerifiedTrue() throws Exception {
        RegisterRequest request = new RegisterRequest("frank", "frank@example.com", "Password@123");
        authService.register(request);

        User savedUser = userRepository.findByEmail("frank@example.com").orElseThrow();
        assertFalse(savedUser.isEmailVerified());

        EmailVerificationToken token = emailVerificationTokenRepository.findAll().stream().findFirst().orElseThrow();

        mockMvc.perform(get("/api/auth/verify-email").param("token", token.getToken()))
            .andExpect(status().isOk());

        User verifiedUser = userRepository.findByEmail("frank@example.com").orElseThrow();
        assertTrue(verifiedUser.isEmailVerified());
    }

    private User createUser(String username, String email) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash("hashed-password");
        user.setRole(employeeRole);
        user.setActive(true);
        user.setEmailVerified(false);
        return userRepository.save(user);
    }

    private EmailVerificationToken createToken(User user, boolean used, LocalDateTime expiryDate) {
        EmailVerificationToken token = new EmailVerificationToken();
        token.setToken(java.util.UUID.randomUUID().toString());
        token.setUser(user);
        token.setExpiryDate(expiryDate);
        token.setUsed(used);
        return emailVerificationTokenRepository.save(token);
    }
}
