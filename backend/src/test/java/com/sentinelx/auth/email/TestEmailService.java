package com.sentinelx.auth.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Primary
@Profile("test")
public class TestEmailService implements EmailService {

    @Override
    public void sendPasswordResetEmail(String toEmail, String resetLink) {
        log.info("[TEST MOCK] Sending password reset email to {} with link: {}", toEmail, resetLink);
    }

    @Override
    public void sendVerificationEmail(String toEmail, String verificationLink) {
        log.info("[TEST MOCK] Sending verification email to {} with link: {}", toEmail, verificationLink);
    }
}
