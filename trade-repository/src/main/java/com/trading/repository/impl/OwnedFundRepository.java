package com.trading.repository.impl;

import java.sql.ResultSet;
import java.util.List;
import com.trading.model.MutualFund;
import com.trading.repository.MutualFundRepository;
import com.trading.repository.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

/** Owner restriction is applied in SQL, including writes and aggregate counts. */
final class OwnedFundRepository extends OwnedPortfolioJdbc implements MutualFundRepository {
    OwnedFundRepository(JdbcTemplate jdbc, long userId) { super(jdbc, userId); }

    private String scope() { return brokerOwned("broker_account_id"); }

    @Override
    public List<MutualFund> findAll(PageRequest page) {
        return jdbc.query("SELECT * FROM mutual_fund WHERE " + scope() + " ORDER BY mutual_fund_id LIMIT :limit OFFSET :offset",
                parameters().addValue("limit", page.size()).addValue("offset", page.offset()), this::mapRow);
    }

    @Override
    public long count() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM mutual_fund WHERE " + scope(), parameters(), Long.class);
    }

    @Override
    public MutualFund findById(Long id) {
        return jdbc.queryForObject("SELECT * FROM mutual_fund WHERE mutual_fund_id = :id AND " + scope(),
                parameters().addValue("id", id), this::mapRow);
    }

    @Override
    public MutualFund save(MutualFund item) {
        long id = insert("INSERT INTO mutual_fund (broker_account_id, mutual_fund_name) SELECT :broker_account_id, :mutual_fund_name WHERE " + brokerOwned(":broker_account_id"),
                parameters().addValue("broker_account_id", item.getBrokerAccountId()).addValue("mutual_fund_name", item.getMutualFundName()), "mutual_fund_id");
        item.setMutualFundId(id);
        return findById(id);
    }

    @Override
    public MutualFund update(MutualFund item) {
        int rows = jdbc.update("UPDATE mutual_fund SET broker_account_id = :broker_account_id, mutual_fund_name = :mutual_fund_name, update_date = CURRENT_TIMESTAMP WHERE mutual_fund_id = :id AND " + scope() + " AND " + brokerOwned(":broker_account_id"),
                parameters().addValue("broker_account_id", item.getBrokerAccountId()).addValue("mutual_fund_name", item.getMutualFundName()).addValue("id", item.getMutualFundId()));
        requireUpdated(rows);
        return findById(item.getMutualFundId());
    }

    @Override
    public boolean deleteById(Long id) {
        int rows = jdbc.update("DELETE FROM mutual_fund WHERE mutual_fund_id = :id AND " + scope(), parameters().addValue("id", id));
        return rows > 0;
    }

    @Override
    public List<MutualFund> findByBrokerAccountId(Long parentId, PageRequest page) {
        return jdbc.query("SELECT * FROM mutual_fund WHERE broker_account_id = :parent AND " + scope()
                + " ORDER BY mutual_fund_id LIMIT :limit OFFSET :offset",
                parameters().addValue("parent", parentId).addValue("limit", page.size()).addValue("offset", page.offset()), this::mapRow);
    }

    @Override
    public long countByBrokerAccountId(Long parentId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM mutual_fund WHERE broker_account_id = :parent AND " + scope(),
                parameters().addValue("parent", parentId), Long.class);
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
