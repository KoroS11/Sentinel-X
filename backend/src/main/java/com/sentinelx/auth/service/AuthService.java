package com.sentinelx.auth.service;

import com.sentinelx.auth.dto.AuthResponse;
import com.sentinelx.auth.dto.LoginRequest;
import com.sentinelx.auth.dto.RegisterRequest;
import com.sentinelx.auth.entity.RefreshToken;
import com.sentinelx.auth.exception.DuplicateEmailException;
import com.sentinelx.auth.exception.InvalidCredentialsException;
import com.sentinelx.auth.jwt.JwtTokenProvider;
import com.sentinelx.auth.refresh.RefreshTokenService;
import com.sentinelx.user.entity.Role;
import com.sentinelx.user.entity.RoleType;
import com.sentinelx.user.entity.User;
import com.sentinelx.user.repository.RoleRepository;
import com.sentinelx.user.repository.UserRepository;
import java.util.List;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final EmailVerificationService emailVerificationService;

    public AuthService(
        UserRepository userRepository,
        RoleRepository roleRepository,
        PasswordEncoder passwordEncoder,
        AuthenticationManager authenticationManager,
        JwtTokenProvider jwtTokenProvider,
        RefreshTokenService refreshTokenService,
        EmailVerificationService emailVerificationService
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenService = refreshTokenService;
        this.emailVerificationService = emailVerificationService;
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateEmailException("Email is already registered.");
        }

        Role defaultRole = roleRepository.findByName(RoleType.EMPLOYEE)
            .orElseThrow(() -> new IllegalStateException("Default EMPLOYEE role is not configured."));

        User user = new User();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setActive(true);
        user.setRole(defaultRole);
        user.setEmailVerified(false);

        User savedUser = userRepository.save(user);
        emailVerificationService.sendVerification(savedUser);
        return createAuthResponse(savedUser, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public AuthResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
            );
        } catch (BadCredentialsException ex) {
            throw new InvalidCredentialsException("Invalid email or password.");
        } catch (AuthenticationException ex) {
            throw new InvalidCredentialsException("Authentication failed.");
        }

        User user = userRepository.findByEmail(request.email())
            .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password."));

        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

        return createAuthResponse(user, refreshToken.getToken());
    }

    @Transactional(rollbackFor = Exception.class)
    public void logout(String username) {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new InvalidCredentialsException("Invalid user context."));
        refreshTokenService.revokeAllUserTokens(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse createAuthResponse(User user, String refreshToken) {
        String token = jwtTokenProvider.generateToken(
            user.getUsername(),
            List.of(user.getRole().getName().name())
        );
        return new AuthResponse(token, user.getUsername(), refreshToken);
    }
}
