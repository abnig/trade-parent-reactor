package com.trading.orders;

import com.trading.support.PostgresTestDatabase;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

class PostgresOrderMetadataMigrationTest {
    private JdbcTemplate jdbc;
    private DataSource source;
    private String schema;

    @BeforeEach void setupV10() throws Exception {
        var database = PostgresTestDatabase.dataSource("order_test");
        schema = "metadata_test_" + UUID.randomUUID().toString().replace("-", "");
        new JdbcTemplate(database).execute("CREATE SCHEMA " + schema);
        var properties = new java.util.Properties();
        properties.setProperty("currentSchema", schema);
        database.setConnectionProperties(properties);
        source = database;
        jdbc = new JdbcTemplate(source);
        for (String file : List.of("V3__application_schema.sql", "V9__mutual_fund_orders.sql", "V10__move_folio_to_mutual_fund.sql")) {
            String sql = new ClassPathResource("db/migration/" + file).getContentAsString(StandardCharsets.UTF_8);
            for (String statement : sql.split(";")) if (!statement.isBlank()) jdbc.execute(statement);
        }
        jdbc.update("INSERT INTO mutual_fund_broker_account(broker_name,account_id) VALUES('Test','001')");
        for (int i = 0; i < 3; i++) {
            jdbc.update("INSERT INTO mutual_fund(broker_account_id,mutual_fund_name,isin,plan) VALUES(1,'Fund','INF123456789','Direct')");
        }
    }

    private void transaction(long fund) {
        jdbc.update("INSERT INTO mutual_fund_txn(mutual_fund_id,amount,txn_date) VALUES(?,100,'2026-01-01')", fund);
    }

    private Flyway migration() {
        return Flyway.configure().dataSource(source).defaultSchema(schema)
                .locations("classpath:db/migration").baselineOnMigrate(true).baselineVersion("10").target("11").load();
    }

    @AfterEach void cleanup() {
        if (jdbc != null) jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
    }

    @Test void transfersMetadataVerbatimWithoutInventingTransactionsAndDropsSourceColumns() {
        transaction(1);
        transaction(2);
        transaction(3);
        String tag = "  {\"tag\": [\"coinandroid\"]}  ";
        for (int i = 0; i < 2; i++) {
            jdbc.update("INSERT INTO mutual_fund_order(mutual_fund_id,mutual_fund_txn_id,status,exchange_order_id,remarks,tag,settlement_id) VALUES(1,1,'PROCESSING','000123','Processing information',?,'000007')", tag);
        }
        jdbc.update("INSERT INTO mutual_fund_order(mutual_fund_id,mutual_fund_txn_id,status,exchange_order_id,tag,settlement_id) VALUES(2,2,'COMPLETE','000123','coinandroidsip','000007')");
        // An order without any metadata can remain unlinked.
        jdbc.update("INSERT INTO mutual_fund_order(mutual_fund_id) VALUES(3)");
        var flyway = migration();
        assertEquals(1, flyway.migrate().migrationsExecuted);
        flyway.validate();
        assertEquals(0, flyway.migrate().migrationsExecuted);
        var first = jdbc.queryForMap("SELECT * FROM mutual_fund_txn WHERE mutual_fund_txn_id=1");
        assertEquals("PROCESSING", first.get("status"));
        assertEquals("000123", first.get("exchange_order_id"));
        assertEquals("Processing information", first.get("remarks"));
        assertEquals(tag, first.get("tag"));
        assertEquals("000007", first.get("settlement_id"));
        assertEquals("coinandroidsip", jdbc.queryForObject("SELECT tag FROM mutual_fund_txn WHERE mutual_fund_txn_id=2", String.class));
        assertNull(jdbc.queryForObject("SELECT status FROM mutual_fund_txn WHERE mutual_fund_txn_id=3", String.class));
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM mutual_fund_txn", Integer.class));
        assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM mutual_fund_order", Integer.class));
        assertEquals(new java.math.BigDecimal("300.00"), jdbc.queryForObject("SELECT SUM(amount) FROM mutual_fund_txn", java.math.BigDecimal.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=? AND table_name='mutual_fund_order' AND column_name IN ('status','exchange_order_id','remarks','tag','settlement_id')", Integer.class, schema));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"status", "exchange_order_id", "remarks", "tag", "settlement_id"})
    void refusesConflictingValuesForEachFieldAndRollsBack(String column) {
        transaction(1);
        // Column names come exclusively from the fixed test annotation above.
        jdbc.update("INSERT INTO mutual_fund_order(mutual_fund_id,mutual_fund_txn_id," + column + ") VALUES(1,1,'first'),(1,1,'second')");
        assertThrows(FlywayException.class, () -> migration().migrate());
        assertEquals(List.of("first", "second"), jdbc.queryForList("SELECT " + column + " FROM mutual_fund_order ORDER BY mutual_fund_order_id", String.class));
        assertRollback();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"status", "exchange_order_id", "remarks", "tag", "settlement_id"})
    void refusesUnlinkedMetadataForEachField(String column) {
        jdbc.update("INSERT INTO mutual_fund_order(mutual_fund_id," + column + ") VALUES(1,'source value')");
        assertThrows(FlywayException.class, () -> migration().migrate());
        assertEquals("source value", jdbc.queryForObject("SELECT " + column + " FROM mutual_fund_order", String.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM mutual_fund_txn", Integer.class));
        assertRollback();
    }

    private void assertRollback() {
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=? AND table_name='mutual_fund_txn' AND column_name IN ('status','exchange_order_id','remarks','tag','settlement_id')", Integer.class, schema));
    }
}
