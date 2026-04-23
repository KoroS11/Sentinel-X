package com.sentinelx.dashboard.repository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JDBC-based repository for dashboard data access.
 * 
 * Provides low-level SQL queries for dashboard analytics and system summary data.
 * Uses NamedParameterJdbcTemplate for parameterized queries.
 */
@Slf4j
@Repository
public class DashboardJdbcRepository {

    private static final int HIGH_RISK_USER_THRESHOLD = 60;

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public DashboardJdbcRepository(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    /**
     * Retrieves activity count grouped by user within a time range.
     * 
     * @param from start time (inclusive)
     * @param to end time (inclusive)
     * @return map with user_id as key and activity count as value, sorted descending by count
     * @throws DataAccessException if query fails
     */
    public Map<String, Long> getActivityCountByUser(Instant from, Instant to) {
        log.debug("DashboardJdbcRepository.getActivityCountByUser called with from={}, to={}", from, to);

        try {
            String sql = "SELECT user_id, COUNT(*) AS cnt " +
                    "FROM activities " +
                    "WHERE created_at BETWEEN :from AND :to " +
                    "GROUP BY user_id ORDER BY cnt DESC";

            MapSqlParameterSource params = new MapSqlParameterSource()
                    .addValue("from", from)
                    .addValue("to", to);

            List<Map<String, Object>> rows = namedParameterJdbcTemplate.queryForList(sql, params);
            
            Map<String, Long> result = new HashMap<>();
            for (Map<String, Object> row : rows) {
                Long userId = ((Number) row.get("user_id")).longValue();
                Long count = ((Number) row.get("cnt")).longValue();
                result.put(userId.toString(), count);
            }
            
            return result;
        } catch (Exception e) {
            log.error("DashboardJdbcRepository.getActivityCountByUser failed: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Retrieves the top risky users based on their latest risk score.
     * 
     * Uses PostgreSQL-specific DISTINCT ON to get latest score per user.
     * Results ordered by score descending.
     * 
     * @param limit maximum number of users to return
     * @return list of maps with userId, username, and latestScore
     * @throws DataAccessException if query fails
     */
    public List<Map<String, Object>> getTopRiskyUsers(int limit) {
        log.debug("DashboardJdbcRepository.getTopRiskyUsers called with limit={}", limit);

        try {
            String sql = "SELECT u.id AS userId, u.username, rs.score AS latestScore " +
                    "FROM users u " +
                    "JOIN (SELECT DISTINCT ON (user_id) user_id, score " +
                    "      FROM risk_scores " +
                    "      ORDER BY user_id, calculated_at DESC) rs ON rs.user_id = u.id " +
                    "ORDER BY rs.score DESC " +
                    "LIMIT :limit";

            MapSqlParameterSource params = new MapSqlParameterSource("limit", limit);

            return namedParameterJdbcTemplate.queryForList(sql, params);
        } catch (Exception e) {
            log.error("DashboardJdbcRepository.getTopRiskyUsers failed: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Retrieves alert counts grouped by status within a time range.
     * 
     * @param from start time (inclusive)
     * @param to end time (inclusive)
     * @return map with status as key and alert count as value
     * @throws DataAccessException if query fails
     */
    public Map<String, Long> getAlertCountsByStatus(Instant from, Instant to) {
        log.debug("DashboardJdbcRepository.getAlertCountsByStatus called with from={}, to={}", from, to);

        try {
            String sql = "SELECT status, COUNT(*) AS cnt " +
                    "FROM alerts " +
                    "WHERE created_at BETWEEN :from AND :to " +
                    "GROUP BY status";

            MapSqlParameterSource params = new MapSqlParameterSource()
                    .addValue("from", from)
                    .addValue("to", to);

            List<Map<String, Object>> rows = namedParameterJdbcTemplate.queryForList(sql, params);
            
            Map<String, Long> result = new HashMap<>();
            for (Map<String, Object> row : rows) {
                String status = (String) row.get("status");
                Long count = ((Number) row.get("cnt")).longValue();
                result.put(status, count);
            }
            
            return result;
        } catch (Exception e) {
            log.error("DashboardJdbcRepository.getAlertCountsByStatus failed: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Retrieves alert trend data (daily alert counts) for the last N days.
     * 
     * @param lastNDays number of days to look back
     * @return list of maps with alertDate and cnt fields, ordered chronologically ascending
     * @throws DataAccessException if query fails
     */
    public List<Map<String, Object>> getAlertTrendByDay(int lastNDays) {
        log.debug("DashboardJdbcRepository.getAlertTrendByDay called with lastNDays={}", lastNDays);

        try {
            Instant cutoff = Instant.now().minus(lastNDays, ChronoUnit.DAYS);

            String sql = "SELECT DATE(created_at) AS alertDate, COUNT(*) AS cnt " +
                    "FROM alerts " +
                    "WHERE created_at >= :cutoff " +
                    "GROUP BY alertDate ORDER BY alertDate ASC";

            MapSqlParameterSource params = new MapSqlParameterSource("cutoff", cutoff);

            return namedParameterJdbcTemplate.queryForList(sql, params);
        } catch (Exception e) {
            log.error("DashboardJdbcRepository.getAlertTrendByDay failed: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Retrieves a system summary with key metrics.
     * 
     * @return map with keys: totalUsers, totalActivities, openAlerts, highRiskUsers
     * @throws DataAccessException if query fails
     */
    public Map<String, Long> getSystemSummary() {
        log.debug("DashboardJdbcRepository.getSystemSummary called");

        try {
            Map<String, Long> summary = new HashMap<>();

            // Total users
            String totalUsersSql = "SELECT COUNT(*) as cnt FROM users";
            Long totalUsers = namedParameterJdbcTemplate.queryForObject(
                    totalUsersSql,
                    new MapSqlParameterSource(),
                    Long.class
            );
            summary.put("totalUsers", totalUsers != null ? totalUsers : 0L);

            // Total activities
            String totalActivitiesSql = "SELECT COUNT(*) as cnt FROM activities";
            Long totalActivities = namedParameterJdbcTemplate.queryForObject(
                    totalActivitiesSql,
                    new MapSqlParameterSource(),
                    Long.class
            );
            summary.put("totalActivities", totalActivities != null ? totalActivities : 0L);

            // Open alerts
            String openAlertsSql = "SELECT COUNT(*) as cnt FROM alerts WHERE status = 'OPEN'";
            Long openAlerts = namedParameterJdbcTemplate.queryForObject(
                    openAlertsSql,
                    new MapSqlParameterSource(),
                    Long.class
            );
            summary.put("openAlerts", openAlerts != null ? openAlerts : 0L);

            // High risk users (score >= 60)
            String highRiskUsersSql = "SELECT COUNT(DISTINCT user_id) as cnt FROM risk_scores WHERE score >= :threshold";
            MapSqlParameterSource highRiskParams = new MapSqlParameterSource(
                    "threshold",
                    HIGH_RISK_USER_THRESHOLD
            );
            Long highRiskUsers = namedParameterJdbcTemplate.queryForObject(
                    highRiskUsersSql,
                    highRiskParams,
                    Long.class
            );
            summary.put("highRiskUsers", highRiskUsers != null ? highRiskUsers : 0L);

            return summary;
        } catch (Exception e) {
            log.error("DashboardJdbcRepository.getSystemSummary failed: {}", e.getMessage());
            throw e;
        }
    }
}
