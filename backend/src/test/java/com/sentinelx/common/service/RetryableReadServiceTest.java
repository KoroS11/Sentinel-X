package com.sentinelx.common.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.retry.backoff.NoBackOffPolicy;
import org.springframework.retry.policy.ExceptionClassifierRetryPolicy;
import org.springframework.retry.policy.NeverRetryPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for RetryableReadService with REAL RetryTemplate.
 * 
 * Tests retry behavior for read-only database operations:
 * - Successful operations return immediately
 * - Transient failures are retried
 * - Non-transient failures are not retried
 * - All attempts exhausted propagates exception
 */
@ExtendWith(MockitoExtension.class)
class RetryableReadServiceTest {

    @InjectMocks
    private RetryableReadService retryableReadService;

    private RetryTemplate realRetryTemplate;

    @Mock
    private Callable<String> mockCallable;

    @BeforeEach
    void setUp() {
        // Configure REAL RetryTemplate with maxAttempts=3, no back-off
        realRetryTemplate = new RetryTemplate();

        // Configure exception classifier policy
        ExceptionClassifierRetryPolicy retryPolicy = new ExceptionClassifierRetryPolicy();

        // Non-retryable exceptions
        Map<Class<? extends Throwable>, org.springframework.retry.RetryPolicy> policyMap = new HashMap<>();
        policyMap.put(DataIntegrityViolationException.class, new NeverRetryPolicy());

        // Default policy: retry transient failures up to 3 times
        SimpleRetryPolicy defaultRetryPolicy = new SimpleRetryPolicy();
        defaultRetryPolicy.setMaxAttempts(3);

        retryPolicy.setExceptionClassifier(throwable -> {
            for (Map.Entry<Class<? extends Throwable>, org.springframework.retry.RetryPolicy> entry : policyMap.entrySet()) {
                if (entry.getKey().isAssignableFrom(throwable.getClass())) {
                    return entry.getValue();
                }
            }
            return defaultRetryPolicy;
        });

        realRetryTemplate.setRetryPolicy(retryPolicy);

        // No back-off delay for fast testing
        realRetryTemplate.setBackOffPolicy(new NoBackOffPolicy());

        // Inject the real RetryTemplate
        ReflectionTestUtils.setField(retryableReadService, "readOnlyRetryTemplate", realRetryTemplate);
    }

    /**
     * Test 1: Successful operation on first attempt.
     * Callable should be invoked exactly once.
     */
    @Test
    void testSuccessOnFirstAttempt_callableInvokedOnce() throws Exception {
        when(mockCallable.call()).thenReturn("success");

        String result = retryableReadService.executeRead("test.operation", mockCallable);

        assertEquals("success", result);
        verify(mockCallable, times(1)).call();
    }

    /**
     * Test 2: Transient failure twice, then success on third attempt.
     * Callable should be invoked exactly 3 times.
     */
    @Test
    void testTransientFailureThenSuccess_retriesAndReturnsValue() throws Exception {
        when(mockCallable.call())
                .thenThrow(new DataAccessException("Transient failure 1") {})
                .thenThrow(new TransientDataAccessException("Transient failure 2") {})
                .thenReturn("success after retries");

        String result = retryableReadService.executeRead("test.operation", mockCallable);

        assertEquals("success after retries", result);
        verify(mockCallable, times(3)).call();
    }

    /**
     * Test 3: All attempts exhausted.
     * Callable always throws DataAccessException.
     * Should be invoked exactly 3 times, then throw exception.
     */
    @Test
    void testAllAttemptsExhausted_exceptionPropagates() throws Exception {
        DataAccessException persistentException = new DataAccessException("Persistent error") {};
        when(mockCallable.call()).thenThrow(persistentException);

        assertThrows(DataAccessException.class, () ->
                retryableReadService.executeRead("test.operation", mockCallable)
        );

        verify(mockCallable, times(3)).call();
    }

    /**
     * Test 4: Non-retryable exception (DataIntegrityViolationException).
     * Should throw immediately without retry.
     * Callable should be invoked exactly once.
     */
    @Test
    void testDataIntegrityViolation_notRetried_throwsImmediately() throws Exception {
        DataIntegrityViolationException nonRetryableException = 
                new DataIntegrityViolationException("Constraint violation");
        when(mockCallable.call()).thenThrow(nonRetryableException);

        assertThrows(DataIntegrityViolationException.class, () ->
                retryableReadService.executeRead("test.operation", mockCallable)
        );

        verify(mockCallable, times(1)).call();
    }
}
