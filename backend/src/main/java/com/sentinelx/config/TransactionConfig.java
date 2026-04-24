package com.sentinelx.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;

/**
 * Transaction management configuration for database operations.
 * 
 * Configures:
 * - PlatformTransactionManager with default timeout settings
 * - TransactionTemplate for programmatic transaction control
 * - Default transaction timeout via property: db.transaction.defaultTimeoutSeconds
 */
@Configuration
@EnableTransactionManagement
@Slf4j
public class TransactionConfig {

    @Value("${db.transaction.defaultTimeoutSeconds:30}")
    private int defaultTimeoutSeconds;

    /**
     * Creates and configures the PlatformTransactionManager.
     * 
     * @param dataSource the DataSource used for transaction management
     * @return configured PlatformTransactionManager with default timeout
     */
    @Bean
    public PlatformTransactionManager transactionManager(DataSource dataSource) {
        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
        transactionManager.setDefaultTimeout(defaultTimeoutSeconds);
        log.info("Transaction manager configured: defaultTimeout={}s", defaultTimeoutSeconds);
        return transactionManager;
    }

    /**
     * Creates and configures the TransactionTemplate.
     * 
     * @param transactionManager the PlatformTransactionManager to use
     * @return configured TransactionTemplate
     */
    @Bean
    public TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setTimeout(defaultTimeoutSeconds);
        return template;
    }
}
