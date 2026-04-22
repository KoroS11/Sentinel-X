package com.sentinelx.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

@ExtendWith(MockitoExtension.class)
class StartupEnvValidatorTest {

    @Mock
    private Environment environment;

    private StartupEnvValidator validator;

    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        validator = new StartupEnvValidator(environment);

        Logger logger = (Logger) LoggerFactory.getLogger(StartupEnvValidator.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
    }

    @Test
    void testAllVarsPresent_noExceptionThrown() {
        when(environment.getProperty("DB_URL")).thenReturn("test-value");
        when(environment.getProperty("DB_USERNAME")).thenReturn("test-value");
        when(environment.getProperty("DB_PASSWORD")).thenReturn("test-value");
        when(environment.getProperty("JWT_SECRET")).thenReturn("test-value");

        assertDoesNotThrow(() -> validator.run(null));
    }

    @Test
    void testMissingDbUrl_throwsIllegalStateException() {
        when(environment.getProperty("DB_URL")).thenReturn(null);
        when(environment.getProperty("DB_USERNAME")).thenReturn("test-value");
        when(environment.getProperty("DB_PASSWORD")).thenReturn("test-value");
        when(environment.getProperty("JWT_SECRET")).thenReturn("test-value");

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> validator.run(null)
        );

        assertTrue(ex.getMessage().contains("DB_URL"));
    }

    @Test
    void testMultipleMissingVars_messageContainsAllNames() {
        when(environment.getProperty("DB_URL")).thenReturn(null);
        when(environment.getProperty("DB_USERNAME")).thenReturn("test-value");
        when(environment.getProperty("DB_PASSWORD")).thenReturn("test-value");
        when(environment.getProperty("JWT_SECRET")).thenReturn(null);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> validator.run(null)
        );

        assertTrue(ex.getMessage().contains("DB_URL"));
        assertTrue(ex.getMessage().contains("JWT_SECRET"));
    }

    @Test
    void testNoActualSecretValuesAppearInOutput() {
        String sensitiveValue = "should-not-appear-in-logs";

        when(environment.getProperty("DB_URL")).thenReturn(sensitiveValue);
        when(environment.getProperty("DB_USERNAME")).thenReturn(sensitiveValue);
        when(environment.getProperty("DB_PASSWORD")).thenReturn(sensitiveValue);
        when(environment.getProperty("JWT_SECRET")).thenReturn(sensitiveValue);

        assertDoesNotThrow(() -> validator.run(null));

        List<ILoggingEvent> logEvents = logAppender.list;
        for (ILoggingEvent event : logEvents) {
            assertFalse(
                    event.getFormattedMessage().contains(sensitiveValue),
                    "Log output must never contain actual secret values"
            );
        }
    }
}
