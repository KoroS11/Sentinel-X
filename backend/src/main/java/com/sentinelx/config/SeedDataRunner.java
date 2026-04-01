package com.sentinelx.config;

import com.sentinelx.user.service.RoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeedDataRunner implements CommandLineRunner {

    private final RoleService roleService;

    @Override
    public void run(String... args) {
        roleService.ensureDefaultRoles();
        log.info("Default roles verified");
    }
}
