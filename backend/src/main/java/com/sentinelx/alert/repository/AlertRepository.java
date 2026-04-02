package com.sentinelx.alert.repository;

import com.sentinelx.alert.entity.Alert;
import com.sentinelx.alert.entity.AlertStatus;
import com.sentinelx.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertRepository extends JpaRepository<Alert, Long> {
    Page<Alert> findAllByUser(User user, Pageable pageable);

    Page<Alert> findAllByStatus(AlertStatus status, Pageable pageable);

    Page<Alert> findAllByUserAndStatus(User user, AlertStatus status, Pageable pageable);
}
