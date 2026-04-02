package com.sentinelx.auth.refresh;

import com.sentinelx.auth.entity.RefreshToken;
import com.sentinelx.auth.exception.InvalidRefreshTokenException;
import com.sentinelx.auth.repository.RefreshTokenRepository;
import com.sentinelx.user.entity.User;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenProperties refreshTokenProperties;

    public RefreshTokenService(
        RefreshTokenRepository refreshTokenRepository,
        RefreshTokenProperties refreshTokenProperties
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenProperties = refreshTokenProperties;
    }

    @Transactional
    public RefreshToken createRefreshToken(User user) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setUser(user);
        refreshToken.setExpiryDate(
            LocalDateTime.now().plus(refreshTokenProperties.getRefreshExpirationMs(), ChronoUnit.MILLIS)
        );
        refreshToken.setRevoked(false);
        return refreshTokenRepository.save(refreshToken);
    }

    @Transactional(readOnly = true)
    public RefreshToken validateRefreshToken(String token) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(token)
            .orElseThrow(() -> new InvalidRefreshTokenException("Refresh token does not exist."));

        if (refreshToken.isRevoked()) {
            throw new InvalidRefreshTokenException("Refresh token is revoked.");
        }

        if (refreshToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new InvalidRefreshTokenException("Refresh token has expired.");
        }

        return refreshToken;
    }

    @Transactional
    public RefreshToken rotateRefreshToken(String oldToken) {
        RefreshToken oldRefreshToken = validateRefreshToken(oldToken);
        oldRefreshToken.setRevoked(true);
        refreshTokenRepository.save(oldRefreshToken);
        return createRefreshToken(oldRefreshToken.getUser());
    }

    @Transactional
    public void revokeAllUserTokens(User user) {
        List<RefreshToken> refreshTokens = refreshTokenRepository.findAllByUser(user);
        for (RefreshToken refreshToken : refreshTokens) {
            refreshToken.setRevoked(true);
        }
        refreshTokenRepository.saveAll(refreshTokens);
    }
}
