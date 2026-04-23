package com.sentinelx.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.ExceptionClassifierRetryPolicy;
import org.springframework.retry.policy.NeverRetryPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableRetry
@Slf4j
public class ReadOnlyRetryConfig {

    @Value("${db.retry.read.maxAttempts:3}")
    private int maxAttempts;

    @Value("${db.retry.read.initialIntervalMs:500}")
    private long initialInterval;

    @Value("${db.retry.read.multiplier:2.0}")
    private double multiplier;

    @Value("${db.retry.read.maxIntervalMs:5000}")
    private long maxInterval;

    @Bean
    public RetryTemplate readOnlyRetryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();

        // Configure exception classifier policy
        ExceptionClassifierRetryPolicy retryPolicy = new ExceptionClassifierRetryPolicy();

        // Non-retryable exceptions map to NeverRetryPolicy
        Map<Class<? extends Throwable>, org.springframework.retry.RetryPolicy> policyMap = new HashMap<>();
        policyMap.put(DataIntegrityViolationException.class, new NeverRetryPolicy());
        policyMap.put(BadSqlGrammarException.class, new NeverRetryPolicy());

        retryPolicy.setPolicyMap(policyMap);

        // Default policy for retryable exceptions: DataAccessException, TransientDataAccessException
        SimpleRetryPolicy defaultRetryPolicy = new SimpleRetryPolicy();
        defaultRetryPolicy.setMaxAttempts(maxAttempts);
        retryPolicy.setDefaultPolicy(defaultRetryPolicy);

        retryTemplate.setRetryPolicy(retryPolicy);

        // Configure exponential backoff policy
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(initialInterval);
        backOffPolicy.setMultiplier(multiplier);
        backOffPolicy.setMaxInterval(maxInterval);
        retryTemplate.setBackOffPolicy(backOffPolicy);

        // Register retry listener for logging
        retryTemplate.registerListener(new ReadOnlyRetryListener(maxAttempts));

        return retryTemplate;
    }

    @Slf4j
    private static class ReadOnlyRetryListener implements RetryListener {

        private final int maxAttempts;

        public ReadOnlyRetryListener(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        @Override
        public <T, E extends Throwable> boolean open(RetryContext context, RetryCallback<T, E> callback) {
            return true;
        }

        @Override
        public <T, E extends Throwable> void close(RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {
        }

        @Override
        public <T, E extends Throwable> void onError(RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {
            int attempt = context.getRetryCount() + 1;
            String operationName = context.getAttribute("operationName") != null ?
                    context.getAttribute("operationName").toString() : "UNKNOWN";

            log.warn("Read-only DB retry attempt {} of {} — operation: {}",
                    attempt, maxAttempts, operationName);
        }
    }
}
