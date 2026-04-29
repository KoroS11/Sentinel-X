package com.sentinelx.common.controller;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sentinelx.auth.jwt.JwtAuthenticationFilter;
import com.sentinelx.auth.security.CustomUserDetailsService;
import com.sentinelx.common.dto.DbHealthResult;
import com.sentinelx.common.service.HealthService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(HealthController.class)
@AutoConfigureMockMvc(addFilters = false)
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private HealthService healthService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    private DbHealthResult buildResult(String resultStatus, long latencyMs) {
        return DbHealthResult.builder()
                .status(resultStatus)
                .dbReachable(!"DOWN".equals(resultStatus))
                .dbLatencyMs(latencyMs)
                .message("test")
                .checkedAt(Instant.now())
                .poolName("test-pool")
                .activeConnections(1)
                .idleConnections(4)
                .totalConnections(5)
                .pendingThreads(0)
                .build();
    }

    @Test
    void testGetHealth_dbUp_returns200WithStatusField() throws Exception {
        when(healthService.getDbHealthResult()).thenReturn(buildResult("UP", 10));

        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void testGetHealth_dbDown_returns503() throws Exception {
        when(healthService.getDbHealthResult()).thenReturn(buildResult("DOWN", -1));

        mockMvc.perform(get("/health"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void testGetHealth_dbDegraded_returns200WithDegradedStatus() throws Exception {
        when(healthService.getDbHealthResult()).thenReturn(buildResult("DEGRADED", 3000));

        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEGRADED"));
    }

    @Test
    void testLivenessEndpoint_alwaysReturns200_withoutCallingService() throws Exception {
        mockMvc.perform(get("/health/live"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        verify(healthService, never()).getDbHealthResult();
    }

    @Test
    void testReadinessEndpoint_dbDown_returns503() throws Exception {
        when(healthService.getDbHealthResult()).thenReturn(buildResult("DOWN", -1));

        mockMvc.perform(get("/health/ready"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void testReadinessEndpoint_dbDegraded_returns200() throws Exception {
        when(healthService.getDbHealthResult()).thenReturn(buildResult("DEGRADED", 1500));

        mockMvc.perform(get("/health/ready"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dbLatencyMs").value(1500));
    }
}
