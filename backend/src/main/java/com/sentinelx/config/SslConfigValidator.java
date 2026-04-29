package com.sentinelx.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Validates SSL configuration for JDBC connections.
 * 
 * Active only when db.ssl.enabled=true property is set.
 * Validates that required SSL certificates exist when using verify-full or verify-ca modes.
 */
@Component
@ConditionalOnProperty(name = "db.ssl.enabled", havingValue = "true")
public class SslConfigValidator implements InitializingBean {

    private static final Logger logger = LoggerFactory.getLogger(SslConfigValidator.class);

    private final Environment environment;
    private String sslMode;
    private String rootCertPath;

    public SslConfigValidator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        // Read configuration from environment
        sslMode = environment.getProperty("db.ssl.mode", "require");
        rootCertPath = environment.getProperty("db.ssl.rootCertPath", "");

        // Validate SSL configuration for verify-full and verify-ca modes
        if ("verify-full".equals(sslMode) || "verify-ca".equals(sslMode)) {
            validateRootCertificate();
        }

        // Log success
        logger.info("SSL configuration validated: mode={}", sslMode);
    }

    /**
     * Validates that the SSL root certificate file exists when required by the SSL mode.
     * 
     * @throws IllegalStateException if rootCertPath is blank or if the certificate file does not exist
     */
    private void validateRootCertificate() throws IllegalStateException {
        if (rootCertPath == null || rootCertPath.isBlank()) {
            logger.error("db.ssl.rootCertPath is required when db.ssl.mode is {}", sslMode);
            throw new IllegalStateException(
                    "db.ssl.rootCertPath is required when db.ssl.mode is " + sslMode);
        }

        if (!Files.exists(Path.of(rootCertPath))) {
            logger.error("SSL root cert file not found at path: {}", rootCertPath);
            throw new IllegalStateException(
                    "SSL root cert file not found at path: " + rootCertPath);
        }
    }
}
