package com.trading.security;

import java.nio.charset.StandardCharsets;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:ownership;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=none", "spring.flyway.enabled=false"
})
@AutoConfigureMockMvc
class SessionAndOwnershipTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired com.trading.repository.UserPortfolioRepositoryFactory portfolios;
    private static final String PASSWORD = "a-long-password";
    private static final String[] PATHS = {"/api/broker-accounts", "/api/mutual-funds", "/api/mutual-fund-txns", "/api/mutual-fund-values"};
    private static final String FUND = "{\"brokerAccountId\":1,\"mutualFundName\":\"My fund\"}";
    private static final String TXN = "{\"mutualFundId\":1,\"amount\":10,\"units\":1,\"avgPrice\":10,\"txnDate\":\"2026-09-07T00:00:00\",\"transactionType\":\"BUY\"}";
    private static final String VALUE = "{\"mutualFundId\":1,\"totalValue\":15,\"valueAsOfDate\":\"2026-09-07T00:00:00\"}";

    @BeforeEach
    void seed() throws Exception {
        jdbc.execute("DROP ALL OBJECTS");
        for (String file : new String[]{"V3__application_schema.sql", "V5__create_app_user.sql", "V6__portfolio_ownership.sql", "V7__password_recovery.sql"}) {
            String sql = new ClassPathResource("db/migration/" + file).getContentAsString(StandardCharsets.UTF_8)
                    .replace(" ON CONFLICT (name) DO NOTHING", ""); // H2 lacks this PostgreSQL syntax.
            for (String statement : sql.split(";")) if (!statement.isBlank()) jdbc.execute(statement);
        }
        String hash = new BCryptPasswordEncoder(4).encode(PASSWORD);
        for (String username : new String[]{"alice", "bob", "disabled"}) {
            jdbc.update("INSERT INTO users(username,email,password,enabled) VALUES(?,?,?,?)", username, username + "@example.com", hash, !username.equals("disabled"));
        }
        jdbc.update("INSERT INTO user_roles SELECT id, 1 FROM users");
        for (int n = 1; n <= 3; n++) {
            jdbc.update("INSERT INTO mutual_fund_broker_account(broker_name,account_id,owner_user_id) VALUES(?,?,?)", "Broker " + n, "Account " + n, n == 3 ? null : n);
            jdbc.update("INSERT INTO mutual_fund(broker_account_id,mutual_fund_name) VALUES(?,?)", n, "Fund " + n);
            jdbc.update("INSERT INTO mutual_fund_txn(mutual_fund_id,amount,txn_date,units,avg_price,txn_type) VALUES(?,?,CURRENT_TIMESTAMP,1,10,'BUY')", n, n * 10);
            jdbc.update("INSERT INTO mutual_fund_value(mutual_fund_id,total_value,value_as_of_date) VALUES(?,?,CURRENT_TIMESTAMP)", n, n * 15);
        }
    }

    private MockHttpServletRequestBuilder secured(MockHttpServletRequestBuilder request, MockHttpSession session) throws Exception {
        var result = mvc.perform(get("/api/auth/csrf").session(session)).andExpect(status().isOk()).andReturn();
        var token = json.readTree(result.getResponse().getContentAsString());
        return request.session(session).header(token.get("headerName").asString(), token.get("token").asString());
    }

    private MockHttpSession login(String username) throws Exception {
        var session = new MockHttpSession();
        mvc.perform(secured(post("/api/auth/login").param("username", username).param("password", PASSWORD), session))
                .andExpect(status().isNoContent());
        return session;
    }

    @Test void fundInvestmentTotalsIncludeAllTransactionsAndOnlyOwnedFunds() throws Exception {
        jdbc.update("INSERT INTO mutual_fund(broker_account_id,mutual_fund_name) VALUES(1,'Fund 1')");
        for (int n = 0; n < 101; n++) {
            jdbc.update("INSERT INTO mutual_fund_txn(mutual_fund_id,amount,txn_date,units,avg_price,txn_type) VALUES(1,1,CURRENT_TIMESTAMP,1,1,'BUY')");
        }
        jdbc.update("INSERT INTO mutual_fund_txn(mutual_fund_id,amount,txn_date,units,avg_price,txn_type) VALUES(1,5,CURRENT_TIMESTAMP,1,5,'SELL')");
        mvc.perform(get("/api/mutual-fund-txns/summary/by-fund")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/mutual-fund-txns/summary/by-fund").session(login("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].mutualFundId").value(1))
                .andExpect(jsonPath("$[0].totalInvested").value(106))
                .andExpect(jsonPath("$[1].mutualFundId").value(4))
                .andExpect(jsonPath("$[1].totalInvested").value(0));
        mvc.perform(get("/api/mutual-fund-txns/summary/by-fund").session(login("bob")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].mutualFundId").value(2))
                .andExpect(jsonPath("$[0].totalInvested").value(20));
        var unscoped = new com.trading.repository.impl.MutualFundTxnRepositoryImpl(jdbc).getFundInvestments();
        assertEquals(4, unscoped.size());
        assertEquals(0, unscoped.getFirst().totalInvested().compareTo(new java.math.BigDecimal("106")));
    }

    @Test void anonymousCannotReadOrWritePortfolio() throws Exception {
        for (String path : PATHS) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(post(path).contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
        }
        mvc.perform(get("/api/mutual-fund-txns/summary")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test void loginRestoresSessionAndLogoutInvalidatesIt() throws Exception {
        var session = new MockHttpSession();
        String oldId = session.getId();
        mvc.perform(secured(post("/api/auth/login").param("username", "alice").param("password", PASSWORD), session))
                .andExpect(status().isNoContent());
        assertNotEquals(oldId, session.getId());
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1)).andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_USER")).andExpect(jsonPath("$.password").doesNotExist());
        mvc.perform(secured(post("/api/auth/logout"), session)).andExpect(status().isNoContent());
        assertTrue(session.isInvalid());
        mvc.perform(get(PATHS[0])).andExpect(status().isUnauthorized());
    }

    @Test void badCredentialsAndDisabledAccountsHaveSameGenericFailure() throws Exception {
        for (String username : new String[]{"unknown", "disabled", "alice"}) {
            mvc.perform(secured(post("/api/auth/login").param("username", username)
                    .param("password", username.equals("alice") ? "wrong" : PASSWORD), new MockHttpSession()))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Invalid username or password."));
        }
    }

    @Test void csrfRequiredForLoginRegistrationAndAuthenticatedWrites() throws Exception {
        mvc.perform(post("/api/auth/login").param("username", "alice").param("password", PASSWORD)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/register").contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
        var session = login("alice");
        mvc.perform(delete(PATHS[2] + "/1").session(session)).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/logout").session(session)).andExpect(status().isForbidden());
    }

    @Test void eachUserSeesOnlyOwnListsAndTotalsAndUnassignedDataIsHidden() throws Exception {
        for (int owner = 1; owner <= 2; owner++) {
            var session = login(owner == 1 ? "alice" : "bob");
            for (String path : PATHS) {
                mvc.perform(get(path).session(session)).andExpect(status().isOk())
                        .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content.length()").value(1));
                mvc.perform(get(path + "/" + owner).session(session)).andExpect(status().isOk());
                mvc.perform(get(path + "/" + (3 - owner)).session(session)).andExpect(status().isNotFound());
                mvc.perform(get(path + "/3").session(session)).andExpect(status().isNotFound());
                mvc.perform(get(path + "?page=1&size=1").session(session)).andExpect(status().isOk())
                        .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content.length()").value(0));
            }
            mvc.perform(get("/api/mutual-fund-txns/summary").session(session)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalValue").value(owner * 10));
            mvc.perform(get("/api/mutual-fund-txns/summary?mutualFundId=" + (3 - owner)).session(session))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalValue").value(0));
            for (String path : new String[]{"/api/mutual-funds/broker-account/", "/api/mutual-fund-txns/mutual-fund/", "/api/mutual-fund-values/mutual-fund/"}) {
                mvc.perform(get(path + (3 - owner)).session(session)).andExpect(status().isOk())
                        .andExpect(jsonPath("$.totalElements").value(0)).andExpect(jsonPath("$.content.length()").value(0));
            }
        }
    }

    @Test void crossUserUpdatesDeletesAndParentReassignmentAreRejected() throws Exception {
        var session = login("alice");
        String[] bodies = {"{\"brokerName\":\"Changed\",\"accountId\":\"X\"}", FUND, TXN, VALUE};
        for (int n = 0; n < PATHS.length; n++) {
            mvc.perform(secured(put(PATHS[n] + "/2").contentType("application/json").content(bodies[n]), session)).andExpect(status().isNotFound());
            mvc.perform(secured(delete(PATHS[n] + "/2"), session)).andExpect(status().isNotFound());
            if (n > 0) {
                String foreignParent = bodies[n].replace("Id\":1", "Id\":2");
                mvc.perform(secured(post(PATHS[n]).contentType("application/json").content(foreignParent), session)).andExpect(status().isBadRequest());
                mvc.perform(secured(put(PATHS[n] + "/1").contentType("application/json").content(foreignParent), session)).andExpect(status().isBadRequest());
            }
        }
        assertEquals("Fund 2", jdbc.queryForObject("SELECT mutual_fund_name FROM mutual_fund WHERE mutual_fund_id=2", String.class));
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM mutual_fund_txn", Integer.class));
    }

    @Test void ownedCreatesUpdatesAndDeletesWorkAndOwnerCannotBeSpoofed() throws Exception {
        var session = login("alice");
        mvc.perform(secured(post(PATHS[0]).contentType("application/json")
                .content("{\"id\":2,\"brokerName\":\"New\",\"accountId\":\"New\",\"ownerUserId\":2}"), session))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(4));
        assertEquals(1L, jdbc.queryForObject("SELECT owner_user_id FROM mutual_fund_broker_account WHERE broker_account_id=4", Long.class));
        String[] bodies = {"{\"brokerName\":\"Updated\",\"accountId\":\"Y\"}", FUND, TXN, VALUE};
        for (int n = 1; n < PATHS.length; n++) {
            mvc.perform(secured(post(PATHS[n]).contentType("application/json").content(bodies[n]), session)).andExpect(status().isCreated());
        }
        for (int n = PATHS.length - 1; n >= 0; n--) {
            mvc.perform(secured(put(PATHS[n] + "/4").contentType("application/json").content(bodies[n]), session)).andExpect(status().isOk());
            mvc.perform(secured(delete(PATHS[n] + "/4"), session)).andExpect(status().isNoContent());
        }
    }

    @Test void repositoryGuardsRejectForeignParentsWithoutControllerValidation() {
        var fund = new com.trading.model.MutualFund();
        fund.setBrokerAccountId(2L);
        fund.setMutualFundName("Attempt");
        assertThrows(org.springframework.dao.EmptyResultDataAccessException.class, () -> portfolios.funds(1).save(fund));
        var txn = portfolios.transactions(1).findById(1L);
        txn.setMutualFundId(2L);
        assertThrows(org.springframework.dao.EmptyResultDataAccessException.class, () -> portfolios.transactions(1).save(txn));
        assertThrows(org.springframework.dao.EmptyResultDataAccessException.class, () -> portfolios.transactions(1).update(txn));
        var value = portfolios.values(1).findById(1L);
        value.setMutualFundId(2L);
        assertThrows(org.springframework.dao.EmptyResultDataAccessException.class, () -> portfolios.values(1).save(value));
        assertThrows(org.springframework.dao.EmptyResultDataAccessException.class, () -> portfolios.values(1).update(value));
        fund.setMutualFundId(1L);
        assertThrows(org.springframework.dao.EmptyResultDataAccessException.class, () -> portfolios.funds(1).update(fund));
        assertEquals(1L, portfolios.funds(1).findById(1L).getBrokerAccountId());
        assertEquals(1L, portfolios.transactions(1).findById(1L).getMutualFundId());
        assertEquals(1L, portfolios.values(1).findById(1L).getMutualFundId());
    }

    @Test void lockedExpiredAndCredentialExpiredAccountsCannotLogin() throws Exception {
        for (String flag : new String[]{"account_non_locked", "account_non_expired", "credentials_non_expired"}) {
            jdbc.update("UPDATE users SET " + flag + " = FALSE WHERE username='alice'");
            mvc.perform(secured(post("/api/auth/login").param("username", "alice").param("password", PASSWORD), new MockHttpSession()))
                    .andExpect(status().isUnauthorized());
            jdbc.update("UPDATE users SET " + flag + " = TRUE WHERE username='alice'");
        }
    }

    @Test void analyticsReturnsAllOwnedFundsAndCompleteChronologicalHistory() throws Exception {
        for (int i = 0; i < 105; i++) {
            jdbc.update("INSERT INTO mutual_fund(broker_account_id,mutual_fund_name) VALUES(1,?)", "Extra " + i);
            jdbc.update("INSERT INTO mutual_fund_value(mutual_fund_id,total_value,value_as_of_date) VALUES(1,?,?)",
                    i, java.time.LocalDateTime.of(2020, 1, 1, 0, 0).plusDays(104 - i));
        }
        var session = login("alice");
        mvc.perform(get("/api/analytics/funds").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(106))
                .andExpect(jsonPath("$[0].brokerAccountId").value(1));
        mvc.perform(get("/api/analytics/funds/1/values").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(106))
                .andExpect(jsonPath("$[0].totalValue").value(104))
                .andExpect(jsonPath("$[105].totalValue").value(15));
        // Shared management endpoints retain their existing pagination contract.
        mvc.perform(get("/api/mutual-funds").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(106)).andExpect(jsonPath("$.content.length()").value(20));
        var bob = login("bob");
        mvc.perform(get("/api/analytics/funds").session(bob)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].mutualFundId").value(2));
    }

    @Test void analyticsRequiresLoginAndHidesForeignUnknownAndUnassignedHistory() throws Exception {
        mvc.perform(get("/api/analytics/funds")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/analytics/funds/1/values")).andExpect(status().isUnauthorized());
        var session = login("alice");
        for (long fundId : new long[]{2, 3, 999}) {
            mvc.perform(get("/api/analytics/funds/" + fundId + "/values").session(session))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        }
        mvc.perform(get("/api/analytics/funds/0/values").session(session)).andExpect(status().isBadRequest());
        jdbc.update("DELETE FROM mutual_fund_value WHERE mutual_fund_id=1");
        mvc.perform(get("/api/analytics/funds/1/values").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test void registrationRemainsPublicButDoesNotLogUserIn() throws Exception {
        var session = new MockHttpSession();
        mvc.perform(secured(post("/api/auth/register").contentType("application/json")
                .content("{\"username\":\"newuser\",\"email\":\"new@example.com\",\"password\":\"a-long-password\",\"roles\":[\"ROLE_ADMIN\"]}"), session))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.roles[0]").value("ROLE_USER"));
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
        var loggedIn = login("newuser");
        mvc.perform(get(PATHS[0]).session(loggedIn)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }
}
