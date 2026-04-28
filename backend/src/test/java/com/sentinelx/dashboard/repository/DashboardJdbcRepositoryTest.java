package com.sentinelx.dashboard.repository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration tests for DashboardJdbcRepository using H2 in-memory database.
 * 
 * Tests dashboard JDBC query methods with minimal seeded test data.
 * Note: Some tests are disabled for H2 as they use PostgreSQL-specific SQL syntax.
 */
@SpringBootTest
@ActiveProfiles("test")
public class DashboardJdbcRepositoryTest {

    @Autowired
    private DashboardJdbcRepository dashboardJdbcRepository;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    public void setUp() throws Exception {
        // Seed test data
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            // Clear existing data
            stmt.execute("DELETE FROM alerts");
            stmt.execute("DELETE FROM risk_scores");
            stmt.execute("DELETE FROM activities");
            stmt.execute("DELETE FROM users");
            stmt.execute("DELETE FROM roles");

            // Insert test roles
            stmt.execute("INSERT INTO roles (id, name, created_at, updated_at) " +
                    "VALUES (1, 'USER_ROLE', NOW(), NOW())");

            // Insert test users
            stmt.execute("INSERT INTO users (id, username, email, password_hash, status, is_active, email_verified, role_id, created_at, updated_at) " +
                    "VALUES (1, 'user1', 'user1@test.com', 'hashed_pwd', 'ACTIVE', true, true, 1, NOW(), NOW())");
            stmt.execute("INSERT INTO users (id, username, email, password_hash, status, is_active, email_verified, role_id, created_at, updated_at) " +
                    "VALUES (2, 'user2', 'user2@test.com', 'hashed_pwd', 'ACTIVE', true, true, 1, NOW(), NOW())");
            stmt.execute("INSERT INTO users (id, username, email, password_hash, status, is_active, email_verified, role_id, created_at, updated_at) " +
                    "VALUES (3, 'user3', 'user3@test.com', 'hashed_pwd', 'ACTIVE', true, true, 1, NOW(), NOW())");

            // Insert test activities
            Instant now = Instant.now();
            stmt.execute("INSERT INTO activities (id, user_id, action, entity_type, entity_id, created_at) " +
                    "VALUES (1, 1, 'LOGIN', 'USER', '1', '" + now + "')");
            stmt.execute("INSERT INTO activities (id, user_id, action, entity_type, entity_id, created_at) " +
                    "VALUES (2, 1, 'CREATE', 'ROLE', '5', '" + now.minus(1, ChronoUnit.DAYS) + "')");
            stmt.execute("INSERT INTO activities (id, user_id, action, entity_type, entity_id, created_at) " +
                    "VALUES (3, 2, 'UPDATE', 'USER', '2', '" + now.minus(2, ChronoUnit.DAYS) + "')");
            stmt.execute("INSERT INTO activities (id, user_id, action, entity_type, entity_id, created_at) " +
                    "VALUES (4, 2, 'DELETE', 'ROLE', '6', '" + now.minus(3, ChronoUnit.DAYS) + "')");

            // Insert test risk scores
            stmt.execute("INSERT INTO risk_scores (id, user_id, score, reason, calculated_at) " +
                    "VALUES (1, 1, 75, 'High activity', '" + now + "')");
            stmt.execute("INSERT INTO risk_scores (id, user_id, score, reason, calculated_at) " +
                    "VALUES (2, 2, 55, 'Normal activity', '" + now + "')");
            stmt.execute("INSERT INTO risk_scores (id, user_id, score, reason, calculated_at) " +
                    "VALUES (3, 3, 40, 'Low activity', '" + now + "')");

            // Insert test alerts
            stmt.execute("INSERT INTO alerts (id, user_id, risk_score_id, severity, message, status, created_at) " +
                    "VALUES (1, 1, 1, 'HIGH', 'High risk detected', 'OPEN', '" + now + "')");
            stmt.execute("INSERT INTO alerts (id, user_id, risk_score_id, severity, message, status, created_at) " +
                    "VALUES (2, 2, 2, 'MEDIUM', 'Medium risk detected', 'OPEN', '" + now.minus(1, ChronoUnit.DAYS) + "')");
            stmt.execute("INSERT INTO alerts (id, user_id, risk_score_id, severity, message, status, created_at) " +
                    "VALUES (3, 1, 1, 'HIGH', 'High risk alert', 'RESOLVED', '" + now.minus(2, ChronoUnit.DAYS) + "')");

            conn.commit();
        }
    }

    @AfterEach
    public void tearDown() throws Exception {
        // Clean up test data
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute("DELETE FROM alerts");
            stmt.execute("DELETE FROM risk_scores");
            stmt.execute("DELETE FROM activities");
            stmt.execute("DELETE FROM users");

            conn.commit();
        }
    }

    /**
     * Test getActivityCountByUser returns correct activity counts grouped by user.
     */
    @Test
    public void testGetActivityCountByUser_returnsCorrectCounts() {
        Instant now = Instant.now();
        Instant from = now.minus(10, ChronoUnit.DAYS);
        Instant to = now.plus(1, ChronoUnit.DAYS);

        Map<String, Long> result = dashboardJdbcRepository.getActivityCountByUser(from, to);

        assertThat(result)
                .isNotEmpty()
                .containsEntry("1", 2L)  // user1 has 2 activities
                .containsEntry("2", 2L); // user2 has 2 activities
    }

    /**
     * Test getTopRiskyUsers is disabled for H2 (uses PostgreSQL-specific DISTINCT ON).
     */
    @Test
    @Disabled("Requires PostgreSQL — DISTINCT ON not supported in H2")
    public void testGetTopRiskyUsers_skippedForH2() {
        // This test is disabled because DISTINCT ON is PostgreSQL-specific
        // and not supported in H2 database used for testing
    }

    /**
     * Test getAlertCountsByStatus groups and counts alerts by status correctly.
     */
    @Test
    public void testGetAlertCountsByStatus_groupsCorrectly() {
        Instant now = Instant.now();
        Instant from = now.minus(10, ChronoUnit.DAYS);
        Instant to = now.plus(1, ChronoUnit.DAYS);

        Map<String, Long> result = dashboardJdbcRepository.getAlertCountsByStatus(from, to);

        assertThat(result)
                .isNotEmpty()
                .containsEntry("OPEN", 2L)      // 2 OPEN alerts
                .containsEntry("RESOLVED", 1L); // 1 RESOLVED alert
    }

    /**
     * Test getSystemSummary returns all expected keys with correct counts.
     */
    @Test
    public void testGetSystemSummary_containsAllExpectedKeys() {
        Map<String, Long> summary = dashboardJdbcRepository.getSystemSummary();

        assertThat(summary)
                .containsKeys("totalUsers", "totalActivities", "openAlerts", "highRiskUsers")
                .containsEntry("totalUsers", 3L)         // 3 users inserted
                .containsEntry("totalActivities", 4L)    // 4 activities inserted
                .containsEntry("openAlerts", 2L)         // 2 open alerts
                .containsEntry("highRiskUsers", 1L);     // 1 user with score >= 60 (user1: 75)
    }

    /**
     * Test getAlertTrendByDay returns data in ascending chronological order.
     */
    @Test
    public void testGetAlertTrendByDay_returnsAscendingDateOrder() {
        int lastNDays = 10;

        List<Map<String, Object>> result = dashboardJdbcRepository.getAlertTrendByDay(lastNDays);

        assertThat(result)
                .isNotEmpty()
                .hasSizeGreaterThanOrEqualTo(3); // At least 3 days with alerts

        // Verify chronological order (alertDate should be ascending)
        for (int i = 1; i < result.size(); i++) {
            String prevDate = (String) result.get(i - 1).get("alertDate");
            String currDate = (String) result.get(i).get("alertDate");
            assertThat(currDate).isGreaterThanOrEqualTo(prevDate);
        }
    }

    /**
     * Test getActivityCountByUser returns empty map when no data matches the time range.
     */
    @Test
    public void testGetActivityCountByUser_emptyRangeReturnsEmptyMap() {
        Instant from = Instant.now().minus(100, ChronoUnit.DAYS);
        Instant to = from.plus(10, ChronoUnit.DAYS);

        Map<String, Long> result = dashboardJdbcRepository.getActivityCountByUser(from, to);

        assertThat(result).isEmpty();
    }

    /**
     * Test that repository propagates exceptions instead of swallowing them.
     * Uses mocked NamedParameterJdbcTemplate to simulate DataAccessException.
     */
    @Test
    public void testRepositoryPropagatesException_whenSqlExecutionFails() {
        // Create a mock NamedParameterJdbcTemplate that throws DataAccessException
        NamedParameterJdbcTemplate mockTemplate = mock(NamedParameterJdbcTemplate.class);

        when(mockTemplate.queryForObject(
                "SELECT COUNT(*) as cnt FROM users",
                new MapSqlParameterSource(),
                Long.class
        )).thenThrow(new DataAccessException("Simulated SQL error") {});

        // Create repository instance with mocked template
        DashboardJdbcRepository testRepository = new DashboardJdbcRepository(mockTemplate);

        // Verify that the exception is propagated (not swallowed)
        assertThatThrownBy(() -> testRepository.getSystemSummary())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("Simulated SQL error");
    }
}
