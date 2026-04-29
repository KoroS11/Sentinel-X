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

        // Configure simple retry policy with exponential backoff
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(maxAttempts);
        retryTemplate.setRetryPolicy(retryPolicy);

        // Configure exponential backoff policy
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(initialInterval);
        backOffPolicy.setMultiplier(multiplier);
        backOffPolicy.setMaxInterval(maxInterval);
        retryTemplate.setBackOffPolicy(backOffPolicy);

        return retryTemplate;
    }
}
