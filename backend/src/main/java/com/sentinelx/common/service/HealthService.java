package com.sentinelx.common.service;

import com.sentinelx.common.dto.DbHealthResult;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HealthService {

    private final JdbcTemplate jdbcTemplate;
    private static final long DEGRADED_THRESHOLD_MS = 3000; // 3 seconds

    public boolean isDatabaseConnected() {
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return result != null && result == 1;
        } catch (Exception ex) {
            return false;
        }
    }

    public DbHealthResult getDbHealthResult() {
        long startTime = System.currentTimeMillis();
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            long latencyMs = System.currentTimeMillis() - startTime;
            boolean isReachable = result != null && result == 1;
            
            if (!isReachable) {
                return DbHealthResult.builder()
                    .status("DOWN")
                    .dbReachable(false)
                    .dbLatencyMs(latencyMs)
                    .build();
            }
            
            String status = latencyMs > DEGRADED_THRESHOLD_MS ? "DEGRADED" : "UP";
            return DbHealthResult.builder()
                .status(status)
                .dbReachable(true)
                .dbLatencyMs(latencyMs)
                .build();
        } catch (Exception ex) {
            long latencyMs = System.currentTimeMillis() - startTime;
            return DbHealthResult.builder()
                .status("DOWN")
                .dbReachable(false)
                .dbLatencyMs(latencyMs)
                .build();
        }
    }
}
