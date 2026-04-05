package com.sentinelx.alert.repository;

import com.sentinelx.alert.entity.Alert;
import com.sentinelx.alert.entity.AlertSeverity;
import com.sentinelx.alert.entity.AlertStatus;
import com.sentinelx.user.entity.User;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AlertRepository extends JpaRepository<Alert, Long> {
    Page<Alert> findAllByUser(User user, Pageable pageable);

    Page<Alert> findAllByStatus(AlertStatus status, Pageable pageable);

    Page<Alert> findAllByUserAndStatus(User user, AlertStatus status, Pageable pageable);

    @Query("select count(a) from Alert a where a.user = :user and a.status = :status")
    long countByUserAndStatus(@Param("user") User user, @Param("status") AlertStatus status);

    @Query("select count(a) from Alert a where a.user = :user and a.severity = :severity")
    long countByUserAndSeverity(@Param("user") User user, @Param("severity") AlertSeverity severity);

    @Query("select count(a) from Alert a where a.status = :status")
    long countByStatusAggregated(@Param("status") AlertStatus status);

    @Query("select a.status, count(a) from Alert a group by a.status")
    List<Object[]> countByStatusGrouped();

    @Query("select a.severity, count(a) from Alert a group by a.severity")
    List<Object[]> countBySeverityGrouped();
}
