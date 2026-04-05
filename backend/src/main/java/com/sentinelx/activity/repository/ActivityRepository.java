package com.sentinelx.activity.repository;

import com.sentinelx.activity.entity.Activity;
import com.sentinelx.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityRepository extends JpaRepository<Activity, Long> {
    Page<Activity> findAllByUser(User user, Pageable pageable);

    Page<Activity> findAllByUserId(Long userId, Pageable pageable);

    Page<Activity> findAllByEntityType(String entityType, Pageable pageable);
}
