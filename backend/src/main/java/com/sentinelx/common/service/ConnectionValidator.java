package com.sentinelx.common.service;

import com.sentinelx.common.dto.DbHealthResult;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;

@Slf4j
@Service
public class ConnectionValidator {

    private final DataSource dataSource;
    private final long degradedThresholdMs;
    private final long warnThresholdMs;

    public ConnectionValidator(
            DataSource dataSource,
            @Value("${health.db.degradedThresholdMs:2000}") long degradedThresholdMs,
            @Value("${health.db.warnThresholdMs:500}") long warnThresholdMs
    ) {
        this.dataSource = dataSource;
        this.degradedThresholdMs = degradedThresholdMs;
        this.warnThresholdMs = warnThresholdMs;
    }

    public DbHealthResult validate() {
        long start = System.nanoTime();
        long latencyMs;

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("SELECT 1");
            latencyMs = (System.nanoTime() - start) / 1_000_000L;
        } catch (Exception ex) {
            log.error("Database connectivity check failed: {}", ex.getMessage());
            return DbHealthResult.builder()
                    .status("DOWN")
                    .dbReachable(false)
                    .dbLatencyMs(-1)
                    .message(ex.getMessage())
                    .checkedAt(Instant.now())
                    .poolName(resolvePoolName())
                    .activeConnections(-1)
                    .idleConnections(-1)
                    .totalConnections(-1)
                    .pendingThreads(-1)
                    .build();
        }

        // Pool metrics
        String poolName = "unknown";
        int activeConnections = -1;
        int idleConnections = -1;
        int totalConnections = -1;
        int pendingThreads = -1;

        try {
            HikariDataSource hikari = (HikariDataSource) dataSource;
            poolName = hikari.getHikariConfigMXBean().getPoolName();
            activeConnections = hikari.getHikariPoolMXBean().getActiveConnections();
            idleConnections = hikari.getHikariPoolMXBean().getIdleConnections();
            totalConnections = hikari.getHikariPoolMXBean().getTotalConnections();
            pendingThreads = hikari.getHikariPoolMXBean().getThreadsAwaitingConnection();
        } catch (Exception ex) {
            log.debug("HikariCP pool metrics unavailable — using defaults: {}", ex.getMessage());
        }

        // Status determination
        String status;
        String message;

        if (latencyMs > degradedThresholdMs) {
            status = "DEGRADED";
            message = "Database reachable but latency (" + latencyMs + "ms) exceeds degraded threshold (" + degradedThresholdMs + "ms)";
        } else {
            status = "UP";
            message = "Database connection healthy";
        }

        if (latencyMs > warnThresholdMs) {
            log.warn("DB latency warning: {}ms", latencyMs);
        }

        return DbHealthResult.builder()
                .status(status)
                .dbReachable(true)
                .dbLatencyMs(latencyMs)
                .message(message)
                .checkedAt(Instant.now())
                .poolName(poolName)
                .activeConnections(activeConnections)
                .idleConnections(idleConnections)
                .totalConnections(totalConnections)
                .pendingThreads(pendingThreads)
                .build();
    }

    private String resolvePoolName() {
        try {
            return ((HikariDataSource) dataSource).getHikariConfigMXBean().getPoolName();
        } catch (Exception ex) {
            return "unknown";
        }
    }
}
