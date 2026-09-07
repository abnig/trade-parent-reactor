package com.trading.repository.impl;

import java.sql.ResultSet;
import java.util.List;
import com.trading.model.MutualFundTxn;
import com.trading.model.enums.TransactionType;
import com.trading.model.result.TransactionSummary;
import com.trading.repository.MutualFundTxnRepository;
import com.trading.repository.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

/** Owner restriction is applied in SQL, including writes and aggregate counts. */
final class OwnedTxnRepository extends OwnedPortfolioJdbc implements MutualFundTxnRepository {
    OwnedTxnRepository(JdbcTemplate jdbc, long userId) { super(jdbc, userId); }

    private String scope() { return fundOwned("mutual_fund_id"); }

    @Override
    public List<MutualFundTxn> findAll(PageRequest page) {
        return jdbc.query("SELECT * FROM mutual_fund_txn WHERE " + scope() + " ORDER BY mutual_fund_txn_id LIMIT :limit OFFSET :offset",
                parameters().addValue("limit", page.size()).addValue("offset", page.offset()), this::mapRow);
    }

    @Override
    public long count() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM mutual_fund_txn WHERE " + scope(), parameters(), Long.class);
    }

    @Override
    public MutualFundTxn findById(Long id) {
        return jdbc.queryForObject("SELECT * FROM mutual_fund_txn WHERE mutual_fund_txn_id = :id AND " + scope(),
                parameters().addValue("id", id), this::mapRow);
    }

    @Override
    public MutualFundTxn save(MutualFundTxn item) {
        long id = insert("INSERT INTO mutual_fund_txn (mutual_fund_id, amount, txn_date, units, avg_price, txn_type) SELECT :mutual_fund_id, :amount, :txn_date, :units, :avg_price, :txn_type WHERE " + fundOwned(":mutual_fund_id"),
                parameters().addValue("mutual_fund_id", item.getMutualFundId()).addValue("amount", item.getAmount()).addValue("txn_date", item.getTxnDate()).addValue("units", item.getUnits()).addValue("avg_price", item.getAvgPrice()).addValue("txn_type", item.getTransactionType().name()), "mutual_fund_txn_id");
        item.setMutualFundTxnId(id);
        return findById(id);
    }

    @Override
    public MutualFundTxn update(MutualFundTxn item) {
        int rows = jdbc.update("UPDATE mutual_fund_txn SET mutual_fund_id = :mutual_fund_id, amount = :amount, txn_date = :txn_date, units = :units, avg_price = :avg_price, txn_type = :txn_type, update_date = CURRENT_TIMESTAMP WHERE mutual_fund_txn_id = :id AND " + scope() + " AND " + fundOwned(":mutual_fund_id"),
                parameters().addValue("mutual_fund_id", item.getMutualFundId()).addValue("amount", item.getAmount()).addValue("txn_date", item.getTxnDate()).addValue("units", item.getUnits()).addValue("avg_price", item.getAvgPrice()).addValue("txn_type", item.getTransactionType().name()).addValue("id", item.getMutualFundTxnId()));
        requireUpdated(rows);
        return findById(item.getMutualFundTxnId());
    }

    @Override
    public boolean deleteById(Long id) {
        int rows = jdbc.update("DELETE FROM mutual_fund_txn WHERE mutual_fund_txn_id = :id AND " + scope(), parameters().addValue("id", id));
        return rows > 0;
    }

    @Override
    public List<MutualFundTxn> findByMutualFundId(Long parentId, PageRequest page) {
        return jdbc.query("SELECT * FROM mutual_fund_txn WHERE mutual_fund_id = :parent AND " + scope()
                + " ORDER BY txn_date DESC, mutual_fund_txn_id LIMIT :limit OFFSET :offset",
                parameters().addValue("parent", parentId).addValue("limit", page.size()).addValue("offset", page.offset()), this::mapRow);
    }

    @Override
    public long countByMutualFundId(Long parentId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM mutual_fund_txn WHERE mutual_fund_id = :parent AND " + scope(),
                parameters().addValue("parent", parentId), Long.class);
    }

    @Override
    public TransactionSummary getSummary() {
        return jdbc.queryForObject("SELECT COALESCE(SUM(amount), 0) AS total_value, COALESCE(SUM(units), 0) AS total_units FROM mutual_fund_txn WHERE " + scope(), parameters(), this::mapSummaryRow);
    }

    @Override
    public TransactionSummary getSummary(Long mutualFundId) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(amount), 0) AS total_value, COALESCE(SUM(units), 0) AS total_units FROM mutual_fund_txn WHERE mutual_fund_id = :parent AND " + scope(), parameters().addValue("parent", mutualFundId), this::mapSummaryRow);
    }

	private MutualFundTxn mapRow(ResultSet rs, int rowNum) throws java.sql.SQLException {

		MutualFundTxn txn = new MutualFundTxn();

		txn.setMutualFundTxnId(rs.getLong("mutual_fund_txn_id"));

		txn.setMutualFundId(rs.getLong("mutual_fund_id"));

		txn.setAmount(rs.getBigDecimal("amount"));
		
		txn.setTransactionType(TransactionType.valueOf(rs.getString("txn_type")));

		// New columns

		txn.setUnits(rs.getBigDecimal("units"));

		txn.setAvgPrice(rs.getBigDecimal("avg_price"));

		if (rs.getTimestamp("create_date") != null) {
			txn.setCreateDate(rs.getTimestamp("create_date").toLocalDateTime());
		}

		if (rs.getTimestamp("update_date") != null) {
			txn.setUpdateDate(rs.getTimestamp("update_date").toLocalDateTime());
		}

		if (rs.getTimestamp("txn_date") != null) {
			txn.setTxnDate(rs.getTimestamp("txn_date").toLocalDateTime());
		}

		return txn;
	}

	private TransactionSummary mapSummaryRow(ResultSet rs, int rowNum) throws java.sql.SQLException {
		return new TransactionSummary(rs.getBigDecimal("total_value"), rs.getBigDecimal("total_units"));
	}
}
