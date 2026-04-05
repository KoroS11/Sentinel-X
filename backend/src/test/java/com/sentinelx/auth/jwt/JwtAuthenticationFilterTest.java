package com.sentinelx.auth.jwt;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.sentinelx.auth.security.CustomUserDetailsService;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

class JwtAuthenticationFilterTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void requestWithValidTokenSetsSecurityContext() {
        JwtTokenProvider jwtTokenProvider = org.mockito.Mockito.mock(JwtTokenProvider.class);
        CustomUserDetailsService userDetailsService = org.mockito.Mockito.mock(CustomUserDetailsService.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtTokenProvider, userDetailsService);

        when(jwtTokenProvider.validateToken("valid-token")).thenReturn(true);
        when(jwtTokenProvider.extractUsername("valid-token")).thenReturn("alice");

        UserDetails userDetails = User.withUsername("alice")
            .password("hashed")
            .authorities("ROLE_ADMIN")
            .build();
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(userDetails);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        assertDoesNotThrow(() -> filter.doFilter(request, response, filterChain));

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("alice", SecurityContextHolder.getContext().getAuthentication().getName());
        Set<String> authorities = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
            .map(grantedAuthority -> grantedAuthority.getAuthority())
            .collect(Collectors.toSet());
        assertTrue(authorities.contains("ROLE_ADMIN"));
    }

    @Test
    void requestWithNoTokenPassesWithoutError() {
        JwtTokenProvider jwtTokenProvider = org.mockito.Mockito.mock(JwtTokenProvider.class);
        CustomUserDetailsService userDetailsService = org.mockito.Mockito.mock(CustomUserDetailsService.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtTokenProvider, userDetailsService);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        assertDoesNotThrow(() -> filter.doFilter(request, response, filterChain));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void requestWithInvalidTokenPassesWithoutError() {
        JwtTokenProvider jwtTokenProvider = org.mockito.Mockito.mock(JwtTokenProvider.class);
        CustomUserDetailsService userDetailsService = org.mockito.Mockito.mock(CustomUserDetailsService.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtTokenProvider, userDetailsService);

        when(jwtTokenProvider.validateToken("invalid-token")).thenReturn(false);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer invalid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        assertDoesNotThrow(() -> filter.doFilter(request, response, filterChain));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
