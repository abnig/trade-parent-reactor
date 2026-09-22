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

class PostgresFundFolioMigrationTest {
    private JdbcTemplate jdbc;
    private DataSource source;
    private String schema;

    @BeforeEach void setupV9() throws Exception {
        var database = PostgresTestDatabase.dataSource("order_test");
        schema = "folio_test_" + UUID.randomUUID().toString().replace("-", "");
        new JdbcTemplate(database).execute("CREATE SCHEMA " + schema);
        var properties = new java.util.Properties();
        properties.setProperty("currentSchema", schema);
        database.setConnectionProperties(properties);
        source = database;
        jdbc = new JdbcTemplate(source);
        for (String file : List.of("V3__application_schema.sql", "V9__mutual_fund_orders.sql")) {
            String sql = new ClassPathResource("db/migration/" + file).getContentAsString(StandardCharsets.UTF_8);
            for (String statement : sql.split(";")) if (!statement.isBlank()) jdbc.execute(statement);
        }
        jdbc.update("INSERT INTO mutual_fund_broker_account(broker_name,account_id) VALUES('Test','001')");
        for (int i = 0; i < 3; i++) {
            jdbc.update("INSERT INTO mutual_fund(broker_account_id,mutual_fund_name,isin,plan) VALUES(1,'Fund','INF123456789','Direct')");
        }
    }

    private Flyway migration() {
        return Flyway.configure().dataSource(source).defaultSchema(schema)
                .locations("classpath:db/migration").baselineOnMigrate(true).baselineVersion("9").target("10").load();
    }

    @AfterEach void cleanup() {
        if (jdbc != null) jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
    }

    @Test void movesRepeatedFoliosPreservesMetadataAndLeavesMissingFoliosNull() {
        jdbc.update("INSERT INTO mutual_fund_order(mutual_fund_id,folio_number) VALUES(1,'00001234/05'),(1,'00001234/05'),(1,NULL),(2,NULL)");
        var flyway = migration();
        assertEquals(1, flyway.migrate().migrationsExecuted);
        flyway.validate();
        assertEquals(0, flyway.migrate().migrationsExecuted);
        assertEquals("00001234/05", jdbc.queryForObject("SELECT folio_number FROM mutual_fund WHERE mutual_fund_id=1", String.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM mutual_fund WHERE folio_number IS NULL", Integer.class));
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM mutual_fund WHERE isin='INF123456789' AND plan='Direct'", Integer.class));
        assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM mutual_fund_order", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=? AND table_name='mutual_fund_order' AND column_name='folio_number'", Integer.class, schema));
    }

    @Test void conflictingFoliosRollBackWithoutLosingOrderData() {
        jdbc.update("INSERT INTO mutual_fund_order(mutual_fund_id,folio_number) VALUES(1,'001/A'),(1,'001/B')");
        assertThrows(FlywayException.class, () -> migration().migrate());
        assertEquals(List.of("001/A", "001/B"), jdbc.queryForList("SELECT folio_number FROM mutual_fund_order ORDER BY mutual_fund_order_id", String.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=? AND table_name='mutual_fund' AND column_name='folio_number'", Integer.class, schema));
    }
}
