package com.trading.repository.impl;

import com.trading.repository.UserRegistrationRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class UserRegistrationRepositoryImpl implements UserRegistrationRepository {
    private final JdbcTemplate jdbc;

    public UserRegistrationRepositoryImpl(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    @Transactional
    public long register(String username, String email, String passwordHash,
                         String firstName, String lastName, String phoneNumber, String avatarUrl) {
        Integer roleId = jdbc.queryForObject("SELECT id FROM roles WHERE name = ?", Integer.class, "ROLE_USER");
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(
                    "INSERT INTO users (username, email, password) VALUES (?, ?, ?)", new String[]{"id"});
            statement.setString(1, username);
            statement.setString(2, email);
            statement.setString(3, passwordHash);
            return statement;
        }, keys);
        long id = keys.getKey().longValue();
        jdbc.update("INSERT INTO user_details (user_id, first_name, last_name, phone_number, avatar_url) VALUES (?, ?, ?, ?, ?)",
                id, firstName, lastName, phoneNumber, avatarUrl);
        jdbc.update("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)", id, roleId);
        return id;
    }
}
