package com.sentinelx.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for SslConfigValidator bean initialization and validation logic.
 * 
 * Uses ApplicationContextRunner to test various SSL configuration scenarios
 * without spinning up a full application context.
 */
public class SslConfigValidatorTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SslConfigValidator.class);

    /**
     * Test that SslConfigValidator bean is NOT created when db.ssl.enabled is not set.
     * The @ConditionalOnProperty annotation should prevent bean instantiation.
     */
    @Test
    public void testSslDisabled_beanNotActivated() {
        contextRunner
                .run(context -> {
                    // Bean should not be present when db.ssl.enabled is not true
                    assertThat(context).doesNotHaveBean(SslConfigValidator.class);
                });
    }

    /**
     * Test that context loads successfully when SSL is enabled but using "require" mode.
     * In "require" mode, no certificate file validation is performed.
     */
    @Test
    public void testSslRequireMode_contextLoadsWithoutCert() {
        contextRunner
                .withPropertyValues(
                        "db.ssl.enabled=true",
                        "db.ssl.mode=require"
                )
                .run(context -> {
                    // Bean should be present
                    assertThat(context).hasSingleBean(SslConfigValidator.class);
                    // Context should load successfully
                    assertThat(context).hasNotFailed();
                });
    }

    /**
     * Test that context fails to load when SSL is enabled with "verify-full" mode
     * but db.ssl.rootCertPath is blank or missing.
     * Should throw IllegalStateException with message about required rootCertPath.
     */
    @Test
    public void testSslVerifyFull_blankCertPath_failsWithReadableMessage() {
        contextRunner
                .withPropertyValues(
                        "db.ssl.enabled=true",
                        "db.ssl.mode=verify-full",
                        "db.ssl.rootCertPath="
                )
                .run(context -> {
                    // Context should have failed
                    assertThat(context).hasFailed();
                    // Verify the exception message is readable and contains expected text
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("rootCertPath is required");
                });
    }

    /**
     * Test that context fails to load when SSL is enabled with "verify-full" mode
     * and db.ssl.rootCertPath points to a non-existent file.
     * Should throw IllegalStateException with message about file not found.
     */
    @Test
    public void testSslVerifyFull_missingCertFile_failsWithReadableMessage() {
        contextRunner
                .withPropertyValues(
                        "db.ssl.enabled=true",
                        "db.ssl.mode=verify-full",
                        "db.ssl.rootCertPath=/nonexistent/path/to/cert.pem"
                )
                .run(context -> {
                    // Context should have failed
                    assertThat(context).hasFailed();
                    // Verify the exception message is readable and contains expected text
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("not found at path");
                });
    }

    /**
     * Test that context loads successfully when SSL is enabled with "verify-ca" mode
     * but no certificate file is provided (since verify-ca is less strict than verify-full).
     * This test ensures "verify-ca" mode validation works correctly.
     */
    @Test
    public void testSslVerifyCa_blankCertPath_failsWithReadableMessage() {
        contextRunner
                .withPropertyValues(
                        "db.ssl.enabled=true",
                        "db.ssl.mode=verify-ca",
                        "db.ssl.rootCertPath="
                )
                .run(context -> {
                    // Context should have failed (verify-ca also requires certificate path)
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("rootCertPath is required");
                });
    }
}
