package com.sentinelx.risk.repository;

import com.sentinelx.risk.entity.RiskScore;
import com.sentinelx.user.entity.User;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RiskScoreRepository extends JpaRepository<RiskScore, Long> {
    Optional<RiskScore> findTopByUserOrderByCalculatedAtDesc(User user);

    Page<RiskScore> findAllByUser(User user, Pageable pageable);

    @Query("""
        select avg(rs.score)
        from RiskScore rs
        where rs.calculatedAt = (
            select max(innerRs.calculatedAt)
            from RiskScore innerRs
            where innerRs.user = rs.user
        )
        """)
    Double findAverageLatestRiskScore();

    @Query("""
        select count(distinct rs.user.id)
        from RiskScore rs
        where rs.score >= :threshold
          and rs.calculatedAt = (
              select max(innerRs.calculatedAt)
              from RiskScore innerRs
              where innerRs.user = rs.user
          )
        """)
    long countUsersWithLatestScoreAtLeast(@Param("threshold") int threshold);

    @Query(nativeQuery = true, value = """
        select
            CAST(EXTRACT(YEAR FROM calculated_at) AS INT) as year_val,
            CAST(EXTRACT(WEEK FROM calculated_at) AS INT) as week_val,
            avg(score) as avg_score,
            CAST(sum(case when score >= :highRiskThreshold then 1 else 0 end) AS BIGINT) as high_risk_count
        from risk_scores
        where calculated_at >= :startDateTime
        group by EXTRACT(YEAR FROM calculated_at), EXTRACT(WEEK FROM calculated_at)
        order by EXTRACT(YEAR FROM calculated_at), EXTRACT(WEEK FROM calculated_at)
        """)
    List<Object[]> findWeeklyRiskTrendForPeriod(
        @Param("startDateTime") LocalDateTime startDateTime,
        @Param("highRiskThreshold") int highRiskThreshold
    );
}
