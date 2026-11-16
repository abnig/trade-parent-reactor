package com.trading.security;

import com.trading.support.PostgresTestDatabase;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Exercises the same HTTP and repository ownership contracts on disposable PostgreSQL. */
class PostgresSessionAndOwnershipTest extends SessionAndOwnershipTest {
    @DynamicPropertySource static void postgres(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.configure(registry, "ownership_test");
    }

    @Override void resetSchema() {
        jdbc.execute("DROP SCHEMA public CASCADE");
        jdbc.execute("CREATE SCHEMA public");
    }

    @Override String adaptMigration(String sql) { return sql; }
}
