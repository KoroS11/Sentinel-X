package com.sentinelx.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class StartupEnvValidator implements ApplicationRunner {

    private static final List<String> REQUIRED_VARS = List.of(
            "DB_URL",
            "DB_USERNAME",
            "DB_PASSWORD",
            "JWT_SECRET"
    );

    private final Environment environment;

    @Override
    public void run(ApplicationArguments args) {
        // Skip validation in test environments
        String[] activeProfiles = environment.getActiveProfiles();
        for (String profile : activeProfiles) {
            if ("test".equals(profile)) {
                log.debug("Test profile detected. Skipping environment variable validation.");
                return;
            }
        }

        List<String> missingVars = new ArrayList<>();

        for (String name : REQUIRED_VARS) {
            String value = environment.getProperty(name);
            if (value == null || value.isBlank()) {
                log.error("STARTUP FAILURE: Required environment variable '{}' is not set.", name);
                missingVars.add(name);
            }
        }

        if (!missingVars.isEmpty()) {
            throw new IllegalStateException(
                    "Application startup aborted. Missing required environment variables: "
                            + String.join(", ", missingVars)
            );
        }

        log.info("✓ Startup validation passed — all required environment variables are present.");
    }
}
