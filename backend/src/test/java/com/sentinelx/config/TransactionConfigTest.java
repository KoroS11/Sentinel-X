package com.sentinelx.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Integration tests for TransactionConfig.
 * 
 * Tests:
 * - Bean presence and configuration
 * - Transactional rollback behavior on exception
 * - Read-only transaction support
 */
@SpringBootTest(properties = "spring.profiles.active=test")
@ActiveProfiles("test")
class TransactionConfigTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        // Create test table for transaction testing
        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS tx_test_table (id BIGINT PRIMARY KEY, label VARCHAR(50))"
        );
    }

    @AfterEach
    void tearDown() {
        // Clean up test table
        jdbcTemplate.execute("DROP TABLE IF EXISTS tx_test_table");
    }

    /**
     * Test 1: Verify TransactionManager bean exists and is registered.
     */
    @Test
    void testTransactionManagerBeanExists() {
        assertThat(applicationContext.containsBean("transactionManager")).isTrue();
        assertThat(transactionManager).isNotNull();
        assertThat(transactionManager).isInstanceOf(PlatformTransactionManager.class);
    }

    /**
     * Test 2: Verify TransactionTemplate bean exists and is registered.
     */
    @Test
    void testTransactionTemplateBeanExists() {
        assertThat(applicationContext.containsBean("transactionTemplate")).isTrue();
        assertThat(transactionTemplate).isNotNull();
        assertThat(transactionTemplate).isInstanceOf(TransactionTemplate.class);
    }

    /**
     * Test 3: Verify write operation rolls back on exception.
     * 
     * Inserts a row within a transaction, then throws an exception.
     * After rollback, the table should be empty.
     */
    @Test
    void testWriteOperationRollsBackOnException() {
        // Verify table is empty at start
        Integer initialCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tx_test_table",
                Integer.class
        );
        assertThat(initialCount).isEqualTo(0);

        // Execute transaction that should rollback
        assertThrows(RuntimeException.class, () ->
                transactionTemplate.executeWithoutResult(status -> {
                    jdbcTemplate.update(
                            "INSERT INTO tx_test_table VALUES (?, ?)",
                            1L,
                            "x"
                    );
                    throw new RuntimeException("forced rollback");
                })
        );

        // Verify rollback occurred - table should still be empty
        Integer finalCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tx_test_table",
                Integer.class
        );
        assertThat(finalCount).isEqualTo(0);
    }

    /**
     * Test 4: Verify read-only transaction completes without error.
     * 
     * Inserts a row, then reads it within a read-only transaction.
     * Read-only transactions should succeed without modification.
     */
    @Test
    void testReadOnlyTransactionCompletesWithoutError() {
        // Set up: Insert a test row
        jdbcTemplate.update(
                "INSERT INTO tx_test_table VALUES (?, ?)",
                1L,
                "test"
        );

        // Execute read-only transaction
        transactionTemplate.setReadOnly(true);
        String result = transactionTemplate.execute(status -> {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM tx_test_table",
                    Integer.class
            );
            assertThat(count).isEqualTo(1);

            String label = jdbcTemplate.queryForObject(
                    "SELECT label FROM tx_test_table WHERE id = ?",
                    String.class,
                    1L
            );
            return label;
        });

        // Verify read-only transaction returned expected result
        assertThat(result).isEqualTo("test");

        // Reset read-only flag for other tests
        transactionTemplate.setReadOnly(false);
    }
}
