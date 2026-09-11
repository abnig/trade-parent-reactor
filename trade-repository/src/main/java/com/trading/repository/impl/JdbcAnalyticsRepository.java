package com.trading.repository.impl;

import java.util.List;
import com.trading.model.MutualFund;
import com.trading.model.MutualFundValue;
import com.trading.repository.AnalyticsRepository;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import com.trading.model.result.PortfolioAnalytics;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

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

    @Override
    public PortfolioAnalytics findPortfolio(long ownerId, LocalDate fromDate, LocalDate toDate) {
        var parameters = new MapSqlParameterSource("ownerId", ownerId)
                .addValue("fromStart", fromDate == null ? null : Timestamp.valueOf(fromDate.atStartOfDay()), Types.TIMESTAMP)
                .addValue("toExclusive", toDate == null ? null : Timestamp.valueOf(toDate.plusDays(1).atStartOfDay()), Types.TIMESTAMP);
        // Aggregate each source before joining: snapshot history must never multiply cash flows.
        // A single statement also gives all funds a consistent database snapshot.
        var rows = new NamedParameterJdbcTemplate(jdbc).query("""
                WITH owned_funds AS (
                    SELECT f.mutual_fund_id, f.mutual_fund_name, b.broker_account_id, b.broker_name, b.account_id
                    FROM mutual_fund f
                    JOIN mutual_fund_broker_account b ON b.broker_account_id = f.broker_account_id
                    WHERE b.owner_user_id = :ownerId
                ), transaction_totals AS (
                    SELECT t.mutual_fund_id,
                           SUM(CASE WHEN t.txn_type = 'BUY' THEN t.amount
                                    WHEN t.txn_type = 'SELL' THEN -ABS(t.amount) ELSE 0 END) AS total_invested,
                           SUM(CASE WHEN t.txn_type = 'BUY' THEN t.amount ELSE 0 END) AS total_bought,
                           SUM(CASE WHEN t.txn_type IN ('BUY', 'SELL') THEN 0 ELSE 1 END) AS invalid_transactions,
                           MAX(t.txn_date) AS last_transaction_date
                    FROM mutual_fund_txn t
                    JOIN owned_funds f ON f.mutual_fund_id = t.mutual_fund_id
                    WHERE (CAST(:toExclusive AS TIMESTAMP) IS NULL OR t.txn_date < :toExclusive)
                    GROUP BY t.mutual_fund_id
                ), ranked_values AS (
                    SELECT v.mutual_fund_id, v.total_value, v.value_as_of_date,
                           ROW_NUMBER() OVER (PARTITION BY v.mutual_fund_id
                                              ORDER BY CAST(v.value_as_of_date AS DATE) DESC, v.val_id DESC) AS position
                    FROM mutual_fund_value v
                    JOIN owned_funds f ON f.mutual_fund_id = v.mutual_fund_id
                    WHERE (CAST(:fromStart AS TIMESTAMP) IS NULL OR v.value_as_of_date >= :fromStart)
                      AND (CAST(:toExclusive AS TIMESTAMP) IS NULL OR v.value_as_of_date < :toExclusive)
                )
                SELECT f.*, COALESCE(t.total_invested, 0) AS total_invested,
                       COALESCE(t.total_bought, 0) AS total_bought, t.last_transaction_date,
                       COALESCE(t.invalid_transactions, 0) AS invalid_transactions,
                       v.total_value, v.value_as_of_date
                FROM owned_funds f
                LEFT JOIN transaction_totals t ON t.mutual_fund_id = f.mutual_fund_id
                LEFT JOIN ranked_values v ON v.mutual_fund_id = f.mutual_fund_id AND v.position = 1
                ORDER BY f.mutual_fund_name, f.mutual_fund_id
                """, parameters, (rs, row) -> {
                    if (rs.getLong("invalid_transactions") > 0) {
                        throw new IllegalStateException("Portfolio history contains an invalid transaction type.");
                    }
                    return new PortfolioAnalytics.Fund(
                    rs.getLong("mutual_fund_id"), rs.getString("mutual_fund_name"), rs.getLong("broker_account_id"),
                    rs.getString("broker_name"), rs.getString("account_id"), rs.getBigDecimal("total_invested"),
                    rs.getBigDecimal("total_bought"), rs.getBigDecimal("total_value"),
                    calendarDate(rs.getTimestamp("value_as_of_date")), calendarDate(rs.getTimestamp("last_transaction_date")),
                    null, null, null, null);
                });
        return aggregatePortfolio(rows, toDate);
    }

    private static LocalDate calendarDate(Timestamp value) {
        return value == null ? null : value.toLocalDateTime().toLocalDate();
    }

    private static BigDecimal percentage(BigDecimal numerator, BigDecimal denominator) {
        return numerator == null || denominator == null || denominator.signum() <= 0 ? null
                : numerator.multiply(BigDecimal.valueOf(100)).divide(denominator, 6, RoundingMode.HALF_UP);
    }

    private static PortfolioAnalytics aggregatePortfolio(List<PortfolioAnalytics.Fund> rows, LocalDate toDate) {
        var invested = BigDecimal.ZERO;
        var bought = BigDecimal.ZERO;
        var knownValue = BigDecimal.ZERO;
        int valued = 0;
        LocalDate asOfDate = toDate;
        boolean nonnegativeValues = true;
        for (var fund : rows) {
            invested = invested.add(fund.totalInvested());
            bought = bought.add(fund.totalBought());
            if (fund.totalValue() != null) {
                knownValue = knownValue.add(fund.totalValue());
                valued++;
                nonnegativeValues &= fund.totalValue().signum() >= 0;
            }
            if (toDate == null) {
                for (LocalDate date : new LocalDate[]{fund.valueAsOfDate(), fund.lastTransactionDate()}) {
                    if (date != null && (asOfDate == null || date.isAfter(asOfDate))) asOfDate = date;
                }
            }
        }
        BigDecimal value = valued == rows.size() ? knownValue : null;
        BigDecimal allocationBase = nonnegativeValues ? value : null;
        BigDecimal gain = value == null ? null : value.subtract(invested);
        boolean nonnegativePurchases = rows.stream().allMatch(fund -> fund.totalBought().signum() >= 0);
        var funds = new ArrayList<PortfolioAnalytics.Fund>();
        var byBroker = new LinkedHashMap<Long, List<PortfolioAnalytics.Fund>>();
        for (var fund : rows) {
            BigDecimal fundGain = fund.totalValue() == null ? null : fund.totalValue().subtract(fund.totalInvested());
            var result = new PortfolioAnalytics.Fund(fund.mutualFundId(), fund.mutualFundName(), fund.brokerAccountId(),
                    fund.brokerName(), fund.accountId(), fund.totalInvested(), fund.totalBought(), fund.totalValue(),
                    fund.valueAsOfDate(), fund.lastTransactionDate(), fundGain, percentage(fundGain, fund.totalInvested()),
                    percentage(fund.totalValue(), allocationBase), percentage(fund.totalBought(), nonnegativePurchases ? bought : null));
            funds.add(result);
            byBroker.computeIfAbsent(fund.brokerAccountId(), ignored -> new ArrayList<>()).add(result);
        }
        var brokers = new ArrayList<PortfolioAnalytics.BrokerAccount>();
        for (var brokerFunds : byBroker.values()) {
            var first = brokerFunds.getFirst();
            var brokerInvested = BigDecimal.ZERO;
            var brokerKnown = BigDecimal.ZERO;
            int brokerValued = 0;
            for (var fund : brokerFunds) {
                brokerInvested = brokerInvested.add(fund.totalInvested());
                if (fund.totalValue() != null) { brokerKnown = brokerKnown.add(fund.totalValue()); brokerValued++; }
            }
            BigDecimal brokerValue = brokerValued == brokerFunds.size() ? brokerKnown : null;
            brokers.add(new PortfolioAnalytics.BrokerAccount(first.brokerAccountId(), first.brokerName(), first.accountId(),
                    brokerValue, brokerKnown, brokerInvested, brokerFunds.size(), brokerValued, percentage(brokerValue, allocationBase)));
        }
        return new PortfolioAnalytics(asOfDate, value, knownValue, invested, bought, gain, percentage(gain, invested),
                rows.size(), valued, List.copyOf(funds), List.copyOf(brokers));
    }
}
