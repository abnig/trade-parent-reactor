package com.trading.analytics;

import com.trading.repository.AnalyticsRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:portfolio_analytics;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=none", "spring.flyway.enabled=false"
})
@AutoConfigureMockMvc
class PortfolioAnalyticsTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired AnalyticsRepository repository;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    private static final LocalDate FROM = LocalDate.of(2026, 2, 1);
    private static final LocalDate TO = LocalDate.of(2026, 2, 28);
    private static final String PATH = "/api/analytics/portfolio";

    @BeforeEach void seed() throws Exception {
        resetSchema();
        for (String file : List.of("V3__application_schema.sql", "V5__create_app_user.sql", "V6__portfolio_ownership.sql",
                "V7__password_recovery.sql", "V8__user_profile_hint.sql")) {
            String sql = adaptMigration(new ClassPathResource("db/migration/" + file).getContentAsString(StandardCharsets.UTF_8));
            for (String statement : sql.split(";")) if (!statement.isBlank()) jdbc.execute(statement);
        }
        var hash = new BCryptPasswordEncoder(4).encode("analytics-test-password");
        for (String name : List.of("alice", "bob", "empty")) {
            jdbc.update("INSERT INTO users(username,email,password) VALUES(?,?,?)", name, name + "@example.com", hash);
        }
        jdbc.update("INSERT INTO user_roles SELECT id,1 FROM users");
        for (int id = 1; id <= 4; id++) {
            Integer ownerId = id == 4 ? null : Integer.valueOf(id <= 2 ? 1 : 2);
            jdbc.update("INSERT INTO mutual_fund_broker_account(broker_name,account_id,owner_user_id) VALUES(?,?,?)",
                    "Same Broker", "Account " + id, ownerId);
            jdbc.update("INSERT INTO mutual_fund(broker_account_id,mutual_fund_name) VALUES(?,?)", id, "Same Fund");
        }
        transaction(1, "2026-01-01 12:00:00", "100", "BUY");
        transaction(1, "2026-02-01 00:00:00", "50", "BUY");
        transaction(1, "2026-02-28 23:59:59", "30", "SELL");
        transaction(1, "2026-03-01 00:00:00", "999", "BUY");
        transaction(2, "2026-01-01 00:00:00", "200", "BUY");
        transaction(2, "2026-02-28 12:00:00", "50", "SELL");
        value(1, "2026-01-01 00:00:00", "100");
        value(1, "2026-02-10 00:00:00", "130");
        value(1, "2026-02-28 23:59:59", "149");
        value(1, "2026-02-28 23:59:59", "150");
        value(1, "2026-03-01 00:00:00", "1000");
        value(2, "2026-02-27 00:00:00", "250");
        for (int id : new int[]{3, 4}) {
            transaction(id, "2026-02-01 00:00:00", "5000", "BUY");
            value(id, "2026-02-28 00:00:00", "9000");
        }
    }

    void resetSchema() { jdbc.execute("DROP ALL OBJECTS"); }
    String adaptMigration(String sql) {
        return sql.replace(" ON CONFLICT (name) DO NOTHING", "")
                .replace("CREATE UNIQUE INDEX idx_users_email_case_insensitive ON users (LOWER(email))",
                    "ALTER TABLE users ADD COLUMN email_case_insensitive VARCHAR(100) GENERATED ALWAYS AS (LOWER(email)); CREATE UNIQUE INDEX idx_users_email_case_insensitive ON users(email_case_insensitive)");
    }
    private void transaction(long fund, String date, String amount, String type) {
        jdbc.update("INSERT INTO mutual_fund_txn(mutual_fund_id,txn_date,amount,units,avg_price,txn_type) VALUES(?,?,?,1,10,?)",
                fund, java.sql.Timestamp.valueOf(date), new BigDecimal(amount), type);
    }
    private void value(long fund, String date, String amount) {
        jdbc.update("INSERT INTO mutual_fund_value(mutual_fund_id,value_as_of_date,total_value) VALUES(?,?,?)",
                fund, java.sql.Timestamp.valueOf(date), new BigDecimal(amount));
    }
    private MockHttpSession login(String name) throws Exception {
        var session = new MockHttpSession();
        var response = mvc.perform(get("/api/auth/csrf").session(session)).andReturn().getResponse();
        var token = json.readTree(response.getContentAsString());
        mvc.perform(post("/api/auth/login").session(session).header(token.get("headerName").asString(), token.get("token").asString())
                .param("username", name).param("password", "analytics-test-password")).andExpect(status().isNoContent());
        return session;
    }
    private static void amount(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "Expected " + expected + ", got " + actual);
    }

    @Test void aggregatesFullOpeningHistoryAndLatestSnapshotsWithoutJoinMultiplication() {
        var result = repository.findPortfolio(1, FROM, TO);
        assertEquals(TO, result.asOfDate());
        assertEquals(2, result.fundCount());
        assertEquals(2, result.valuedFundCount());
        amount("400", result.totalValue());
        amount("400", result.knownValueTotal());
        amount("270", result.totalInvested());
        amount("350", result.totalBought());
        amount("130", result.gainLoss());
        amount("48.148148", result.returnPercentage());
        var first = result.funds().getFirst();
        amount("120", first.totalInvested());
        amount("150", first.totalValue());
        amount("30", first.gainLoss());
        amount("25", first.returnPercentage());
        amount("37.5", first.allocationPercentage());
        amount("42.857143", first.contributionPercentage());
        assertEquals(TO, first.valueAsOfDate());
        assertEquals(TO, result.funds().getLast().lastTransactionDate());
        assertEquals(LocalDate.of(2026, 2, 27), result.funds().getLast().valueAsOfDate());
    }

    @Test void brokerAccountsAndFundsRemainDistinctWhenNamesMatch() {
        var result = repository.findPortfolio(1, FROM, TO);
        assertEquals(2, result.brokerAccounts().size());
        assertNotEquals(result.funds().getFirst().mutualFundId(), result.funds().getLast().mutualFundId());
        amount("150", result.brokerAccounts().getFirst().totalValue());
        amount("250", result.brokerAccounts().getLast().totalValue());
        amount("62.5", result.brokerAccounts().getLast().allocationPercentage());
        assertEquals("Account 1", result.brokerAccounts().getFirst().accountId());
        assertEquals("Account 2", result.brokerAccounts().getLast().accountId());
    }

    @Test void fundsWithoutTransactionsRemainVisibleAndSharedBrokerBalancesAreSummed() {
        jdbc.update("INSERT INTO mutual_fund(broker_account_id,mutual_fund_name) VALUES(1,'Untraded')");
        value(5, "2026-02-20 00:00:00", "50");
        var result = repository.findPortfolio(1, FROM, TO);
        assertEquals(3, result.fundCount());
        amount("450", result.totalValue());
        amount("270", result.totalInvested());
        var broker = result.brokerAccounts().getFirst();
        assertEquals(2, broker.fundCount());
        assertEquals(2, broker.valuedFundCount());
        amount("200", broker.totalValue());
        amount("120", broker.totalInvested());
        amount("0", result.funds().getLast().totalInvested());
        amount("0", result.funds().getLast().contributionPercentage());
        assertNull(result.funds().getLast().returnPercentage());
    }

    @Test void missingValuationsKeepKnownValuesButDoNotPretendPortfolioTotalsAreComplete() {
        jdbc.update("INSERT INTO mutual_fund(broker_account_id,mutual_fund_name) VALUES(1,'Unvalued')");
        transaction(5, "2026-01-01 00:00:00", "10", "BUY");
        var result = repository.findPortfolio(1, FROM, TO);
        assertEquals(3, result.fundCount());
        assertEquals(2, result.valuedFundCount());
        amount("400", result.knownValueTotal());
        amount("280", result.totalInvested());
        assertNull(result.totalValue());
        assertNull(result.gainLoss());
        assertNull(result.returnPercentage());
        assertTrue(result.funds().stream().allMatch(fund -> fund.allocationPercentage() == null));
        var missing = result.funds().getLast();
        assertNull(missing.totalValue());
        assertNull(missing.valueAsOfDate());
        assertNull(missing.gainLoss());
        assertNull(missing.returnPercentage());
        assertNotNull(missing.contributionPercentage());
        assertNull(result.brokerAccounts().getFirst().totalValue());
        amount("150", result.brokerAccounts().getFirst().knownValueTotal());
        amount("250", result.brokerAccounts().getLast().totalValue());
    }

    @Test void emptyTransactionRangesCarryInvestmentButDoNotReuseSnapshotsOutsideTheRange() {
        var result = repository.findPortfolio(1, LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 31));
        amount("1269", result.totalInvested());
        assertNull(result.totalValue());
        assertEquals(0, result.valuedFundCount());
        amount("0", result.knownValueTotal());
    }

    @Test void absentBoundsUseFullRecordedHistoryAndOwnerScopedLatestDate() {
        var result = repository.findPortfolio(1, null, null);
        amount("1250", result.totalValue());
        amount("1269", result.totalInvested());
        assertEquals(LocalDate.of(2026, 3, 1), result.asOfDate());
        amount("400", repository.findPortfolio(1, null, TO).totalValue());
        amount("1250", repository.findPortfolio(1, FROM, null).totalValue());
        amount("9000", repository.findPortfolio(2, FROM, TO).totalValue());
        assertEquals(1, repository.findPortfolio(2, FROM, TO).fundCount());
    }

    @Test void zeroAndNegativeNetInvestmentDoNotProducePercentageReturns() {
        transaction(1, "2026-02-28 23:59:59", "120", "SELL");
        transaction(2, "2026-02-28 23:59:59", "160", "SELL");
        var result = repository.findPortfolio(1, FROM, TO);
        amount("0", result.funds().getFirst().totalInvested());
        amount("-10", result.funds().getLast().totalInvested());
        assertNull(result.funds().getFirst().returnPercentage());
        assertNull(result.funds().getLast().returnPercentage());
        assertNull(result.returnPercentage());
        amount("410", result.gainLoss());
    }

    @Test void valuesWithoutPurchasesHaveNoContributionShareAndZeroValuesHaveNoAllocation() {
        jdbc.update("DELETE FROM mutual_fund_txn WHERE mutual_fund_id IN (1,2)");
        jdbc.update("UPDATE mutual_fund_value SET total_value=0 WHERE mutual_fund_id IN (1,2)");
        var result = repository.findPortfolio(1, FROM, TO);
        amount("0", result.totalValue());
        amount("0", result.totalInvested());
        assertTrue(result.funds().stream().allMatch(fund -> fund.allocationPercentage() == null && fund.contributionPercentage() == null));
        assertNull(result.returnPercentage());
    }

    @Test void emptyOwnersHaveEmptyCollectionsAndZeroTotals() {
        var result = repository.findPortfolio(3, null, null);
        assertEquals(0, result.fundCount());
        assertTrue(result.funds().isEmpty());
        assertTrue(result.brokerAccounts().isEmpty());
        amount("0", result.totalValue());
        amount("0", result.totalInvested());
        assertNull(result.returnPercentage());
        assertNull(result.asOfDate());
    }

    @Test void sameCalendarDateUsesHighestSnapshotIdConsistentlyWithFundAnalytics() {
        value(1, "2026-02-28 08:00:00", "160");
        amount("160", repository.findPortfolio(1, FROM, TO).funds().getFirst().totalValue());
    }

    @Test void invalidTransactionTypesFailRatherThanSilentlyContributingZero() {
        transaction(3, "2026-02-01 00:00:00", "100", "UNKNOWN");
        amount("400", repository.findPortfolio(1, FROM, TO).totalValue());
        transaction(1, "2026-02-01 00:00:00", "100", "UNKNOWN");
        var exception = assertThrows(org.springframework.dao.InvalidDataAccessApiUsageException.class,
                () -> repository.findPortfolio(1, FROM, TO));
        assertInstanceOf(IllegalStateException.class, exception.getMostSpecificCause());
        assertEquals("Portfolio history contains an invalid transaction type.", exception.getMostSpecificCause().getMessage());
    }

    @Test void negativeValuesAndPurchasesDoNotProduceMisleadingAllocationShares() {
        value(1, "2026-02-28 12:00:00", "-10");
        transaction(1, "2026-02-01 00:00:00", "-200", "BUY");
        var result = repository.findPortfolio(1, FROM, TO);
        assertTrue(result.funds().stream().allMatch(fund -> fund.allocationPercentage() == null && fund.contributionPercentage() == null));
        assertTrue(result.brokerAccounts().stream().allMatch(broker -> broker.allocationPercentage() == null));
    }

    @Test void endpointRequiresAuthenticationAndUsesOnlyTheAuthenticatedOwner() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mvc.perform(get(PATH).session(login("alice")).param("fromDate", "2026-02-01").param("toDate", "2026-02-28"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fundCount").value(2))
                .andExpect(jsonPath("$.totalValue").value(400)).andExpect(jsonPath("$.totalInvested").value(270))
                .andExpect(jsonPath("$.asOfDate").value("2026-02-28"))
                .andExpect(jsonPath("$.funds[0].allocationPercentage").value(37.5));
        mvc.perform(get(PATH).session(login("bob"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.fundCount").value(1)).andExpect(jsonPath("$.funds[0].mutualFundId").value(3));
        mvc.perform(get(PATH).session(login("empty"))).andExpect(status().isOk()).andExpect(jsonPath("$.funds").isEmpty());
    }

    @Test void endpointRejectsInvalidDatesRangesAndOwnershipOverrides() throws Exception {
        var session = login("alice");
        for (String date : List.of("2026-02-30", "2026-13-01", "0000-01-01", "bad", "2026-01-01T12:00:00", "10000-01-01")) {
            mvc.perform(get(PATH).session(session).param("fromDate", date)).andExpect(status().isBadRequest());
            mvc.perform(get(PATH).session(session).param("toDate", date)).andExpect(status().isBadRequest());
        }
        mvc.perform(get(PATH).session(session).param("fromDate", "2026-03-01").param("toDate", "2026-02-01"))
                .andExpect(status().isBadRequest());
        for (String key : List.of("ownerId", "userId", "mutualFundId")) {
            mvc.perform(get(PATH).session(session).param(key, "2")).andExpect(status().isBadRequest());
        }
    }
}
