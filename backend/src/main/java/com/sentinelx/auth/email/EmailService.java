package com.sentinelx.auth.email;

public interface EmailService {

    void sendPasswordResetEmail(String toEmail, String resetLink);
}
