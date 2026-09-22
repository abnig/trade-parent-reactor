package com.trading.repository.impl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.util.List;
import com.trading.model.MutualFundOrder;
import com.trading.repository.MutualFundOrderRepository;
import com.trading.repository.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

/** Ownership is checked in SQL for the existing order and every replacement fund. */
final class OwnedOrderRepository extends OwnedPortfolioJdbc implements MutualFundOrderRepository {
    OwnedOrderRepository(JdbcTemplate jdbc, long userId) { super(jdbc, userId); }

    private String scope() { return fundOwned("mutual_fund_id"); }

    @Override
    public MutualFundOrder save(MutualFundOrder item) {
        long id = insert("""
                INSERT INTO mutual_fund_order (mutual_fund_id, mutual_fund_txn_id, txn_type, trade_date, ordered_at, amount, units, avg_price)
                SELECT :mutual_fund_id, :mutual_fund_txn_id, :txn_type, :trade_date, :ordered_at, :amount, :units, :avg_price
                WHERE
                """ + fundOwned(":mutual_fund_id"), values(item), "mutual_fund_order_id");
        item.setMutualFundOrderId(id);
        return findById(id);
    }

    @Override
    public MutualFundOrder update(MutualFundOrder item) {
        int rows = jdbc.update("""
                UPDATE mutual_fund_order SET mutual_fund_id = :mutual_fund_id,
                    mutual_fund_txn_id = :mutual_fund_txn_id,
                    txn_type = :txn_type,
                    trade_date = :trade_date,
                    ordered_at = :ordered_at,
                    amount = :amount,
                    units = :units,
                    avg_price = :avg_price,
                    update_date = CURRENT_TIMESTAMP
                WHERE mutual_fund_order_id = :id AND
                """ + scope() + " AND " + fundOwned(":mutual_fund_id"),
                values(item).addValue("id", item.getMutualFundOrderId()));
        requireUpdated(rows);
        return findById(item.getMutualFundOrderId());
    }

    @Override
    public MutualFundOrder findById(Long id) {
        return jdbc.queryForObject("SELECT * FROM mutual_fund_order WHERE mutual_fund_order_id = :id AND " + scope(),
                parameters().addValue("id", id), this::mapRow);
    }

    @Override
    public List<MutualFundOrder> findByMutualFundId(Long fundId, PageRequest page) {
        return jdbc.query("SELECT * FROM mutual_fund_order WHERE mutual_fund_id = :fund AND " + scope()
                        + " ORDER BY mutual_fund_order_id LIMIT :limit OFFSET :offset",
                parameters().addValue("fund", fundId).addValue("limit", page.size()).addValue("offset", page.offset()), this::mapRow);
    }

    @Override
    public long countByMutualFundId(Long fundId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM mutual_fund_order WHERE mutual_fund_id = :fund AND " + scope(),
                parameters().addValue("fund", fundId), Long.class);
    }

    private MapSqlParameterSource values(MutualFundOrder item) {
        return parameters()
                .addValue("mutual_fund_id", item.getMutualFundId())
                .addValue("mutual_fund_txn_id", item.getMutualFundTxnId())
                .addValue("txn_type", item.getTransactionType())
                .addValue("trade_date", item.getTradeDate())
                .addValue("ordered_at", item.getOrderedAt())
                .addValue("amount", item.getAmount())
                .addValue("units", item.getUnits())
                .addValue("avg_price", item.getAvgPrice());
    }

    private MutualFundOrder mapRow(ResultSet rs, int row) throws SQLException {
        var item = new MutualFundOrder();
        item.setMutualFundOrderId(rs.getLong("mutual_fund_order_id"));
        item.setMutualFundId(rs.getObject("mutual_fund_id", Long.class));
        item.setMutualFundTxnId(rs.getObject("mutual_fund_txn_id", Long.class));
        item.setTransactionType(rs.getString("txn_type"));
        item.setTradeDate(rs.getObject("trade_date", LocalDate.class));
        item.setOrderedAt(rs.getObject("ordered_at", LocalTime.class));
        item.setAmount(rs.getBigDecimal("amount"));
        item.setUnits(rs.getBigDecimal("units"));
        item.setAvgPrice(rs.getBigDecimal("avg_price"));
        item.setCreateDate(rs.getObject("create_date", LocalDateTime.class));
        item.setUpdateDate(rs.getObject("update_date", LocalDateTime.class));
        return item;
    }
}
