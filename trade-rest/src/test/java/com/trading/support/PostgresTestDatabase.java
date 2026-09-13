package com.trading.support;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * One disposable container per suite, kept alive for cached Spring contexts.
 * Testcontainers removes the containers when the test JVM exits. Startup errors
 * fail tests rather than disabling them. Application database URLs are never used.
 */
public final class PostgresTestDatabase {
    private static final Set<String> DATABASES = Set.of(
            "analytics_test", "profile_test", "recovery_test", "order_test");
    private static final Map<String, PostgreSQLContainer> CONTAINERS = new HashMap<>();

    private PostgresTestDatabase() { }

    private static synchronized PostgreSQLContainer container(String database) {
        if (!DATABASES.contains(database)) throw new IllegalArgumentException("Unknown test database");
        return CONTAINERS.computeIfAbsent(database, name -> {
            var postgres = new PostgreSQLContainer("postgres:16-alpine").withDatabaseName(name);
            postgres.start();
            return postgres;
        });
    }

    public static DriverManagerDataSource dataSource(String database) {
        var postgres = container(database);
        return new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    public static void configure(DynamicPropertyRegistry registry, String database) {
        var postgres = container(database);
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }
}
