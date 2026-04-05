package com.sentinelx.user.controller;

import com.sentinelx.auth.exception.InvalidCredentialsException;
import com.sentinelx.auth.security.RoleConstants;
import com.sentinelx.user.dto.CreateUserRequest;
import com.sentinelx.user.dto.UpdateUserRequest;
import com.sentinelx.user.dto.UserResponse;
import com.sentinelx.user.dto.UserStatusRequest;
import com.sentinelx.user.entity.User;
import com.sentinelx.user.service.UserService;
import com.sentinelx.user.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final UserRepository userRepository;

    public UserController(UserService userService, UserRepository userRepository) {
        this.userService = userService;
        this.userRepository = userRepository;
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.sentinelx.auth.security.RoleConstants).ADMIN)")
    public Page<UserResponse> getAllUsers(Pageable pageable) {
        return userService.getAllUsers(pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority(T(com.sentinelx.auth.security.RoleConstants).EMPLOYEE, T(com.sentinelx.auth.security.RoleConstants).ANALYST, T(com.sentinelx.auth.security.RoleConstants).ADMIN)")
    public UserResponse getUserById(@PathVariable Long id, Authentication authentication) {
        ensureAdminOrOwnProfile(authentication, id);
        return userService.getUserById(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority(T(com.sentinelx.auth.security.RoleConstants).ADMIN)")
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createUser(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority(T(com.sentinelx.auth.security.RoleConstants).EMPLOYEE, T(com.sentinelx.auth.security.RoleConstants).ANALYST, T(com.sentinelx.auth.security.RoleConstants).ADMIN)")
    public UserResponse updateUser(
        @PathVariable Long id,
        @Valid @RequestBody UpdateUserRequest request,
        Authentication authentication
    ) {
        ensureAdminOrOwnProfile(authentication, id);
        return userService.updateUser(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.sentinelx.auth.security.RoleConstants).ADMIN)")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority(T(com.sentinelx.auth.security.RoleConstants).ADMIN)")
    public UserResponse updateUserStatus(@PathVariable Long id, @Valid @RequestBody UserStatusRequest request) {
        return userService.updateUserStatus(id, request);
    }

    private void ensureAdminOrOwnProfile(Authentication authentication, Long targetUserId) {
        if (authentication == null || authentication.getName() == null) {
            throw new InvalidCredentialsException("Authentication is required.");
        }

        boolean isAdmin = authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch(RoleConstants.ADMIN::equals);
        if (isAdmin) {
            return;
        }

        User currentUser = userRepository.findByUsername(authentication.getName())
            .orElseThrow(() -> new InvalidCredentialsException("Invalid user context."));

        if (!currentUser.getId().equals(targetUserId)) {
            throw new AccessDeniedException("You can only access your own profile.");
        }
    }
}