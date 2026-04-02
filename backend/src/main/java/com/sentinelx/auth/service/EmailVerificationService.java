package com.sentinelx.auth.service;

import com.sentinelx.auth.email.EmailService;
import com.sentinelx.auth.entity.EmailVerificationToken;
import com.sentinelx.auth.exception.InvalidEmailVerificationTokenException;
import com.sentinelx.auth.repository.EmailVerificationTokenRepository;
import com.sentinelx.user.entity.User;
import com.sentinelx.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailVerificationService {

    private static final long EMAIL_VERIFICATION_EXPIRATION_HOURS = 24L;
    private static final String VERIFICATION_LINK_PREFIX = "/api/auth/verify-email?token=";

    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    public EmailVerificationService(
        EmailVerificationTokenRepository emailVerificationTokenRepository,
        UserRepository userRepository,
        EmailService emailService
    ) {
        this.emailVerificationTokenRepository = emailVerificationTokenRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
    }

    @Transactional
    public void sendVerification(User user) {
        emailVerificationTokenRepository.deleteAllByUser(user);

        EmailVerificationToken token = new EmailVerificationToken();
        token.setToken(UUID.randomUUID().toString());
        token.setUser(user);
        token.setExpiryDate(LocalDateTime.now().plus(EMAIL_VERIFICATION_EXPIRATION_HOURS, ChronoUnit.HOURS));
        token.setUsed(false);

        EmailVerificationToken savedToken = emailVerificationTokenRepository.save(token);
        emailService.sendVerificationEmail(user.getEmail(), VERIFICATION_LINK_PREFIX + savedToken.getToken());
    }

    @Transactional
    public void verifyEmail(String token) {
        EmailVerificationToken verificationToken = emailVerificationTokenRepository.findByToken(token)
            .orElseThrow(() -> new InvalidEmailVerificationTokenException("Email verification token does not exist."));

        if (verificationToken.isUsed()) {
            throw new InvalidEmailVerificationTokenException("Email verification token was already used.");
        }

        if (verificationToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new InvalidEmailVerificationTokenException("Email verification token has expired.");
        }

        User user = verificationToken.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);

        verificationToken.setUsed(true);
        emailVerificationTokenRepository.save(verificationToken);
    }
}
