package com.sentinelx.config;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sentinelx.auth.jwt.JwtTokenProvider;
import com.sentinelx.auth.security.CustomUserDetailsService;
import com.sentinelx.common.service.HealthService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:securityconfigtest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false",
    "jwt.secret=security_config_test_secret_at_least_32_chars",
    "jwt.expiration-ms=3600000",
    "jwt.refresh-expiration-ms=604800000"
})
@ActiveProfiles("test")
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @MockBean
    private HealthService healthService;

    @Test
    void healthEndpointIsPublic() throws Exception {
        when(healthService.isDatabaseConnected()).thenReturn(true);

        mockMvc.perform(get("/health"))
            .andExpect(status().isOk());
    }

    @Test
    void protectedEndpointWithoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/some-protected-path"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointWithValidJwtIsNotUnauthorized() throws Exception {
        UserDetails userDetails = User.withUsername("alice")
            .password("hashed")
            .authorities("ROLE_ADMIN")
            .build();
        when(customUserDetailsService.loadUserByUsername("alice")).thenReturn(userDetails);

        String token = jwtTokenProvider.generateToken("alice", List.of("ADMIN"));

        MvcResult result = mockMvc.perform(get("/api/some-protected-path")
                .header("Authorization", "Bearer " + token))
            .andReturn();

        assertNotEquals(401, result.getResponse().getStatus());
    }
}
