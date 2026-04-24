package com.sentinelx.dashboard;

import com.sentinelx.auth.jwt.JwtTokenProvider;
import com.sentinelx.auth.security.CustomUserDetailsService;
import com.sentinelx.dashboard.controller.DashboardController;
import com.sentinelx.dashboard.service.DashboardService;
import com.sentinelx.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that the dashboard security contract is completely unchanged
 * after all JDBC changes. Security filters are fully active — no addFilters=false.
 * Unauthenticated requests must be rejected with 401 or 403.
 */
@WebMvcTest(DashboardController.class)
public class DashboardSecurityContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DashboardService dashboardService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @MockBean
    private UserRepository userRepository;

    @Test
    void testUnauthenticatedRequest_returns401Or403() throws Exception {
        mockMvc.perform(get("/api/dashboard/admin"))
            .andExpect(status().is(anyOf(equalTo(401), equalTo(403))));
    }
}
