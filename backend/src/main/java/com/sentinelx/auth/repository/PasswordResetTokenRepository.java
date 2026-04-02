package com.sentinelx.auth.repository;

import com.sentinelx.auth.entity.PasswordResetToken;
import com.sentinelx.user.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByToken(String token);

    void deleteAllByUser(User user);
}
