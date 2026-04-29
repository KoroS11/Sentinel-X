package com.sentinelx.common.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.sentinelx.common.dto.DbHealthResult;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConnectionValidatorTest {

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private Statement statement;

    private ConnectionValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ConnectionValidator(dataSource, 2000L, 500L);
    }

    @Test
    void testHealthyDb_returnsUpStatus() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.execute("SELECT 1")).thenReturn(true);

        DbHealthResult result = validator.validate();

        assertEquals("UP", result.status());
        assertTrue(result.dbReachable());
    }

    @Test
    void testUnreachableDb_returnsDownStatus() throws SQLException {
        when(dataSource.getConnection()).thenThrow(new SQLException("Connection refused"));

        DbHealthResult result = validator.validate();

        assertEquals("DOWN", result.status());
        assertFalse(result.dbReachable());
    }

    @Test
    void testLatencyAboveDegradedThreshold_returnsDegradedStatus() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.execute("SELECT 1")).thenReturn(true);

        ConnectionValidator zeroThresholdValidator = new ConnectionValidator(dataSource, -1L, -1L);
        DbHealthResult result = zeroThresholdValidator.validate();

        assertEquals("DEGRADED", result.status());
        assertTrue(result.dbReachable());
    }

    @Test
    void testResultContainsCheckedAtTimestamp() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.execute("SELECT 1")).thenReturn(true);

        Instant before = Instant.now();
        DbHealthResult result = validator.validate();

        assertNotNull(result.checkedAt());
        assertFalse(result.checkedAt().isAfter(Instant.now()),
                "checkedAt must not be in the future");
        assertFalse(result.checkedAt().isBefore(before),
                "checkedAt must not be before the test started");
    }
}
