package com.trading.repository.impl;

import java.sql.ResultSet;
import java.util.List;
import com.trading.model.MutualFundValue;
import com.trading.repository.MutualFundValueRepository;
import com.trading.repository.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

/** Owner restriction is applied in SQL, including writes and aggregate counts. */
final class OwnedValueRepository extends OwnedPortfolioJdbc implements MutualFundValueRepository {
    OwnedValueRepository(JdbcTemplate jdbc, long userId) { super(jdbc, userId); }

    private String scope() { return fundOwned("mutual_fund_id"); }

    @Override
    public List<MutualFundValue> findAll(PageRequest page) {
        return jdbc.query("SELECT * FROM mutual_fund_value WHERE " + scope() + " ORDER BY val_id LIMIT :limit OFFSET :offset",
                parameters().addValue("limit", page.size()).addValue("offset", page.offset()), this::mapRow);
    }

    @Override
    public long count() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM mutual_fund_value WHERE " + scope(), parameters(), Long.class);
    }

    @Override
    public MutualFundValue findById(Long id) {
        return jdbc.queryForObject("SELECT * FROM mutual_fund_value WHERE val_id = :id AND " + scope(),
                parameters().addValue("id", id), this::mapRow);
    }

    @Override
    public MutualFundValue save(MutualFundValue item) {
        long id = insert("INSERT INTO mutual_fund_value (mutual_fund_id, total_value, value_as_of_date) SELECT :mutual_fund_id, :total_value, :value_as_of_date WHERE " + fundOwned(":mutual_fund_id"),
                parameters().addValue("mutual_fund_id", item.getMutualFundId()).addValue("total_value", item.getTotalValue()).addValue("value_as_of_date", item.getValueAsOfDate()), "val_id");
        item.setValId(id);
        return findById(id);
    }

    @Override
    public MutualFundValue update(MutualFundValue item) {
        int rows = jdbc.update("UPDATE mutual_fund_value SET mutual_fund_id = :mutual_fund_id, total_value = :total_value, value_as_of_date = :value_as_of_date WHERE val_id = :id AND " + scope() + " AND " + fundOwned(":mutual_fund_id"),
                parameters().addValue("mutual_fund_id", item.getMutualFundId()).addValue("total_value", item.getTotalValue()).addValue("value_as_of_date", item.getValueAsOfDate()).addValue("id", item.getValId()));
        requireUpdated(rows);
        return findById(item.getValId());
    }

    @Override
    public boolean deleteById(Long id) {
        int rows = jdbc.update("DELETE FROM mutual_fund_value WHERE val_id = :id AND " + scope(), parameters().addValue("id", id));
        return rows > 0;
    }

    @Override
    public List<MutualFundValue> findByMutualFundId(Long parentId, PageRequest page) {
        return jdbc.query("SELECT * FROM mutual_fund_value WHERE mutual_fund_id = :parent AND " + scope()
                + " ORDER BY value_as_of_date DESC, val_id LIMIT :limit OFFSET :offset",
                parameters().addValue("parent", parentId).addValue("limit", page.size()).addValue("offset", page.offset()), this::mapRow);
    }

    @Override
    public long countByMutualFundId(Long parentId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM mutual_fund_value WHERE mutual_fund_id = :parent AND " + scope(),
                parameters().addValue("parent", parentId), Long.class);
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
