package com.sentinelx.auth.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Test implementation of EmailService for the 'test' profile.
 * 
 * Provides a stub implementation that logs operations instead of sending actual emails.
 * This allows tests to run without requiring a real email service.
 */
@Service
@Profile("test")
public class TestEmailService implements EmailService {

    private static final Logger logger = LoggerFactory.getLogger(TestEmailService.class);

    @Override
    public void sendVerificationEmail(String toEmail, String verificationLink) {
        logger.debug("TEST: Sending verification email to {} with link {}", toEmail, verificationLink);
    }

    @Override
    public void sendPasswordResetEmail(String toEmail, String resetLink) {
        logger.debug("TEST: Sending password reset email to {} with link {}", toEmail, resetLink);
    }
}
