package com.trading.repository.impl;

import java.util.HashSet;
import java.util.Optional;
import com.trading.model.LoginAccount;
import com.trading.repository.LoginAccountRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcLoginAccountRepository implements LoginAccountRepository {
    private final JdbcTemplate jdbc;
    public JdbcLoginAccountRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public Optional<LoginAccount> findByUsername(String username) {
        var accounts = jdbc.query("SELECT id, username, password, enabled, account_non_expired, account_non_locked, credentials_non_expired FROM users WHERE username = ?",
            (rs, row) -> new LoginAccount(rs.getLong("id"), rs.getString("username"), rs.getString("password"),
                rs.getBoolean("enabled"), rs.getBoolean("account_non_expired"), rs.getBoolean("account_non_locked"),
                rs.getBoolean("credentials_non_expired"), java.util.Set.of()), username);
        if (accounts.isEmpty()) return Optional.empty();
        var account = accounts.getFirst();
        var roles = new HashSet<>(jdbc.queryForList(
                "SELECT r.name FROM roles r JOIN user_roles ur ON ur.role_id = r.id WHERE ur.user_id = ?", String.class, account.id()));
        return Optional.of(new LoginAccount(account.id(), account.username(), account.passwordHash(), account.enabled(),
                account.accountNonExpired(), account.accountNonLocked(), account.credentialsNonExpired(), roles));
    }
}
