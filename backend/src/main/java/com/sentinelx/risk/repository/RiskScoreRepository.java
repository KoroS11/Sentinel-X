package com.sentinelx.risk.repository;

import com.sentinelx.risk.entity.RiskScore;
import com.sentinelx.user.entity.User;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RiskScoreRepository extends JpaRepository<RiskScore, Long> {
    Optional<RiskScore> findTopByUserOrderByCalculatedAtDesc(User user);

    Page<RiskScore> findAllByUser(User user, Pageable pageable);
}
