package com.trading.orders;

import com.trading.model.MutualFund;
import com.trading.model.MutualFundOrder;
import com.trading.repository.PageRequest;
import com.trading.repository.UserPortfolioRepositoryFactory;
import com.trading.repository.impl.JdbcUserPortfolioRepositoryFactory;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

class MutualFundOrderPersistenceTest {
    protected JdbcTemplate jdbc;
    protected UserPortfolioRepositoryFactory portfolios;
    private static final PageRequest PAGE = PageRequest.of(0, 20);

    protected DataSource dataSource() {
        return new DriverManagerDataSource("jdbc:h2:mem:orders_" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
    }

    protected String adaptMigration(String sql) {
        return sql.replace(" ON CONFLICT (name) DO NOTHING", "")
                .replace("CREATE UNIQUE INDEX idx_users_email_case_insensitive ON users (LOWER(email))",
                        "CREATE UNIQUE INDEX idx_users_email_case_insensitive ON users (email)");
    }

    protected void migrate(String file) throws Exception {
        String sql = adaptMigration(new ClassPathResource("db/migration/" + file)
                .getContentAsString(StandardCharsets.UTF_8));
        for (String statement : sql.split(";")) if (!statement.isBlank()) jdbc.execute(statement);
    }

    @BeforeEach void seedAndUpgrade() throws Exception {
        jdbc = new JdbcTemplate(dataSource());
        for (String file : List.of("V3__application_schema.sql", "V5__create_app_user.sql",
                "V6__portfolio_ownership.sql", "V7__password_recovery.sql", "V8__user_profile_hint.sql")) migrate(file);
        for (String name : List.of("alice", "bob")) {
            jdbc.update("INSERT INTO users(username,email,password) VALUES(?,?,?)", name, name + "@example.com", "test-only");
        }
        for (int id = 1; id <= 4; id++) {
            jdbc.update("INSERT INTO mutual_fund_broker_account(broker_name,account_id,owner_user_id) VALUES('Zerodha',?,?)",
                    "00CLIENT/" + id, id == 4 ? null : id == 3 ? 2 : 1);
            jdbc.update("INSERT INTO mutual_fund(broker_account_id,mutual_fund_name) VALUES(?, 'Existing scheme')", id);
            jdbc.update("INSERT INTO mutual_fund_txn(mutual_fund_id,amount,units,avg_price,txn_date,txn_type) VALUES(?,123.45,1.234,100.041,'2025-01-02 16:30:00','BUY')", id);
        }
        migrate("V9__mutual_fund_orders.sql");
        portfolios = new JdbcUserPortfolioRepositoryFactory(jdbc);
    }

    @Test void migrationPreservesLegacyDataAndDoesNotInventOrders() {
        var fund = portfolios.funds(1).findById(1L);
        assertNull(fund.getIsin());
        assertNull(fund.getPlan());
        assertEquals("Existing scheme", fund.getMutualFundName());
        assertEquals("00CLIENT/1", portfolios.brokers(1).findById(1L).getAccountId());
        var txn = portfolios.transactions(1).findById(1L);
        assertEquals(new BigDecimal("123.45"), txn.getAmount());
        assertEquals(new BigDecimal("1.234000"), txn.getUnits());
        assertEquals(new BigDecimal("100.041000"), txn.getAvgPrice());
        assertEquals(LocalDateTime.of(2025, 1, 2, 16, 30), txn.getTxnDate());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM mutual_fund_order", Integer.class));
        assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM mutual_fund_txn", Integer.class));
        assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM mutual_fund", Integer.class));
    }

    @Test void fundMetadataRoundTripsAndLegacyUpdatesPreserveIt() {
        var fund = new MutualFund();
        fund.setBrokerAccountId(1L);
        fund.setMutualFundName("Scheme");
        fund.setIsin("INF123456789");
        fund.setPlan("source plan text");
        var saved = portfolios.funds(1).save(fund);
        assertEquals(fund.getIsin(), saved.getIsin());
        assertEquals(fund.getPlan(), saved.getPlan());
        var legacyUpdate = new MutualFund(saved.getMutualFundId(), 1L, "Renamed", null, null);
        var updated = portfolios.funds(1).update(legacyUpdate);
        assertEquals("INF123456789", updated.getIsin());
        assertEquals("source plan text", updated.getPlan());
        assertEquals("Renamed", updated.getMutualFundName());
        fund.setIsin("too-short");
        assertThrows(DataIntegrityViolationException.class, () -> portfolios.funds(1).save(fund));
        fund.setIsin("INF1234567890");
        assertThrows(DataIntegrityViolationException.class, () -> portfolios.funds(1).save(fund));
    }

    @Test void completedTransactionPersistenceRetainsSixDecimalsAndOriginalIntegerCapacity() {
        var txn = portfolios.transactions(1).findById(1L);
        txn.setUnits(new BigDecimal("999999999999999.123456"));
        txn.setAvgPrice(new BigDecimal("100.123456"));
        var saved = portfolios.transactions(1).save(txn);
        assertEquals(txn.getUnits(), saved.getUnits());
        assertEquals(txn.getAvgPrice(), saved.getAvgPrice());
        saved.setUnits(new BigDecimal("1.000001"));
        assertEquals(saved.getUnits(), portfolios.transactions(1).update(saved).getUnits());
    }

    @Test void completedOrderPreservesDatesIdentifiersDecimalsAndRawJson() {
        var order = order(1L, "COMPLETE");
        order.setMutualFundTxnId(1L);
        order.setSettlementId("000123");
        order.setFolioNumber("00001234/05");
        order.setExchangeOrderId("000009876543210987654321");
        order.setUnits(new BigDecimal("12.345678"));
        order.setAvgPrice(new BigDecimal("81.000123"));
        order.setTag("{\"tag\": [\"coinandroid\"]}");
        order.setRemarks("Processing information");
        var saved = portfolios.orders(1).save(order);
        assertEquals(order.getSettlementId(), saved.getSettlementId());
        assertEquals(order.getFolioNumber(), saved.getFolioNumber());
        assertEquals(order.getExchangeOrderId(), saved.getExchangeOrderId());
        assertEquals(order.getTradeDate(), saved.getTradeDate());
        assertEquals(order.getOrderedAt(), saved.getOrderedAt());
        assertEquals(order.getAmount(), saved.getAmount());
        assertEquals(order.getUnits(), saved.getUnits());
        assertEquals(order.getAvgPrice(), saved.getAvgPrice());
        assertEquals(order.getTag(), saved.getTag());
        assertEquals(order.getRemarks(), saved.getRemarks());
        assertEquals("BUY", saved.getTransactionType());
        assertEquals("COMPLETE", saved.getStatus());
        assertEquals(1L, saved.getMutualFundTxnId());
        assertNotNull(saved.getCreateDate());
        assertNotNull(saved.getUpdateDate());
        // Source trade date is not substituted into the existing transaction timestamp.
        assertEquals(LocalDateTime.of(2025, 1, 2, 16, 30), portfolios.transactions(1).findById(1L).getTxnDate());
    }

    @Test void processingZerosAndMissingDetailsNeverCreateTransactionsOrHoldings() {
        var before = portfolios.transactions(1).getSummary();
        var order = order(1L, "PROCESSING");
        order.setUnits(BigDecimal.ZERO);
        order.setAvgPrice(BigDecimal.ZERO);
        order.setTag("coinandroidsip");
        var saved = portfolios.orders(1).save(order);
        assertNull(saved.getSettlementId());
        assertNull(saved.getFolioNumber());
        assertNull(saved.getMutualFundTxnId());
        assertEquals(new BigDecimal("0.000000"), saved.getUnits());
        assertEquals(new BigDecimal("0.000000"), saved.getAvgPrice());
        assertEquals("coinandroidsip", saved.getTag());
        saved.setStatus("HISTORICAL_OTHER_STATUS");
        saved.setTransactionType("HISTORICAL_OTHER_DIRECTION");
        saved.setUnits(null);
        saved.setAvgPrice(null);
        saved.setRemarks("Source explanation");
        var updated = portfolios.orders(1).update(saved);
        assertEquals(saved.getStatus(), updated.getStatus());
        assertEquals(saved.getTransactionType(), updated.getTransactionType());
        assertNull(updated.getUnits());
        assertNull(updated.getAvgPrice());
        assertEquals(before.getTotalValue(), portfolios.transactions(1).getSummary().getTotalValue());
        assertEquals(before.getTotalUnits(), portfolios.transactions(1).getSummary().getTotalUnits());
        assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM mutual_fund_txn", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM mutual_fund_value", Integer.class));
        var incomplete = new MutualFundOrder();
        incomplete.setMutualFundId(1L);
        assertNull(portfolios.orders(1).save(incomplete).getTradeDate());
    }

    @Test void repeatedReferencesAreAllowedWithinAndAcrossAccountsAndPaginationIsScoped() {
        for (long fund : new long[]{1, 1, 2, 3}) {
            var order = order(fund, "COMPLETE");
            order.setSettlementId("shared-settlement");
            order.setExchangeOrderId("shared-exchange-reference");
            order.setTransactionType("SELL");
            portfolios.orders(fund == 3 ? 2 : 1).save(order);
        }
        assertEquals(2, portfolios.orders(1).countByMutualFundId(1L));
        assertEquals(1, portfolios.orders(1).findByMutualFundId(1L, PageRequest.of(1, 1)).size());
        assertEquals(0, portfolios.orders(1).countByMutualFundId(3L));
        assertTrue(portfolios.orders(1).findByMutualFundId(3L, PAGE).isEmpty());
    }

    @Test void ownershipAndSameFundTransactionReferenceAreEnforcedOnWrites() {
        var saved = portfolios.orders(1).save(order(1L, "PROCESSING"));
        assertThrows(EmptyResultDataAccessException.class, () -> portfolios.orders(2).findById(saved.getMutualFundOrderId()));
        assertThrows(EmptyResultDataAccessException.class, () -> portfolios.orders(2).update(saved));
        assertThrows(EmptyResultDataAccessException.class, () -> portfolios.orders(1).save(order(3L, "PROCESSING")));
        assertThrows(EmptyResultDataAccessException.class, () -> portfolios.orders(1).save(order(4L, "PROCESSING")));
        saved.setMutualFundId(3L);
        assertThrows(EmptyResultDataAccessException.class, () -> portfolios.orders(1).update(saved));
        saved.setMutualFundId(1L);
        for (long foreignTxn : new long[]{2, 3, 999}) {
            saved.setMutualFundTxnId(foreignTxn);
            assertThrows(DataIntegrityViolationException.class, () -> portfolios.orders(1).update(saved));
        }
        assertNull(portfolios.orders(1).findById(saved.getMutualFundOrderId()).getMutualFundTxnId());
        var invalid = order(1L, "COMPLETE");
        invalid.setMutualFundTxnId(3L);
        assertThrows(DataIntegrityViolationException.class, () -> portfolios.orders(1).save(invalid));
    }

    private MutualFundOrder order(Long fundId, String status) {
        var order = new MutualFundOrder();
        order.setMutualFundId(fundId);
        order.setStatus(status);
        order.setTransactionType("BUY");
        order.setTradeDate(LocalDate.parse("03/02/2025", DateTimeFormatter.ofPattern("dd/MM/uuuu")));
        order.setOrderedAt(LocalTime.parse("12:07 AM", DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH)));
        order.setAmount(new BigDecimal("1000.25"));
        return order;
    }
}
