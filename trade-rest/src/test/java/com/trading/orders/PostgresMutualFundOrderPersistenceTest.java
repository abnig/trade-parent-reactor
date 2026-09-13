package com.trading.orders;

import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import com.trading.support.PostgresTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import com.trading.model.MutualFund;
import com.trading.repository.impl.MutualFundRepositoryImpl;
import static org.junit.jupiter.api.Assertions.*;

/** Always runs against container-managed PostgreSQL, using a fresh schema per test. */
class PostgresMutualFundOrderPersistenceTest extends MutualFundOrderPersistenceTest {
    private String schema;
    private DataSource source;

    @Override protected DataSource dataSource() {
        var database = PostgresTestDatabase.dataSource("order_test");
        schema = "order_test_" + UUID.randomUUID().toString().replace("-", "");
        new JdbcTemplate(database).execute("CREATE SCHEMA " + schema);
        var properties = new java.util.Properties();
        properties.setProperty("currentSchema", schema);
        database.setConnectionProperties(properties);
        source = database;
        return source;
    }

    @Override protected String adaptMigration(String sql) { return sql; }

    @Override protected void migrate(String file) throws Exception {
        if (!file.equals("V9__mutual_fund_orders.sql")) {
            super.migrate(file);
            return;
        }
        // Exercise the actual Flyway upgrade from an existing V8 portfolio schema.
        var flyway = Flyway.configure().dataSource(source).defaultSchema(schema)
                .locations("classpath:db/migration").baselineOnMigrate(true).baselineVersion("8").load();
        assertEquals(1, flyway.migrate().migrationsExecuted);
        flyway.validate();
        assertEquals(0, flyway.migrate().migrationsExecuted);
    }

    @AfterEach void removeTestSchema() {
        if (jdbc != null && schema != null) jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
    }

    @Test void existingUnscopedFundRepositoryAlsoPreservesSecurityMetadata() {
        var repository = new MutualFundRepositoryImpl(jdbc);
        var fund = new MutualFund(null, 1L, "Scheme", null, null);
        fund.setIsin("INF123456789");
        fund.setPlan("verbatim plan");
        var saved = repository.save(fund);
        assertEquals("INF123456789", saved.getIsin());
        assertEquals("verbatim plan", saved.getPlan());
        assertEquals("INF123456789", repository.findByBrokerAccountId(1L,
                com.trading.repository.PageRequest.of(0, 20)).getLast().getIsin());
        assertEquals("INF123456789", repository.findAll(
                com.trading.repository.PageRequest.of(0, 20)).getLast().getIsin());
        var updated = repository.update(new MutualFund(saved.getMutualFundId(), 1L, "Renamed", null, null));
        assertEquals("INF123456789", updated.getIsin());
        assertEquals("verbatim plan", updated.getPlan());
    }
}
