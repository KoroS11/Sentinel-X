package com.sentinelx.auth.repository;

import com.sentinelx.auth.entity.EmailVerificationToken;
import com.sentinelx.user.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

    Optional<EmailVerificationToken> findByToken(String token);

    void deleteAllByUser(User user);
}
