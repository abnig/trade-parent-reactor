package com.trading.repository.impl;

import java.sql.ResultSet;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.trading.model.MutualFund;
import com.trading.repository.MutualFundRepository;

@Repository
public class MutualFundRepositoryImpl implements MutualFundRepository {

    private final JdbcTemplate jdbcTemplate;

    public MutualFundRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // CREATE
    @Override
	public MutualFund save(MutualFund mutualFund) {

        String sql = """
                INSERT INTO mutual_fund
                    (broker_account_id, mutual_fund_name)
                VALUES (?, ?)
                RETURNING mutual_fund_id
                """;

        Long generatedId = jdbcTemplate.queryForObject(
                sql,
                Long.class,
                mutualFund.getBrokerAccountId(),
                mutualFund.getMutualFundName()
        );

        mutualFund.setMutualFundId(generatedId);

        return findById(generatedId);
    }

    // READ - Find all
    @Override
	public List<MutualFund> findAll() {

        String sql = """
                SELECT mutual_fund_id,
                       broker_account_id,
                       mutual_fund_name,
                       create_date,
                       update_date
                FROM mutual_fund
                ORDER BY mutual_fund_id
                """;

        return jdbcTemplate.query(sql, this::mapRow);
    }

    // READ - Find by ID
    @Override
	public MutualFund findById(Long id) {

        String sql = """
                SELECT mutual_fund_id,
                       broker_account_id,
                       mutual_fund_name,
                       create_date,
                       update_date
                FROM mutual_fund
                WHERE mutual_fund_id = ?
                """;

        return jdbcTemplate.queryForObject(
                sql,
                this::mapRow,
                id
        );
    }

    // READ - Find by Broker Account
    @Override
	public List<MutualFund> findByBrokerAccountId(Long brokerAccountId) {

        String sql = """
                SELECT mutual_fund_id,
                       broker_account_id,
                       mutual_fund_name,
                       create_date,
                       update_date
                FROM mutual_fund
                WHERE broker_account_id = ?
                ORDER BY mutual_fund_id
                """;

        return jdbcTemplate.query(
                sql,
                this::mapRow,
                brokerAccountId
        );
    }

    // UPDATE
    @Override
	public MutualFund update(MutualFund mutualFund) {

        String sql = """
                UPDATE mutual_fund
                SET broker_account_id = ?,
                    mutual_fund_name = ?,
                    update_date = CURRENT_TIMESTAMP
                WHERE mutual_fund_id = ?
                """;

        jdbcTemplate.update(
                sql,
                mutualFund.getBrokerAccountId(),
                mutualFund.getMutualFundName(),
                mutualFund.getMutualFundId()
        );

        return findById(mutualFund.getMutualFundId());
    }

    // DELETE
    @Override
	public boolean deleteById(Long id) {

        String sql = """
                DELETE FROM mutual_fund
                WHERE mutual_fund_id = ?
                """;

        return jdbcTemplate.update(sql, id) > 0;
    }

    private MutualFund mapRow(ResultSet rs, int rowNum)
            throws java.sql.SQLException {

        MutualFund mutualFund = new MutualFund();

        mutualFund.setMutualFundId(
                rs.getLong("mutual_fund_id")
        );

        mutualFund.setBrokerAccountId(
                rs.getLong("broker_account_id")
        );

        mutualFund.setMutualFundName(
                rs.getString("mutual_fund_name")
        );

        if (rs.getTimestamp("create_date") != null) {
            mutualFund.setCreateDate(
                    rs.getTimestamp("create_date").toLocalDateTime()
            );
        }

        if (rs.getTimestamp("update_date") != null) {
            mutualFund.setUpdateDate(
                    rs.getTimestamp("update_date").toLocalDateTime()
            );
        }

        return mutualFund;
    }
}