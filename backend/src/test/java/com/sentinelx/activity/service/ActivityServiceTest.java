package com.sentinelx.activity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.sentinelx.activity.dto.ActivityResponse;
import com.sentinelx.activity.entity.Activity;
import com.sentinelx.activity.repository.ActivityRepository;
import com.sentinelx.user.entity.Role;
import com.sentinelx.user.entity.RoleType;
import com.sentinelx.user.entity.User;
import com.sentinelx.user.repository.RoleRepository;
import com.sentinelx.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

@DataJpaTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import(ActivityService.class)
class ActivityServiceTest {

    private static final String ACTION_LOGIN = "LOGIN";
    private static final String ENTITY_TYPE_USER = "USER";
    private static final String ENTITY_ID_1 = "1";
    private static final String METADATA_IP = "{\"ip\":\"127.0.0.1\"}";

    @Autowired
    private ActivityService activityService;

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Test
    void logActivitySavesRecordWithCorrectFields() {
        User user = createUser("alice", "alice@example.com");

        activityService.logActivity(user, ACTION_LOGIN, ENTITY_TYPE_USER, ENTITY_ID_1, METADATA_IP);

        Activity saved = activityRepository.findAll().stream().findFirst().orElseThrow();
        assertNotNull(saved.getId());
        assertEquals(user.getId(), saved.getUser().getId());
        assertEquals(ACTION_LOGIN, saved.getAction());
        assertEquals(ENTITY_TYPE_USER, saved.getEntityType());
        assertEquals(ENTITY_ID_1, saved.getEntityId());
        assertEquals(METADATA_IP, saved.getMetadata());
        assertNotNull(saved.getCreatedAt());
    }

    @Test
    void getActivitiesForUserReturnsOnlyThatUsersActivities() {
        User userOne = createUser("bob", "bob@example.com");
        User userTwo = createUser("charlie", "charlie@example.com");

        activityService.logActivity(userOne, "CREATE", "ALERT", "A-1", "meta-1");
        activityService.logActivity(userOne, "UPDATE", "ALERT", "A-2", "meta-2");
        activityService.logActivity(userTwo, "DELETE", "ALERT", "A-3", "meta-3");

        Page<ActivityResponse> page = activityService.getActivitiesForUser(userOne, PageRequest.of(0, 10));

        assertEquals(2, page.getTotalElements());
        page.getContent().forEach(activity -> assertEquals(userOne.getId(), activity.userId()));
    }

    private User createUser(String username, String email) {
        Role role = roleRepository.findByName(RoleType.EMPLOYEE).orElseGet(() -> {
            Role newRole = new Role();
            newRole.setName(RoleType.EMPLOYEE);
            return roleRepository.save(newRole);
        });

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash("hashed-password");
        user.setRole(role);
        user.setActive(true);
        return userRepository.save(user);
    }
}
