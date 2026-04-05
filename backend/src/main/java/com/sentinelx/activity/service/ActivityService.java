package com.sentinelx.activity.service;

import com.sentinelx.activity.dto.ActivityResponse;
import com.sentinelx.activity.entity.Activity;
import com.sentinelx.activity.repository.ActivityRepository;
import com.sentinelx.user.entity.User;
import com.sentinelx.user.exception.ResourceNotFoundException;
import com.sentinelx.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActivityService {

    private static final String USER_NOT_FOUND = "User not found.";
    private static final String ACTIVITY_NOT_FOUND = "Activity not found.";

    private final ActivityRepository activityRepository;
    private final UserRepository userRepository;

    public ActivityService(ActivityRepository activityRepository, UserRepository userRepository) {
        this.activityRepository = activityRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void logActivity(
        User user,
        String action,
        String entityType,
        String entityId,
        String metadata
    ) {
        Activity activity = new Activity();
        activity.setUser(user);
        activity.setAction(action);
        activity.setEntityType(entityType);
        activity.setEntityId(entityId);
        activity.setMetadata(metadata);
        activityRepository.save(activity);
    }

    @Transactional(readOnly = true)
    public Page<ActivityResponse> getActivitiesForUser(User user, Pageable pageable) {
        return activityRepository.findAllByUser(user, pageable).map(ActivityResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public Page<ActivityResponse> getActivitiesByEntity(String entityType, Pageable pageable) {
        return activityRepository.findAllByEntityType(entityType, pageable).map(ActivityResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public Page<ActivityResponse> getActivitiesByUserId(Long userId, Pageable pageable) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException(USER_NOT_FOUND);
        }

        return activityRepository.findAllByUserId(userId, pageable).map(ActivityResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public ActivityResponse getActivityById(Long id) {
        Activity activity = activityRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(ACTIVITY_NOT_FOUND));
        return ActivityResponse.fromEntity(activity);
    }
}
