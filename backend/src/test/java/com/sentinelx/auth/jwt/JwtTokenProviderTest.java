package com.sentinelx.auth.jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

    @Test
    void generatesValidTokenAndExtractsUsername() {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setSecret("this_is_a_test_secret_with_more_than_32_chars");
        jwtProperties.setExpirationMs(3600000L);

        JwtTokenProvider tokenProvider = new JwtTokenProvider(jwtProperties);

        String token = tokenProvider.generateToken("alice", List.of("ADMIN", "ANALYST"));

        assertTrue(token != null && !token.isBlank());
        assertTrue(tokenProvider.validateToken(token));
        assertEquals("alice", tokenProvider.extractUsername(token));
    }
}
