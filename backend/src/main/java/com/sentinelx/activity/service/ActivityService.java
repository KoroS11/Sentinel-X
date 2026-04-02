package com.sentinelx.activity.service;

import com.sentinelx.activity.dto.ActivityResponse;
import com.sentinelx.activity.entity.Activity;
import com.sentinelx.activity.repository.ActivityRepository;
import com.sentinelx.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActivityService {

    private final ActivityRepository activityRepository;

    public ActivityService(ActivityRepository activityRepository) {
        this.activityRepository = activityRepository;
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
}
