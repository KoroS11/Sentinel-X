package com.sentinelx.user.repository;

import com.sentinelx.user.entity.Role;
import com.sentinelx.user.entity.RoleType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Long> {
    boolean existsByName(RoleType name);

    Optional<Role> findByName(RoleType name);
}
