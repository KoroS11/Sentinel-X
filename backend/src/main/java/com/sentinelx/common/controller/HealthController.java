package com.sentinelx.common.controller;

import com.sentinelx.common.service.HealthService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/health")
@RequiredArgsConstructor
public class HealthController {

    private final HealthService healthService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        boolean dbConnected = healthService.isDatabaseConnected();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", dbConnected ? "UP" : "DEGRADED");
        response.put("application", "UP");
        response.put("database", dbConnected ? "CONNECTED" : "DISCONNECTED");
        response.put("timestamp", Instant.now().toString());
        return ResponseEntity.status(dbConnected ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }
}
