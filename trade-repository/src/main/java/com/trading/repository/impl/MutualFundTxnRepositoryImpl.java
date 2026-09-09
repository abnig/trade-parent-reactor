package com.trading.repository.impl;

import java.sql.ResultSet;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.trading.model.MutualFundTxn;
import com.trading.model.enums.TransactionType;
import com.trading.model.result.TransactionSummary;
import com.trading.model.result.FundInvestmentSummary;
import com.trading.repository.MutualFundTxnRepository;
import com.trading.repository.PageRequest;

@Repository
public class MutualFundTxnRepositoryImpl implements MutualFundTxnRepository {

	private final JdbcTemplate jdbcTemplate;

	public MutualFundTxnRepositoryImpl(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

    @Override
    public List<FundInvestmentSummary> getFundInvestments() {
        String sql = """
                SELECT f.mutual_fund_id, f.mutual_fund_name,
                       COALESCE(SUM(CASE WHEN t.txn_type = 'BUY' THEN t.amount
                                         WHEN t.txn_type = 'SELL' THEN -t.amount ELSE 0 END), 0) AS total_invested
                FROM mutual_fund f
                LEFT JOIN mutual_fund_txn t ON t.mutual_fund_id = f.mutual_fund_id
                GROUP BY f.mutual_fund_id, f.mutual_fund_name
                ORDER BY f.mutual_fund_name, f.mutual_fund_id
                """;
        return jdbcTemplate.query(sql,
                (rs, row) -> new FundInvestmentSummary(rs.getLong("mutual_fund_id"),
                        rs.getString("mutual_fund_name"), rs.getBigDecimal("total_invested")));
    }

	// CREATE
	@Override
	public MutualFundTxn save(MutualFundTxn txn) {

        String sql = """
                INSERT INTO mutual_fund_txn
                    (mutual_fund_id,
                     amount,
                     txn_date, units, avg_price, txn_type)
                VALUES (?, ?, ?, ?, ?, ?)
                RETURNING mutual_fund_txn_id
                """;

        Long generatedId = jdbcTemplate.queryForObject(
                sql,
                Long.class,
                txn.getMutualFundId(),
                txn.getAmount(),
                txn.getTxnDate(),
                txn.getUnits(),
                txn.getAvgPrice(),
                txn.getTransactionType().name()
        );

        txn.setMutualFundTxnId(generatedId);

        return findById(generatedId);
    }

	// READ - Find all
	@Override
	public List<MutualFundTxn> findAll(PageRequest pageRequest) {

		String sql = """
				SELECT mutual_fund_txn_id,
				       mutual_fund_id,
				       amount, units, avg_price,
				       create_date,
				       update_date,
				       txn_date,
				       txn_type
				FROM mutual_fund_txn
				ORDER BY mutual_fund_txn_id
				LIMIT ? OFFSET ?
				""";

		return jdbcTemplate.query(sql, this::mapRow, pageRequest.size(), pageRequest.offset());
	}

	@Override
	public long count() {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM mutual_fund_txn", Long.class);
	}

	// READ - Find by ID
	@Override
	public MutualFundTxn findById(Long id) {

		String sql = """
				SELECT mutual_fund_txn_id,
				       mutual_fund_id,
				       amount, units, avg_price,
				       create_date,
				       update_date,
				       txn_date,
				       txn_type
				FROM mutual_fund_txn
				WHERE mutual_fund_txn_id = ?
				""";

		return jdbcTemplate.queryForObject(sql, this::mapRow, id);
	}

	// READ - Find transactions for a mutual fund
	@Override
	public List<MutualFundTxn> findByMutualFundId(Long mutualFundId, PageRequest pageRequest) {

		String sql = """
				SELECT mutual_fund_txn_id,
				       mutual_fund_id,
				       amount, units, avg_price,
				       create_date,
				       update_date,
				       txn_date,
				       txn_type
				FROM mutual_fund_txn
				WHERE mutual_fund_id = ?
				ORDER BY txn_date DESC, mutual_fund_txn_id
				LIMIT ? OFFSET ?
				""";

		return jdbcTemplate.query(sql, this::mapRow, mutualFundId, pageRequest.size(), pageRequest.offset());
	}

	@Override
	public long countByMutualFundId(Long mutualFundId) {
		return jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM mutual_fund_txn WHERE mutual_fund_id = ?", Long.class, mutualFundId);
	}

	@Override
	public TransactionSummary getSummary() {
		String sql = """
				SELECT COALESCE(SUM(amount), 0) AS total_value,
				       COALESCE(SUM(units), 0) AS total_units
				FROM mutual_fund_txn
				""";
		return jdbcTemplate.queryForObject(sql, this::mapSummaryRow);
	}

	@Override
	public TransactionSummary getSummary(Long mutualFundId) {
		String sql = """
				SELECT COALESCE(SUM(amount), 0) AS total_value,
				       COALESCE(SUM(units), 0) AS total_units
				FROM mutual_fund_txn
				WHERE mutual_fund_id = ?
				""";
		return jdbcTemplate.queryForObject(sql, this::mapSummaryRow, mutualFundId);
	}

	// UPDATE
	@Override
	public MutualFundTxn update(MutualFundTxn txn) {

		String sql = """
				       UPDATE mutual_fund_txn
				       SET mutual_fund_id = ?,
				           amount = ?,
				           txn_date = ?,
				           units = ?,
						   avg_price = ?,
						   txn_type = ?,
				           update_date = CURRENT_TIMESTAMP
				       WHERE mutual_fund_txn_id = ?
				       """;

		jdbcTemplate.update(sql, txn.getMutualFundId(), txn.getAmount(), txn.getTxnDate(), txn.getUnits(),
				txn.getAvgPrice(), txn.getTransactionType().name(), txn.getMutualFundTxnId());

		return findById(txn.getMutualFundTxnId());
	}

	// DELETE
	@Override
	public boolean deleteById(Long id) {

		String sql = """
				DELETE FROM mutual_fund_txn
				WHERE mutual_fund_txn_id = ?
				""";

		return jdbcTemplate.update(sql, id) > 0;
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
