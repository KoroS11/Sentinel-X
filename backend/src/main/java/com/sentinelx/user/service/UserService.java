package com.sentinelx.user.service;

import com.sentinelx.auth.exception.DuplicateEmailException;
import com.sentinelx.auth.service.EmailVerificationService;
import com.sentinelx.user.dto.CreateUserRequest;
import com.sentinelx.user.dto.UpdateUserRequest;
import com.sentinelx.user.dto.UserResponse;
import com.sentinelx.user.dto.UserStatusRequest;
import com.sentinelx.user.entity.Role;
import com.sentinelx.user.entity.RoleType;
import com.sentinelx.user.entity.User;
import com.sentinelx.user.exception.ResourceNotFoundException;
import com.sentinelx.user.exception.UserOperationNotAllowedException;
import com.sentinelx.user.repository.RoleRepository;
import com.sentinelx.user.repository.UserRepository;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private static final String USER_NOT_FOUND = "User not found.";
    private static final String EMAIL_ALREADY_REGISTERED = "Email is already registered.";
    private static final String ROLE_NOT_FOUND_PREFIX = "Role not found: ";
    private static final String ADMIN_DELETE_FORBIDDEN = "Deleting an ADMIN user is not allowed.";
    private static final String LAST_ADMIN_DELETE_FORBIDDEN = "Deleting the last ADMIN user is not allowed.";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerificationService;

    public UserService(
        UserRepository userRepository,
        RoleRepository roleRepository,
        PasswordEncoder passwordEncoder,
        EmailVerificationService emailVerificationService
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailVerificationService = emailVerificationService;
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(UserResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {
        return UserResponse.fromEntity(findExistingUser(id));
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateEmailException(EMAIL_ALREADY_REGISTERED);
        }

        RoleType roleType = parseRoleType(request.role());
        Role role = roleRepository.findByName(roleType)
            .orElseThrow(() -> new ResourceNotFoundException(ROLE_NOT_FOUND_PREFIX + roleType.name()));

        User user = new User();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(role);
        user.setActive(true);

        User savedUser = userRepository.save(user);
        emailVerificationService.sendVerification(savedUser);
        return UserResponse.fromEntity(savedUser);
    }

    @Transactional
    public UserResponse updateUser(Long id, UpdateUserRequest request) {
        User user = findExistingUser(id);

        if (request.username() != null) {
            user.setUsername(request.username());
        }

        if (request.email() != null && !request.email().equalsIgnoreCase(user.getEmail())) {
            if (userRepository.existsByEmail(request.email())) {
                throw new DuplicateEmailException(EMAIL_ALREADY_REGISTERED);
            }
            user.setEmail(request.email());
        }

        return UserResponse.fromEntity(userRepository.save(user));
    }

    @Transactional
    public void deleteUser(Long id) {
        User user = findExistingUser(id);

        if (user.getRole().getName() == RoleType.ADMIN) {
            long adminCount = userRepository.countByRole_Name(RoleType.ADMIN);
            if (adminCount <= 1) {
                throw new UserOperationNotAllowedException(LAST_ADMIN_DELETE_FORBIDDEN);
            }
            throw new UserOperationNotAllowedException(ADMIN_DELETE_FORBIDDEN);
        }

        userRepository.delete(user);
    }

    @Transactional
    public UserResponse updateUserStatus(Long id, UserStatusRequest request) {
        User user = findExistingUser(id);
        user.setStatus(request.status());
        return UserResponse.fromEntity(userRepository.save(user));
    }

    private User findExistingUser(Long id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(USER_NOT_FOUND));
    }

    private RoleType parseRoleType(String roleValue) {
        try {
            return RoleType.valueOf(roleValue.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new UserOperationNotAllowedException("Unsupported role: " + roleValue);
        }
    }
}