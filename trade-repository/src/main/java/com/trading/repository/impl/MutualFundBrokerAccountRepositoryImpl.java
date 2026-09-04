package com.trading.repository.impl;

import java.sql.ResultSet;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.trading.model.MutualFundBrokerAccount;
import com.trading.repository.MutualFundBrokerAccountRepository;

@Repository
public class MutualFundBrokerAccountRepositoryImpl implements MutualFundBrokerAccountRepository {

    private final JdbcTemplate jdbcTemplate;

    public MutualFundBrokerAccountRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
	public List<MutualFundBrokerAccount> findAll() {
        String sql = """
            SELECT broker_account_id, broker_name, account_id, create_date, update_date
            FROM mutual_fund_broker_account
            """;

        return jdbcTemplate.query(sql, this::mapRow);
    }

    @Override
	public MutualFundBrokerAccount findById(Long id) {
        String sql = """
            SELECT broker_account_id, broker_name, account_id, create_date, update_date
            FROM mutual_fund_broker_account
            WHERE broker_account_id = ?
            """;

        return jdbcTemplate.queryForObject(sql, this::mapRow, id);
    }

    @Override
	public int save(MutualFundBrokerAccount account) {
        String sql = """
            INSERT INTO mutual_fund_broker_account
                (broker_name, account_id)
            VALUES (?, ?)
            """;

        return jdbcTemplate.update(
                sql,
                account.getBrokerName(),
                account.getAccountId()
        );
    }

    @Override
	public int update(MutualFundBrokerAccount account) {
        String sql = """
            UPDATE mutual_fund_broker_account
            SET broker_name = ?,
                account_id = ?,
                update_date = CURRENT_TIMESTAMP
            WHERE broker_account_id = ?
            """;

        return jdbcTemplate.update(
                sql,
                account.getBrokerName(),
                account.getAccountId(),
                account.getId()
        );
    }

    @Override
	public int deleteById(Long id) {
        String sql = """
            DELETE FROM mutual_fund_broker_account
            WHERE broker_account_id = ?
            """;

        return jdbcTemplate.update(sql, id);
    }

    private MutualFundBrokerAccount mapRow(ResultSet rs, int rowNum) throws java.sql.SQLException {
    	MutualFundBrokerAccount account = new MutualFundBrokerAccount();

        account.setId(rs.getLong("broker_account_id"));
        account.setBrokerName(rs.getString("broker_name"));
        account.setAccountId(rs.getString("account_id"));
        account.setCreateDate(
                rs.getTimestamp("create_date").toLocalDateTime()
        );
        account.setUpdateDate(
                rs.getTimestamp("update_date").toLocalDateTime()
        );

        return account;
    }
}