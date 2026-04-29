package com.sentinelx.common.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.Callable;

/**
 * Service for executing read-only database operations with retry logic.
 * 
 * Delegates to RetryTemplate for transient failure handling with exponential backoff.
 * Provides a convenient wrapper for dashboard JDBC queries.
 */
@Service
@Slf4j
public class RetryableReadService {

    @Autowired
    private RetryTemplate readOnlyRetryTemplate;

    /**
     * Executes a read-only database operation with automatic retry on transient failures.
     * 
     * @param operationName descriptive name for logging (e.g., "dashboard.getActivityCountByUser")
     * @param operation callable wrapping the database query
     * @param <T> return type of the operation
     * @return result from the callable
     * @throws Exception if all retry attempts fail or if non-retryable exception occurs
     */
    public <T> T executeRead(String operationName, Callable<T> operation) throws Exception {
        return readOnlyRetryTemplate.execute(context -> {
            context.setAttribute("operationName", operationName);
            return operation.call();
        });
    }
}
