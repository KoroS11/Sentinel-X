package com.sentinelx.auth.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("dev")
public class DevEmailService implements EmailService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DevEmailService.class);

    @Override
    public void sendPasswordResetEmail(String toEmail, String resetLink) {
        LOGGER.info("Password reset email queued for {} with link {}", toEmail, resetLink);
    }
}
