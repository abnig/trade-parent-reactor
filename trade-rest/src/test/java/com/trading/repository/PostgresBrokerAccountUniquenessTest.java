package com.trading.repository;

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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

class PostgresBrokerAccountUniquenessTest {
    private JdbcTemplate jdbc;
    private DataSource source;
    private String schema;

    @BeforeEach void setupV11() throws Exception {
        var database = PostgresTestDatabase.dataSource("order_test");
        schema = "broker_unique_" + UUID.randomUUID().toString().replace("-", "");
        new JdbcTemplate(database).execute("CREATE SCHEMA " + schema);
        var properties = new java.util.Properties();
        properties.setProperty("currentSchema", schema);
        database.setConnectionProperties(properties);
        source = database;
        jdbc = new JdbcTemplate(source);
        for (String file : List.of("V3__application_schema.sql", "V5__create_app_user.sql",
                "V6__portfolio_ownership.sql", "V7__password_recovery.sql", "V8__user_profile_hint.sql",
                "V9__mutual_fund_orders.sql", "V10__move_folio_to_mutual_fund.sql",
                "V11__move_order_metadata_to_transactions.sql")) {
            String sql = new ClassPathResource("db/migration/" + file).getContentAsString(StandardCharsets.UTF_8);
            for (String statement : sql.split(";")) if (!statement.isBlank()) jdbc.execute(statement);
        }
        jdbc.update("INSERT INTO users(username,email,password) VALUES('alice','alice@example.com','test-only'),('bob','bob@example.com','test-only')");
    }

    private Flyway migration() {
        return Flyway.configure().dataSource(source).defaultSchema(schema)
                .locations("classpath:db/migration").baselineOnMigrate(true).baselineVersion("11").target("12").load();
    }

    private long account(String broker, String account, Long owner) {
        return jdbc.queryForObject("INSERT INTO mutual_fund_broker_account(broker_name,account_id,owner_user_id) VALUES(?,?,?) RETURNING broker_account_id",
                Long.class, broker, account, owner);
    }

    @AfterEach void cleanup() {
        if (jdbc != null) jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
    }

    @Test void rejectsDuplicateInsertsUpdatesAndOwnerReassignment() {
        long first = account("Zerodha", "001", 1L);
        var flyway = migration();
        assertEquals(1, flyway.migrate().migrationsExecuted);
        flyway.validate();
        assertEquals(0, flyway.migrate().migrationsExecuted);
        assertThrows(DuplicateKeyException.class, () -> account("Zerodha", "001", 1L));
        long second = account("Zerodha", "002", 1L);
        assertThrows(DuplicateKeyException.class, () -> jdbc.update(
                "UPDATE mutual_fund_broker_account SET account_id='001' WHERE broker_account_id=?", second));
        long otherOwner = account("Zerodha", "001", 2L);
        assertThrows(DuplicateKeyException.class, () -> jdbc.update(
                "UPDATE mutual_fund_broker_account SET owner_user_id=1 WHERE broker_account_id=?", otherOwner));
        assertEquals("002", jdbc.queryForObject("SELECT account_id FROM mutual_fund_broker_account WHERE broker_account_id=?", String.class, second));
        assertEquals(1L, jdbc.queryForObject("SELECT owner_user_id FROM mutual_fund_broker_account WHERE broker_account_id=?", Long.class, first));
    }

    @Test void allowsDistinctCompositeKeysAndPreservesNullOwnerSemantics() {
        migration().migrate();
        account("Zerodha", "001", 1L);
        account("Zerodha", "001", 2L);
        account("Other Broker", "001", 1L);
        account("Zerodha", "002", 1L);
        account("Zerodha", "001", null);
        account("Zerodha", "001", null);
        assertEquals(6, jdbc.queryForObject("SELECT COUNT(*) FROM mutual_fund_broker_account", Integer.class));
    }

    @Test void existingDuplicatesAbortMigrationWithoutDeletingAccountsOrFunds() {
        long first = account("Zerodha", "001", 1L);
        account("Zerodha", "001", 1L);
        jdbc.update("INSERT INTO mutual_fund(broker_account_id,mutual_fund_name) VALUES(?,'Existing fund')", first);
        assertThrows(FlywayException.class, () -> migration().migrate());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM mutual_fund_broker_account", Integer.class));
        assertEquals(first, jdbc.queryForObject("SELECT broker_account_id FROM mutual_fund", Long.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema=? AND constraint_name='uq_broker_account_name_account_owner'", Integer.class, schema));
    }
}
