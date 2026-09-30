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
    @Test void unscopedFundRepositoryAcceptsNASourceMetadata() {
        verifyFundSourceMetadata(new MutualFundRepositoryImpl(jdbc));
    }
    @Test void unscopedTransactionRepositoryPreservesMetadata() {
        verifyTransactionMetadata(new com.trading.repository.impl.MutualFundTxnRepositoryImpl(jdbc));
    }
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
        if (!file.equals("V9__mutual_fund_orders.sql") && !file.equals("V10__move_folio_to_mutual_fund.sql")
                && !file.equals("V11__move_order_metadata_to_transactions.sql")
                && !file.equals("V12__unique_broker_account_per_owner.sql")
                && !file.equals("V13__relax_mutual_fund_isin_length.sql")) {
            super.migrate(file);
            return;
        }
        // Exercise the actual Flyway upgrade from an existing V8 portfolio schema.
        var flyway = Flyway.configure().dataSource(source).defaultSchema(schema)
                .locations("classpath:db/migration").baselineOnMigrate(true).baselineVersion("8")
                .target(file.substring(1, file.indexOf("__"))).load();
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
        fund.setFolioNumber("00001234/05");
        var saved = repository.save(fund);
        assertEquals("INF123456789", saved.getIsin());
        assertEquals("verbatim plan", saved.getPlan());
        assertEquals("00001234/05", saved.getFolioNumber());
        assertEquals("INF123456789", repository.findByBrokerAccountId(1L,
                com.trading.repository.PageRequest.of(0, 20)).getLast().getIsin());
        assertEquals("INF123456789", repository.findAll(
                com.trading.repository.PageRequest.of(0, 20)).getLast().getIsin());
        var updated = repository.update(new MutualFund(saved.getMutualFundId(), 1L, "Renamed", null, null));
        assertEquals("INF123456789", updated.getIsin());
        assertEquals("verbatim plan", updated.getPlan());
        assertEquals("00001234/05", updated.getFolioNumber());
        updated.setIsin("");
        updated.setPlan("");
        updated.setFolioNumber("");
        var cleared = repository.update(updated);
        assertNull(cleared.getIsin());
        assertNull(cleared.getPlan());
        assertNull(cleared.getFolioNumber());
    }
}
