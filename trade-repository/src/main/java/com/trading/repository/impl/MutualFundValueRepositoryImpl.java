package com.trading.repository.impl;

import java.sql.ResultSet;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.trading.model.MutualFundValue;
import com.trading.repository.MutualFundValueRepository;

@Repository
public class MutualFundValueRepositoryImpl implements MutualFundValueRepository {

    private final JdbcTemplate jdbcTemplate;

    public MutualFundValueRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // CREATE
    @Override
	public MutualFundValue save(MutualFundValue value) {

        String sql = """
                INSERT INTO mutual_fund_value
                    (mutual_fund_id,
                     total_value,
                     value_as_of_date)
                VALUES (?, ?, ?)
                RETURNING val_id
                """;

        Long generatedId = jdbcTemplate.queryForObject(
                sql,
                Long.class,
                value.getMutualFundId(),
                value.getTotalValue(),
                value.getValueAsOfDate()
        );

        value.setValId(generatedId);

        return findById(generatedId);
    }

    // READ - Find all
    @Override
	public List<MutualFundValue> findAll() {

        String sql = """
                SELECT val_id,
                       mutual_fund_id,
                       total_value,
                       value_as_of_date
                FROM mutual_fund_value
                ORDER BY val_id
                """;

        return jdbcTemplate.query(sql, this::mapRow);
    }

    // READ - Find by ID
    @Override
	public MutualFundValue findById(Long id) {

        String sql = """
                SELECT val_id,
                       mutual_fund_id,
                       total_value,
                       value_as_of_date
                FROM mutual_fund_value
                WHERE val_id = ?
                """;

        return jdbcTemplate.queryForObject(
                sql,
                this::mapRow,
                id
        );
    }

    // READ - Find values for a mutual fund
    @Override
	public List<MutualFundValue> findByMutualFundId(Long mutualFundId) {

        String sql = """
                SELECT val_id,
                       mutual_fund_id,
                       total_value,
                       value_as_of_date
                FROM mutual_fund_value
                WHERE mutual_fund_id = ?
                ORDER BY value_as_of_date DESC
                """;

        return jdbcTemplate.query(
                sql,
                this::mapRow,
                mutualFundId
        );
    }

    // UPDATE
    @Override
	public MutualFundValue update(MutualFundValue value) {

        String sql = """
                UPDATE mutual_fund_value
                SET mutual_fund_id = ?,
                    total_value = ?,
                    value_as_of_date = ?
                WHERE val_id = ?
                """;

        jdbcTemplate.update(
                sql,
                value.getMutualFundId(),
                value.getTotalValue(),
                value.getValueAsOfDate(),
                value.getValId()
        );

        return findById(value.getValId());
    }

    // DELETE
    @Override
	public boolean deleteById(Long id) {

        String sql = """
                DELETE FROM mutual_fund_value
                WHERE val_id = ?
                """;

        return jdbcTemplate.update(sql, id) > 0;
    }

    private MutualFundValue mapRow(ResultSet rs, int rowNum)
            throws java.sql.SQLException {

        MutualFundValue value = new MutualFundValue();

        value.setValId(
                rs.getLong("val_id")
        );

        value.setMutualFundId(
                rs.getLong("mutual_fund_id")
        );

        value.setTotalValue(
                rs.getBigDecimal("total_value")
        );

        if (rs.getTimestamp("value_as_of_date") != null) {
            value.setValueAsOfDate(
                    rs.getTimestamp("value_as_of_date")
                            .toLocalDateTime()
            );
        }

        return value;
    }
}