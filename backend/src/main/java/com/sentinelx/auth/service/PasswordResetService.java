package com.sentinelx.auth.service;

import com.sentinelx.auth.email.EmailService;
import com.sentinelx.auth.entity.PasswordResetToken;
import com.sentinelx.auth.exception.InvalidPasswordResetTokenException;
import com.sentinelx.auth.repository.PasswordResetTokenRepository;
import com.sentinelx.user.entity.User;
import com.sentinelx.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetService {

    private static final long RESET_TOKEN_EXPIRATION_MINUTES = 30L;
    private static final String RESET_LINK_PREFIX = "/reset-password?token=";

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    public PasswordResetService(
        UserRepository userRepository,
        PasswordResetTokenRepository passwordResetTokenRepository,
        PasswordEncoder passwordEncoder,
        EmailService emailService
    ) {
        this.userRepository = userRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    @Transactional
    public void initiateReset(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            PasswordResetToken resetToken = new PasswordResetToken();
            resetToken.setToken(UUID.randomUUID().toString());
            resetToken.setUser(user);
            resetToken.setExpiryDate(LocalDateTime.now().plus(RESET_TOKEN_EXPIRATION_MINUTES, ChronoUnit.MINUTES));
            resetToken.setUsed(false);
            PasswordResetToken savedToken = passwordResetTokenRepository.save(resetToken);
            emailService.sendPasswordResetEmail(user.getEmail(), RESET_LINK_PREFIX + savedToken.getToken());
        });
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(token)
            .orElseThrow(() -> new InvalidPasswordResetTokenException("Password reset token does not exist."));

        if (resetToken.isUsed()) {
            throw new InvalidPasswordResetTokenException("Password reset token was already used.");
        }

        if (resetToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new InvalidPasswordResetTokenException("Password reset token has expired.");
        }

        User user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);
    }
}
