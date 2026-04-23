package com.sentinelx.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;

/**
 * JDBC configuration for Spring Boot application.
 * 
 * Provides NamedParameterJdbcTemplate bean for JDBC operations with named parameters.
 */
@Configuration
public class JdbcConfig {

    /**
     * Creates and configures a NamedParameterJdbcTemplate bean.
     * 
     * @param dataSource the data source
     * @return configured NamedParameterJdbcTemplate
     */
    @Bean
    public NamedParameterJdbcTemplate namedParameterJdbcTemplate(DataSource dataSource) {
        return new NamedParameterJdbcTemplate(dataSource);
    }
}
