package com.sentinelx.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.sentinelx.auth.exception.DuplicateEmailException;
import com.sentinelx.auth.service.EmailVerificationService;
import com.sentinelx.user.dto.CreateUserRequest;
import com.sentinelx.user.dto.UpdateUserRequest;
import com.sentinelx.user.dto.UserResponse;
import com.sentinelx.user.dto.UserStatusRequest;
import com.sentinelx.user.entity.Role;
import com.sentinelx.user.entity.RoleType;
import com.sentinelx.user.entity.User;
import com.sentinelx.user.entity.UserStatus;
import com.sentinelx.user.exception.ResourceNotFoundException;
import com.sentinelx.user.exception.UserOperationNotAllowedException;
import com.sentinelx.user.repository.RoleRepository;
import com.sentinelx.user.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
class UserServiceTest {

    private static final String TEST_SECRET =
        UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");

    private static final String DEFAULT_PASSWORD = "Password@123";

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private EmailVerificationService emailVerificationService;

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:userservicetest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("jwt.secret", () -> TEST_SECRET);
        registry.add("jwt.expiration-ms", () -> "3600000");
        registry.add("jwt.refresh-expiration-ms", () -> "604800000");
    }

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void getAllUsersReturnsPaginatedResults() {
        createUserWithRole("user1", "user1@example.com", RoleType.EMPLOYEE);
        createUserWithRole("user2", "user2@example.com", RoleType.ANALYST);

        Page<UserResponse> page = userService.getAllUsers(PageRequest.of(0, 1));

        assertEquals(1, page.getSize());
        assertEquals(2, page.getTotalElements());
    }

    @Test
    void getUserByIdThrowsResourceNotFoundExceptionForUnknownId() {
        assertThrows(ResourceNotFoundException.class, () -> userService.getUserById(9999L));
    }

    @Test
    void getUserByIdReturnsUserForKnownId() {
        User user = createUserWithRole("known-user", "known@example.com", RoleType.EMPLOYEE);

        UserResponse response = userService.getUserById(user.getId());

        assertEquals(user.getId(), response.id());
        assertEquals("known@example.com", response.email());
    }

    @Test
    void createUserWithDuplicateEmailThrowsConflictException() {
        createUserWithRole("existing", "duplicate@example.com", RoleType.EMPLOYEE);

        CreateUserRequest request = new CreateUserRequest(
            "new-user",
            "duplicate@example.com",
            DEFAULT_PASSWORD,
            RoleType.EMPLOYEE.name()
        );

        assertThrows(DuplicateEmailException.class, () -> userService.createUser(request));
    }

    @Test
    void createUserValidRequestSavesUserAndSendsVerificationEmail() {
        CreateUserRequest request = new CreateUserRequest(
            "created-user",
            "created@example.com",
            DEFAULT_PASSWORD,
            RoleType.EMPLOYEE.name()
        );

        UserResponse response = userService.createUser(request);

        User saved = userRepository.findById(response.id()).orElseThrow();
        assertEquals("created-user", saved.getUsername());
        assertEquals("created@example.com", saved.getEmail());
        assertTrue(passwordEncoder.matches(DEFAULT_PASSWORD, saved.getPasswordHash()));
        verify(emailVerificationService, times(1)).sendVerification(any(User.class));
    }

    @Test
    void updateUserOnlyModifiesProvidedFieldsLeavesOthersUnchanged() {
        User existing = createUserWithRole("before-name", "before@example.com", RoleType.EMPLOYEE);
        UserStatus originalStatus = existing.getStatus();
        String originalEmail = existing.getEmail();

        UpdateUserRequest request = new UpdateUserRequest("after-name", null);
        UserResponse response = userService.updateUser(existing.getId(), request);

        User updated = userRepository.findById(response.id()).orElseThrow();
        assertEquals("after-name", updated.getUsername());
        assertEquals(originalEmail, updated.getEmail());
        assertEquals(originalStatus, updated.getStatus());
    }

    @Test
    void updateUserWithUnknownIdThrowsResourceNotFoundException() {
        UpdateUserRequest request = new UpdateUserRequest("updated", "updated@example.com");

        assertThrows(ResourceNotFoundException.class, () -> userService.updateUser(9999L, request));
    }

    @Test
    void deleteUserOnAdminRoleThrowsMeaningfulException() {
        User admin = createUserWithRole("admin-user", "admin-user@example.com", RoleType.ADMIN);

        UserOperationNotAllowedException exception = assertThrows(
            UserOperationNotAllowedException.class,
            () -> userService.deleteUser(admin.getId())
        );

        assertTrue(exception.getMessage().toLowerCase().contains("admin"));
    }

    @Test
    void deleteUserOnNonAdminRoleDeletesUser() {
        User employee = createUserWithRole("employee-del", "employee-del@example.com", RoleType.EMPLOYEE);

        userService.deleteUser(employee.getId());

        assertTrue(userRepository.findById(employee.getId()).isEmpty());
    }

    @Test
    void updateUserStatusChangesStatusCorrectly() {
        User user = createUserWithRole("status-user", "status@example.com", RoleType.EMPLOYEE);

        UserResponse response = userService.updateUserStatus(
            user.getId(),
            new UserStatusRequest(UserStatus.SUSPENDED)
        );

        assertEquals(UserStatus.SUSPENDED, response.status());
        User updated = userRepository.findById(user.getId()).orElseThrow();
        assertEquals(UserStatus.SUSPENDED, updated.getStatus());
    }

    private User createUserWithRole(String username, String email, RoleType roleType) {
        Role role = roleRepository.findByName(roleType).orElseGet(() -> {
            Role createdRole = new Role();
            createdRole.setName(roleType);
            return roleRepository.save(createdRole);
        });

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(DEFAULT_PASSWORD));
        user.setRole(role);
        user.setActive(true);
        user.setEmailVerified(true);
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.save(user);
    }
}