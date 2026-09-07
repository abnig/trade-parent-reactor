package com.trading.repository.impl;

import java.util.List;
import com.trading.model.MutualFund;
import com.trading.model.MutualFundValue;
import com.trading.repository.AnalyticsRepository;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAnalyticsRepository implements AnalyticsRepository {
    private final JdbcTemplate jdbc;

    public JdbcAnalyticsRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public List<MutualFund> findFunds(long ownerId) {
        return jdbc.query("""
                SELECT f.mutual_fund_id, f.broker_account_id, f.mutual_fund_name,
                       f.create_date, f.update_date
                FROM mutual_fund f
                JOIN mutual_fund_broker_account b ON b.broker_account_id = f.broker_account_id
                WHERE b.owner_user_id = ?
                ORDER BY f.mutual_fund_name, f.mutual_fund_id
                """, BeanPropertyRowMapper.newInstance(MutualFund.class), ownerId);
    }

    @Override
    public List<MutualFundValue> findValueHistory(long ownerId, long mutualFundId) {
        return jdbc.query("""
                SELECT v.val_id, v.mutual_fund_id, v.total_value, v.value_as_of_date
                FROM mutual_fund_value v
                JOIN mutual_fund f ON f.mutual_fund_id = v.mutual_fund_id
                JOIN mutual_fund_broker_account b ON b.broker_account_id = f.broker_account_id
                WHERE b.owner_user_id = ? AND v.mutual_fund_id = ?
                ORDER BY v.value_as_of_date, v.val_id
                """, BeanPropertyRowMapper.newInstance(MutualFundValue.class), ownerId, mutualFundId);
    }
}
