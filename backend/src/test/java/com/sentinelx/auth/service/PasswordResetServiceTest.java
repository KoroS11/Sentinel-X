package com.sentinelx.auth.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.sentinelx.auth.email.EmailService;
import com.sentinelx.auth.entity.PasswordResetToken;
import com.sentinelx.auth.exception.InvalidPasswordResetTokenException;
import com.sentinelx.auth.repository.PasswordResetTokenRepository;
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
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:passwordresettest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false",
    "jwt.secret=password_reset_test_secret_which_is_long_enough_12345",
    "jwt.expiration-ms=3600000",
    "jwt.refresh-expiration-ms=604800000"
})
@TestPropertySource(properties = "spring.profiles.active=dev")
class PasswordResetServiceTest {

    @Autowired
    private PasswordResetService passwordResetService;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthenticationManager authenticationManager;

    @MockBean
    private EmailService emailService;

    private Role employeeRole;

    @BeforeEach
    void setUp() {
        passwordResetTokenRepository.deleteAll();
        userRepository.deleteAll();

        employeeRole = roleRepository.findByName(RoleType.EMPLOYEE).orElseGet(() -> {
            Role role = new Role();
            role.setName(RoleType.EMPLOYEE);
            return roleRepository.save(role);
        });
    }

    @Test
    void initiateResetWithValidEmailSavesTokenAndCallsEmailService() {
        User user = createUser("alice", "alice@example.com", "OldPassword123");

        passwordResetService.initiateReset(user.getEmail());

        PasswordResetToken token = passwordResetTokenRepository.findAll().stream().findFirst().orElseThrow();
        assertEquals(user.getId(), token.getUser().getId());
        assertFalse(token.isUsed());
        verify(emailService, times(1)).sendPasswordResetEmail(org.mockito.ArgumentMatchers.eq(user.getEmail()), org.mockito.ArgumentMatchers.contains(token.getToken()));
    }

    @Test
    void initiateResetWithUnknownEmailCompletesSilentlyWithoutThrowing() {
        assertDoesNotThrow(() -> passwordResetService.initiateReset("unknown@example.com"));
        verify(emailService, never()).sendPasswordResetEmail(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        assertTrue(passwordResetTokenRepository.findAll().isEmpty());
    }

    @Test
    void resetPasswordWithValidTokenUpdatesPasswordAndMarksTokenUsed() {
        User user = createUser("bob", "bob@example.com", "OldPassword123");
        PasswordResetToken token = createToken(user, false, LocalDateTime.now().plusMinutes(10));

        passwordResetService.resetPassword(token.getToken(), "NewPassword123");

        User updatedUser = userRepository.findById(user.getId()).orElseThrow();
        assertTrue(passwordEncoder.matches("NewPassword123", updatedUser.getPasswordHash()));
        PasswordResetToken usedToken = passwordResetTokenRepository.findByToken(token.getToken()).orElseThrow();
        assertTrue(usedToken.isUsed());
    }

    @Test
    void resetPasswordWithExpiredTokenThrows() {
        User user = createUser("charlie", "charlie@example.com", "OldPassword123");
        PasswordResetToken token = createToken(user, false, LocalDateTime.now().minusMinutes(1));

        assertThrows(
            InvalidPasswordResetTokenException.class,
            () -> passwordResetService.resetPassword(token.getToken(), "NewPassword123")
        );
    }

    @Test
    void resetPasswordWithAlreadyUsedTokenThrows() {
        User user = createUser("diana", "diana@example.com", "OldPassword123");
        PasswordResetToken token = createToken(user, true, LocalDateTime.now().plusMinutes(10));

        assertThrows(
            InvalidPasswordResetTokenException.class,
            () -> passwordResetService.resetPassword(token.getToken(), "NewPassword123")
        );
    }

    @Test
    void resetPasswordWithNonexistentTokenThrows() {
        assertThrows(
            InvalidPasswordResetTokenException.class,
            () -> passwordResetService.resetPassword("missing-token", "NewPassword123")
        );
    }

    @Test
    void afterSuccessfulResetOldPasswordNoLongerAuthenticates() {
        User user = createUser("eve", "eve@example.com", "OldPassword123");
        PasswordResetToken token = createToken(user, false, LocalDateTime.now().plusMinutes(10));

        passwordResetService.resetPassword(token.getToken(), "BrandNewPassword123");

        assertThrows(
            BadCredentialsException.class,
            () -> authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(user.getEmail(), "OldPassword123")
            )
        );

        assertDoesNotThrow(() -> authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(user.getEmail(), "BrandNewPassword123")
        ));
    }

    private User createUser(String username, String email, String rawPassword) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(employeeRole);
        user.setActive(true);
        return userRepository.save(user);
    }

    private PasswordResetToken createToken(User user, boolean used, LocalDateTime expiryDate) {
        PasswordResetToken token = new PasswordResetToken();
        token.setToken(java.util.UUID.randomUUID().toString());
        token.setUser(user);
        token.setExpiryDate(expiryDate);
        token.setUsed(used);
        return passwordResetTokenRepository.save(token);
    }
}
