package com.sentinelx.common.controller;

import com.sentinelx.common.dto.DbHealthResult;
import com.sentinelx.common.service.HealthService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/health")
@RequiredArgsConstructor
public class HealthController {

    private final HealthService healthService;

    /**
     * Combined health check endpoint.
     * - UP (latency < 3s) → 200 OK
     * - DEGRADED (latency >= 3s) → 200 OK (still available, just slow)
     * - DOWN → 503 Service Unavailable
     */
    @GetMapping
    @PreAuthorize("permitAll()")
    public ResponseEntity<Map<String, Object>> health() {
        DbHealthResult result = healthService.getDbHealthResult();
        Map<String, Object> response = buildHealthResponse(result);
        
        if ("DOWN".equals(result.getStatus())) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
        }
        return ResponseEntity.ok(response); // 200 for both UP and DEGRADED
    }

    /**
     * Liveness probe - checks if application is alive.
     * Does NOT call database - must be lightweight and always return 200.
     * Kubernetes uses this to restart pods.
     */
    @GetMapping("/live")
    @PreAuthorize("permitAll()")
    public ResponseEntity<Map<String, Object>> liveness() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "UP");
        response.put("timestamp", Instant.now().toString());
        return ResponseEntity.ok(response);
    }

    /**
     * Readiness probe - checks if application is ready to accept traffic.
     * Calls database - Kubernetes uses this to add/remove pod from load balancer.
     * - UP or DEGRADED → 200 OK (ready to serve requests)
     * - DOWN → 503 Service Unavailable (not ready)
     */
    @GetMapping("/ready")
    @PreAuthorize("permitAll()")
    public ResponseEntity<Map<String, Object>> readiness() {
        DbHealthResult result = healthService.getDbHealthResult();
        Map<String, Object> response = buildHealthResponse(result);
        
        if ("DOWN".equals(result.getStatus())) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
        }
        return ResponseEntity.ok(response); // 200 for both UP and DEGRADED
    }

    private Map<String, Object> buildHealthResponse(DbHealthResult result) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", result.getStatus());
        response.put("dbReachable", result.isDbReachable());
        response.put("dbLatencyMs", result.getDbLatencyMs());
        response.put("timestamp", Instant.now().toString());
        return response;
    }
}
