package com.sentinelx.common.controller;

import com.sentinelx.common.dto.DbHealthResult;
import com.sentinelx.common.service.HealthService;
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

    @GetMapping
    @PreAuthorize("permitAll()")
    public ResponseEntity<DbHealthResult> health() {
        DbHealthResult result = healthService.getDbHealthResult();
        HttpStatus status = "DOWN".equals(result.status()) ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.OK;
        return ResponseEntity.status(status).body(result);
    }

    @GetMapping("/live")
    @PreAuthorize("permitAll()")
    public ResponseEntity<Map<String, String>> liveness() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "SentinelX"));
    }

    @GetMapping("/ready")
    @PreAuthorize("permitAll()")
    public ResponseEntity<Map<String, Object>> readiness() {
        DbHealthResult result = healthService.getDbHealthResult();
        HttpStatus status = "DOWN".equals(result.status()) ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.OK;
        return ResponseEntity.status(status).body(
                Map.of("status", result.status(), "dbLatencyMs", result.dbLatencyMs())
        );
    }
}
