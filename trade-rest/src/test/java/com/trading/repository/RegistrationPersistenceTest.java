package com.trading.repository;

import com.trading.repository.impl.UserRegistrationRepositoryImpl;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import static org.junit.jupiter.api.Assertions.*;

class RegistrationPersistenceTest {
    @org.springframework.boot.test.context.TestConfiguration
    @EnableTransactionManagement
    static class Config {
        @Bean DriverManagerDataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:registration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        }
        @Bean JdbcTemplate jdbc(DriverManagerDataSource ds) { return new JdbcTemplate(ds); }
        @Bean DataSourceTransactionManager transactionManager(DriverManagerDataSource ds) {
            return new DataSourceTransactionManager(ds);
        }
        @Bean UserRegistrationRepository repository(JdbcTemplate jdbc) {
            return new UserRegistrationRepositoryImpl(jdbc);
        }
    }

    @Test void persistsProfileAndRoleAndRollsBackOnFailure() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var jdbc = context.getBean(JdbcTemplate.class);
            jdbc.execute("DROP ALL OBJECTS");
            jdbc.execute("CREATE TABLE roles (id SERIAL PRIMARY KEY, name VARCHAR(50) UNIQUE NOT NULL)");
            jdbc.execute("CREATE TABLE users (id BIGSERIAL PRIMARY KEY, username VARCHAR(50) UNIQUE, email VARCHAR(100) UNIQUE, password VARCHAR(100))");
            jdbc.execute("CREATE TABLE user_details (user_id BIGINT PRIMARY KEY REFERENCES users(id), first_name VARCHAR(50), last_name VARCHAR(50), phone_number VARCHAR(20), avatar_url VARCHAR(255))");
            jdbc.execute("CREATE TABLE user_roles (user_id BIGINT REFERENCES users(id), role_id INT REFERENCES roles(id), PRIMARY KEY(user_id, role_id))");
            jdbc.update("INSERT INTO roles(name) VALUES ('ROLE_USER')");
            var repository = context.getBean(UserRegistrationRepository.class);
            long id = repository.register("alice", "alice@example.com", "hashed", "Alice", null, null, null);
            assertEquals("Alice", jdbc.queryForObject("SELECT first_name FROM user_details WHERE user_id = ?", String.class, id));
            assertEquals("ROLE_USER", jdbc.queryForObject("SELECT r.name FROM roles r JOIN user_roles ur ON r.id=ur.role_id WHERE ur.user_id=?", String.class, id));
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> repository.register("bob", "bob@example.com", "hashed", "x".repeat(51), null, null, null));
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM users WHERE username='bob'", Integer.class));
            assertThrows(org.springframework.dao.DuplicateKeyException.class,
                () -> repository.register("alice", "other@example.com", "hashed", null, null, null, null));
            assertThrows(org.springframework.dao.DuplicateKeyException.class,
                () -> repository.register("other", "alice@example.com", "hashed", null, null, null, null));
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM user_details", Integer.class));
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM user_roles", Integer.class));
        }
    }
}
