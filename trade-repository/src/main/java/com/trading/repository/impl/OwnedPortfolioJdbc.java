package com.trading.repository.impl;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;

abstract class OwnedPortfolioJdbc {
    protected final NamedParameterJdbcTemplate jdbc;
    private final long userId;

    OwnedPortfolioJdbc(JdbcTemplate jdbc, long userId) {
        if (userId <= 0) throw new IllegalArgumentException("Owner ID must be positive");
        this.jdbc = new NamedParameterJdbcTemplate(jdbc);
        this.userId = userId;
    }

    protected MapSqlParameterSource parameters() { return new MapSqlParameterSource("owner", userId); }

    // Column expressions below are fixed SQL identifiers, never supplied by clients.
    protected String brokerOwned(String column) {
        return column + " IN (SELECT broker_account_id FROM mutual_fund_broker_account WHERE owner_user_id = :owner)";
    }

    protected String fundOwned(String column) {
        return column + " IN (SELECT mutual_fund_id FROM mutual_fund WHERE " + brokerOwned("broker_account_id") + ")";
    }

    protected long insert(String sql, MapSqlParameterSource parameters, String key) {
        var keys = new GeneratedKeyHolder();
        if (jdbc.update(sql, parameters, keys, new String[]{key}) != 1) throw new EmptyResultDataAccessException(1);
        return keys.getKey().longValue();
    }

    protected void requireUpdated(int rows) {
        if (rows != 1) throw new EmptyResultDataAccessException(1);
    }
}
