package com.sentinelx.auth.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("test")
public class TestEmailService implements EmailService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestEmailService.class);

    @Override
    public void sendPasswordResetEmail(String toEmail, String resetLink) {
        LOGGER.debug("[TEST] Password reset email suppressed for {}", toEmail);
    }

    @Override
    public void sendVerificationEmail(String toEmail, String verificationLink) {
        LOGGER.debug("[TEST] Verification email suppressed for {}", toEmail);
    }
}
