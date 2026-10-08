package com.trading.coin;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import com.trading.repository.impl.JdbcCoinImportRepository;

/** Disposable PostgreSQL only; never reads application DB configuration. */
final class CoinPostgresFixture implements AutoCloseable {
    private static final class Database {
        static final PostgreSQLContainer POSTGRES = start();
        private static PostgreSQLContainer start() {
            var postgres = new PostgreSQLContainer("postgres:16-alpine").withDatabaseName("coin_test");
            postgres.start();
            return postgres;
        }
    }
    final String schema = "coin_" + UUID.randomUUID().toString().replace("-", "");
    final DriverManagerDataSource source;
    final JdbcTemplate jdbc;
    final JdbcTransactionManager manager;
    final JdbcCoinImportRepository repository;
    final TransactionTemplate transaction;
    CoinPostgresFixture() throws Exception {
        var postgres = Database.POSTGRES;
        var admin = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        var catalog = new JdbcTemplate(admin);
        catalog.execute("CREATE EXTENSION IF NOT EXISTS btree_gist WITH SCHEMA public");
        catalog.execute("CREATE SCHEMA " + schema);
        source = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        var properties = new java.util.Properties();
        properties.setProperty("currentSchema", schema + ",public");
        source.setConnectionProperties(properties);
        jdbc = new JdbcTemplate(source);
        for (String migration : new String[]{"V2__spring_batch_schema.sql", "V3__application_schema.sql",
                "V5__create_app_user.sql", "V6__portfolio_ownership.sql", "V7__password_recovery.sql", "V8__user_profile_hint.sql",
                "V9__mutual_fund_orders.sql", "V10__move_folio_to_mutual_fund.sql", "V11__move_order_metadata_to_transactions.sql",
                "V12__unique_broker_account_per_owner.sql", "V13__relax_mutual_fund_isin_length.sql"}) {
            try (var stream = new ClassPathResource("db/migration/" + migration).getInputStream()) {
                jdbc.execute(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        var flyway = Flyway.configure().dataSource(source).defaultSchema(schema).locations("classpath:db/migration")
                .baselineOnMigrate(true).baselineVersion("13").target("14").load();
        if (flyway.migrate().migrationsExecuted != 1) throw new IllegalStateException("V14 was not exercised");
        flyway.validate();
        if (flyway.migrate().migrationsExecuted != 0) throw new IllegalStateException("Migration is not repeatable");
        jdbc.update("INSERT INTO users(id,username,email,password) VALUES(1,'owner1','one@example.invalid','unused'),(2,'owner2','two@example.invalid','unused')");
        jdbc.update("INSERT INTO mutual_fund_broker_account(broker_account_id,broker_name,account_id,owner_user_id) VALUES(1,'Zerodha','CLIENT000',1),(2,'Zerodha','CLIENT000',2)");
        jdbc.update("INSERT INTO mutual_fund(mutual_fund_id,broker_account_id,mutual_fund_name,isin,folio_number) OVERRIDING SYSTEM VALUE VALUES(1,1,'Test Fund','INFTEST00001','000123/01'),(2,2,'Test Fund','INFTEST00001','000123/01'),(3,1,'Second Fund','INFTEST00002','000456/01')");
        manager = new JdbcTransactionManager(source);
        transaction = new TransactionTemplate(manager);
        repository = new JdbcCoinImportRepository(jdbc, manager);
    }
    long count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class); }
    @Override public void close() { jdbc.execute("DROP SCHEMA " + schema + " CASCADE"); }
}
