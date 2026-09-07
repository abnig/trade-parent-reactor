package com.trading.repository.impl;

import java.sql.ResultSet;
import java.util.List;
import com.trading.model.MutualFundBrokerAccount;
import com.trading.repository.MutualFundBrokerAccountRepository;
import com.trading.repository.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

/** Owner restriction is applied in SQL, including writes and aggregate counts. */
final class OwnedBrokerRepository extends OwnedPortfolioJdbc implements MutualFundBrokerAccountRepository {
    OwnedBrokerRepository(JdbcTemplate jdbc, long userId) { super(jdbc, userId); }

    private String scope() { return "owner_user_id = :owner"; }

    @Override
    public List<MutualFundBrokerAccount> findAll(PageRequest page) {
        return jdbc.query("SELECT * FROM mutual_fund_broker_account WHERE " + scope() + " ORDER BY broker_account_id LIMIT :limit OFFSET :offset",
                parameters().addValue("limit", page.size()).addValue("offset", page.offset()), this::mapRow);
    }

    @Override
    public long count() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM mutual_fund_broker_account WHERE " + scope(), parameters(), Long.class);
    }

    @Override
    public MutualFundBrokerAccount findById(Long id) {
        return jdbc.queryForObject("SELECT * FROM mutual_fund_broker_account WHERE broker_account_id = :id AND " + scope(),
                parameters().addValue("id", id), this::mapRow);
    }

    @Override
    public int save(MutualFundBrokerAccount item) {
        long id = insert("INSERT INTO mutual_fund_broker_account (broker_name, account_id, owner_user_id) VALUES (:broker_name, :account_id, :owner)",
                parameters().addValue("broker_name", item.getBrokerName()).addValue("account_id", item.getAccountId()), "broker_account_id");
        item.setId(id);
        return 1;
    }

    @Override
    public int update(MutualFundBrokerAccount item) {
        int rows = jdbc.update("UPDATE mutual_fund_broker_account SET broker_name = :broker_name, account_id = :account_id, update_date = CURRENT_TIMESTAMP WHERE broker_account_id = :id AND " + scope(),
                parameters().addValue("broker_name", item.getBrokerName()).addValue("account_id", item.getAccountId()).addValue("id", item.getId()));
        requireUpdated(rows);
        return 1;
    }

    @Override
    public int deleteById(Long id) {
        int rows = jdbc.update("DELETE FROM mutual_fund_broker_account WHERE broker_account_id = :id AND " + scope(), parameters().addValue("id", id));
        return rows;
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
