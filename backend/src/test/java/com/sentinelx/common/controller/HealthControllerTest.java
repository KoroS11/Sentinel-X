package com.sentinelx.common.controller;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sentinelx.common.dto.DbHealthResult;
import com.sentinelx.common.service.HealthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:healthcontrollertest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false",
    "jwt.secret=health_controller_test_secret_at_least_32_chars",
    "jwt.expiration-ms=3600000",
    "jwt.refresh-expiration-ms=604800000"
})
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private HealthService healthService;

    @Test
    void testGetHealth_dbUp_returns200WithStatusUp() throws Exception {
        // Arrange
        DbHealthResult result = DbHealthResult.builder()
            .status("UP")
            .dbReachable(true)
            .dbLatencyMs(45)
            .build();
        when(healthService.getDbHealthResult()).thenReturn(result);

        // Act & Assert
        mockMvc.perform(get("/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.dbReachable").value(true))
            .andExpect(jsonPath("$.dbLatencyMs").value(45));
    }

    @Test
    void testGetHealth_dbDown_returns503() throws Exception {
        // Arrange
        DbHealthResult result = DbHealthResult.builder()
            .status("DOWN")
            .dbReachable(false)
            .dbLatencyMs(0)
            .build();
        when(healthService.getDbHealthResult()).thenReturn(result);

        // Act & Assert
        mockMvc.perform(get("/health"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.status").value("DOWN"))
            .andExpect(jsonPath("$.dbReachable").value(false));
    }

    @Test
    void testGetHealth_dbDegraded_returns200WithDegradedStatus() throws Exception {
        // Arrange
        DbHealthResult result = DbHealthResult.builder()
            .status("DEGRADED")
            .dbReachable(true)
            .dbLatencyMs(3500)
            .build();
        when(healthService.getDbHealthResult()).thenReturn(result);

        // Act & Assert
        mockMvc.perform(get("/health"))
            .andExpect(status().isOk()) // DEGRADED must be 200, not 503
            .andExpect(jsonPath("$.status").value("DEGRADED"))
            .andExpect(jsonPath("$.dbReachable").value(true))
            .andExpect(jsonPath("$.dbLatencyMs").value(3500));
    }

    @Test
    void testLivenessEndpoint_alwaysReturns200_withoutCallingDb() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/health/live"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
        
        // Verify HealthService was never called (liveness must not touch DB)
        verify(healthService, never()).getDbHealthResult();
    }

    @Test
    void testReadinessEndpoint_dbDown_returns503() throws Exception {
        // Arrange
        DbHealthResult result = DbHealthResult.builder()
            .status("DOWN")
            .dbReachable(false)
            .dbLatencyMs(0)
            .build();
        when(healthService.getDbHealthResult()).thenReturn(result);

        // Act & Assert
        mockMvc.perform(get("/health/ready"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.status").value("DOWN"));
    }

    @Test
    void testReadinessEndpoint_dbDegraded_returns200WithLatency() throws Exception {
        // Arrange
        DbHealthResult result = DbHealthResult.builder()
            .status("DEGRADED")
            .dbReachable(true)
            .dbLatencyMs(1500)
            .build();
        when(healthService.getDbHealthResult()).thenReturn(result);

        // Act & Assert
        mockMvc.perform(get("/health/ready"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DEGRADED"))
            .andExpect(jsonPath("$.dbLatencyMs").value(1500));
    }
}
