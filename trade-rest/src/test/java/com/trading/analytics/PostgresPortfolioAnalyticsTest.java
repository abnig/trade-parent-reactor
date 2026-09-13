package com.trading.analytics;

import com.trading.support.PostgresTestDatabase;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Always runs against container-managed PostgreSQL, isolated from application data. */
class PostgresPortfolioAnalyticsTest extends PortfolioAnalyticsTest {
    @DynamicPropertySource static void postgres(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.configure(registry, "analytics_test");
    }
    @Override void resetSchema() {
        jdbc.execute("DROP SCHEMA public CASCADE");
        jdbc.execute("CREATE SCHEMA public");
    }
    @Override String adaptMigration(String sql) { return sql; }
}
