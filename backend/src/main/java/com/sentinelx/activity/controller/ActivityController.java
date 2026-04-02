package com.sentinelx.activity.controller;

import com.sentinelx.activity.dto.ActivityResponse;
import com.sentinelx.activity.service.ActivityService;
import com.sentinelx.auth.exception.InvalidCredentialsException;
import com.sentinelx.user.entity.User;
import com.sentinelx.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/activities")
public class ActivityController {

    private final ActivityService activityService;
    private final UserRepository userRepository;

    public ActivityController(ActivityService activityService, UserRepository userRepository) {
        this.activityService = activityService;
        this.userRepository = userRepository;
    }

    @GetMapping("/me")
    public Page<ActivityResponse> getMyActivities(Authentication authentication, Pageable pageable) {
        User user = resolveCurrentUser(authentication);
        return activityService.getActivitiesForUser(user, pageable);
    }

    @GetMapping("/entity/{entityType}")
    public Page<ActivityResponse> getActivitiesByEntity(
        @PathVariable String entityType,
        Pageable pageable
    ) {
        return activityService.getActivitiesByEntity(entityType, pageable);
    }

    private User resolveCurrentUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new InvalidCredentialsException("Authentication is required.");
        }

        return userRepository.findByUsername(authentication.getName())
            .orElseThrow(() -> new InvalidCredentialsException("Invalid user context."));
    }
}
