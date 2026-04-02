package com.sentinelx.auth.refresh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sentinelx.auth.entity.RefreshToken;
import com.sentinelx.auth.exception.InvalidRefreshTokenException;
import com.sentinelx.auth.repository.RefreshTokenRepository;
import com.sentinelx.user.entity.Role;
import com.sentinelx.user.entity.RoleType;
import com.sentinelx.user.entity.User;
import com.sentinelx.user.repository.RoleRepository;
import com.sentinelx.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import(RefreshTokenService.class)
@EnableConfigurationProperties(RefreshTokenProperties.class)
@TestPropertySource(properties = "jwt.refresh-expiration-ms=86400000")
class RefreshTokenServiceTest {

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void createRefreshTokenSavesNonNullTokenWithCorrectUserAndFutureExpiry() {
        User user = createUser("alice", "alice@example.com");

        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

        assertNotNull(refreshToken.getId());
        assertNotNull(refreshToken.getToken());
        assertEquals(user.getId(), refreshToken.getUser().getId());
        assertTrue(refreshToken.getExpiryDate().isAfter(LocalDateTime.now()));
    }

    @Test
    void validateRefreshTokenThrowsIfTokenDoesNotExist() {
        assertThrows(
            InvalidRefreshTokenException.class,
            () -> refreshTokenService.validateRefreshToken("missing-token")
        );
    }

    @Test
    void validateRefreshTokenThrowsIfTokenIsExpired() {
        User user = createUser("bob", "bob@example.com");
        RefreshToken expiredToken = new RefreshToken();
        expiredToken.setToken("expired-token");
        expiredToken.setUser(user);
        expiredToken.setExpiryDate(LocalDateTime.now().minusMinutes(1));
        expiredToken.setRevoked(false);
        refreshTokenRepository.save(expiredToken);

        assertThrows(
            InvalidRefreshTokenException.class,
            () -> refreshTokenService.validateRefreshToken("expired-token")
        );
    }

    @Test
    void validateRefreshTokenThrowsIfTokenIsRevoked() {
        User user = createUser("charlie", "charlie@example.com");
        RefreshToken revokedToken = new RefreshToken();
        revokedToken.setToken("revoked-token");
        revokedToken.setUser(user);
        revokedToken.setExpiryDate(LocalDateTime.now().plusDays(1));
        revokedToken.setRevoked(true);
        refreshTokenRepository.save(revokedToken);

        assertThrows(
            InvalidRefreshTokenException.class,
            () -> refreshTokenService.validateRefreshToken("revoked-token")
        );
    }

    @Test
    void rotateRefreshTokenRevokesOldTokenAndReturnsNewValidOne() {
        User user = createUser("diana", "diana@example.com");
        RefreshToken oldToken = refreshTokenService.createRefreshToken(user);

        RefreshToken newToken = refreshTokenService.rotateRefreshToken(oldToken.getToken());

        RefreshToken reloadedOldToken = refreshTokenRepository.findByToken(oldToken.getToken()).orElseThrow();
        assertTrue(reloadedOldToken.isRevoked());
        assertNotEquals(oldToken.getToken(), newToken.getToken());
        RefreshToken validatedNewToken = refreshTokenService.validateRefreshToken(newToken.getToken());
        assertEquals(newToken.getToken(), validatedNewToken.getToken());
    }

    @Test
    void revokeAllUserTokensMarksAllTokensForThatUserAsRevoked() {
        User user = createUser("eve", "eve@example.com");
        RefreshToken tokenOne = refreshTokenService.createRefreshToken(user);
        RefreshToken tokenTwo = refreshTokenService.createRefreshToken(user);

        refreshTokenService.revokeAllUserTokens(user);

        List<RefreshToken> refreshedTokens = refreshTokenRepository.findAllByUser(user);
        assertEquals(2, refreshedTokens.size());
        assertTrue(refreshedTokens.stream().allMatch(RefreshToken::isRevoked));
        assertNotNull(tokenOne.getToken());
        assertNotNull(tokenTwo.getToken());
    }

    private User createUser(String username, String email) {
        Role role = new Role();
        role.setName(RoleType.EMPLOYEE);
        role = roleRepository.save(role);

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash("hashed-password");
        user.setRole(role);
        user.setActive(true);
        return userRepository.save(user);
    }
}
