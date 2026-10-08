package com.trading.upload;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.net.*;
import java.net.http.*;
import java.util.*;
import javax.sql.DataSource;
import com.trading.model.coin.CoinRow;
import com.trading.support.PostgresTestDatabase;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=none", "batch.coin.chunk-size=1" })
@AutoConfigureMockMvc
class CoinUploadPostgresIntegrationTest {
    static final String ROUTE = "/api/mutual-fund-txns/zerodha-upload";
    static final String PASSWORD = "synthetic-test-password";
    static final Path DIRECTORY = temporaryDirectory();
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource source;
    @Autowired ObjectMapper json;
    @Autowired ApplicationContext context;
    @LocalServerPort int port;
    static Path temporaryDirectory() {
        try { return Files.createTempDirectory("coin-upload-test-").toRealPath(); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.configure(registry, "coin_upload_test");
        registry.add("app.upload.coin-import-directory", () -> DIRECTORY.resolve("managed").toString());
        registry.add("app.upload.zerodha-transactions-directory", () -> DIRECTORY.resolve("legacy").toString());
    }
    @BeforeEach void setup() throws Exception {
        jdbc.execute("DROP SCHEMA public CASCADE"); jdbc.execute("CREATE SCHEMA public");
        for (String file : List.of("V2__spring_batch_schema.sql", "V3__application_schema.sql", "V5__create_app_user.sql",
                "V6__portfolio_ownership.sql", "V7__password_recovery.sql", "V8__user_profile_hint.sql", "V9__mutual_fund_orders.sql",
                "V10__move_folio_to_mutual_fund.sql", "V11__move_order_metadata_to_transactions.sql",
                "V12__unique_broker_account_per_owner.sql", "V13__relax_mutual_fund_isin_length.sql"))
            jdbc.execute(new ClassPathResource("db/migration/" + file).getContentAsString(StandardCharsets.UTF_8));
        var migration = Flyway.configure().dataSource(source).baselineOnMigrate(true).baselineVersion("13").target("14").load();
        assertEquals(1, migration.migrate().migrationsExecuted);
        String password = new BCryptPasswordEncoder(4).encode(PASSWORD);
        jdbc.update("INSERT INTO users(id,username,email,password) VALUES(1,'alice','alice@example.invalid',?),(2,'bob','bob@example.invalid',?)", password, password);
        jdbc.update("INSERT INTO user_roles(user_id,role_id) VALUES(1,1),(2,1)");
        jdbc.update("INSERT INTO mutual_fund_broker_account(broker_account_id,broker_name,account_id,owner_user_id) VALUES(1,'Zerodha','SYNTHETIC',1),(2,'Zerodha','SYNTHETIC',2)");
        jdbc.update("INSERT INTO mutual_fund(mutual_fund_id,broker_account_id,mutual_fund_name,isin,folio_number) OVERRIDING SYSTEM VALUE VALUES(1,1,'Synthetic Fund','INFTEST00001','0001'),(2,2,'Synthetic Fund','INFTEST00001','0001')");
    }
    @AfterAll static void removeFiles() throws Exception { org.springframework.util.FileSystemUtils.deleteRecursively(DIRECTORY); }
    org.springframework.test.web.servlet.RequestBuilder secured(AbstractMockHttpServletRequestBuilder<?> request, MockHttpSession session) throws Exception {
        var result = mvc.perform(get("/api/auth/csrf").session(session)).andExpect(status().isOk()).andReturn();
        var token = json.readTree(result.getResponse().getContentAsString());
        return request.session(session).header(token.get("headerName").asString(), token.get("token").asString());
    }
    MockHttpSession login(String user) throws Exception {
        var session = new MockHttpSession();
        mvc.perform(secured(post("/api/auth/login").param("username", user).param("password", PASSWORD), session)).andExpect(status().isNoContent());
        return session;
    }
    Map<String,Object> options(long account, String policy) {
        return new LinkedHashMap<>(Map.of("brokerAccountId", account, "periodStart", "2026-10-01", "periodEnd", "2026-10-31",
                "dateFormat", "dd/MM/uuuu", "postingPolicy", policy, "fundOverrides", Map.of("1", account)));
    }
    String row(String id, String status) {
        return "SYNTHETIC,INFTEST00001,Synthetic Fund,,BUY," + (status.equals("COMPLETE") ? "0007" : "")
                + ",01/10/2026,03:25 PM," + (status.equals("COMPLETE") ? "0001" : "")
                + ",100," + (status.equals("COMPLETE") ? "1.123456,89.010001" : "0,0") + "," + status + "," + id + ",  note  ,{\"source\":[\"synthetic\"]}";
    }
    byte[] csv(String... rows) { return (String.join(",", CoinRow.HEADERS) + "\r\n" + String.join("\r\n", rows) + "\r\n").getBytes(StandardCharsets.UTF_8); }
    org.springframework.test.web.servlet.ResultActions upload(MockHttpSession session, byte[] bytes, Map<String,Object> options) throws Exception {
        var request = multipart(ROUTE).file(new MockMultipartFile("file", "C:\\exports\\synthetic.csv", "text/csv", bytes));
        if (options != null) request.file(new MockMultipartFile("options", "", "application/json", json.writeValueAsBytes(options)));
        return mvc.perform(secured(request, session));
    }
    long count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class); }
    @Test void importsCompletesAndReplaysThroughAuthenticatedEndpointAndPreservesRootContext() throws Exception {
        var session = login("alice"); var options = options(1, "TRADE_DATE_ORDER_TIME");
        upload(session, csv(row("00001", "PROCESSING")), options).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED")).andExpect(jsonPath("$.originalFilename").value("synthetic.csv"))
                .andExpect(jsonPath("$.importResult.inserted").value(1)).andExpect(jsonPath("$.importResult.posted").value(0));
        long order = jdbc.queryForObject("SELECT mutual_fund_order_id FROM mutual_fund_order", Long.class);
        for (int attempt = 0; attempt < 2; attempt++) upload(session, csv(row("00001", "COMPLETE")), options)
                .andExpect(status().isOk()).andExpect(jsonPath("$.importResult.updated").value(1)).andExpect(jsonPath("$.importResult.posted").value(1));
        assertEquals(1, count("mutual_fund_order")); assertEquals(1, count("mutual_fund_txn")); assertEquals(2, count("coin_import_file"));
        assertEquals(order, jdbc.queryForObject("SELECT mutual_fund_order_id FROM mutual_fund_order", Long.class));
        assertEquals("0007", jdbc.queryForObject("SELECT settlement_id FROM mutual_fund_txn", String.class));
        assertEquals("  note  ", jdbc.queryForObject("SELECT remarks FROM mutual_fund_txn", String.class));
        assertEquals(java.sql.Timestamp.valueOf("2026-10-01 15:25:00"), jdbc.queryForObject("SELECT txn_date FROM mutual_fund_txn", java.sql.Timestamp.class));
        assertEquals(2, count("batch_job_instance")); assertEquals(0, count("trade_records")); assertEquals(0, count("ledger_records"));
        assertTrue(context.getBeansOfType(org.springframework.batch.core.job.Job.class).isEmpty());
        assertTrue(context.getBeansOfType(org.springframework.batch.core.repository.JobRepository.class).isEmpty());
        assertFalse(((com.zaxxer.hikari.HikariDataSource) source).isClosed());
        mvc.perform(get("/api/mutual-fund-txns/summary").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.totalValue").value(100));
    }
    @Test void validatesEveryBadRecordAndReturnsSafeFieldErrorsWithoutStaging() throws Exception {
        var response = upload(login("alice"), csv(row("00001", "COMPLETE").replace(",100,", ",private-invalid-amount,"),
                row("00002", "COMPLETE").replace("01/10/2026", "31/02/2026")), options(1, "ORDER_ONLY"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.fieldErrors['record[1].amount']").value("INVALID_VALUE"))
                .andExpect(jsonPath("$.fieldErrors['record[2].trade_date']").value("INVALID_VALUE")).andReturn().getResponse().getContentAsString();
        assertFalse(response.contains("private-invalid-amount")); assertEquals(0, count("coin_import_file")); assertEquals(0, count("mutual_fund_order"));
    }
    @Test void rejectsForeignAccountEvenWhenBodyTriesToOverrideOwner() throws Exception {
        var options = options(2, "ORDER_ONLY"); options.put("ownerUserId", 2);
        upload(login("alice"), csv(row("00001", "COMPLETE")), options).andExpect(status().isBadRequest());
        assertEquals(0, count("coin_import_file"));
        upload(login("bob"), csv(row("00001", "COMPLETE")), options(2, "ORDER_ONLY")).andExpect(status().isOk());
        assertEquals(2, jdbc.queryForObject("SELECT owner_user_id FROM coin_import_file", Long.class));
    }
    @Test void keepsFileOnlyCompatibilityAndRequiresSessionAndCsrf() throws Exception {
        byte[] bytes = "legacy,staged\n".getBytes(StandardCharsets.UTF_8);
        mvc.perform(multipart(ROUTE).file(new MockMultipartFile("file", "a.csv", "text/csv", bytes))).andExpect(status().isUnauthorized());
        var session = login("alice");
        mvc.perform(multipart(ROUTE).file(new MockMultipartFile("file", "a.csv", "text/csv", bytes)).session(session)).andExpect(status().isForbidden());
        var result = upload(session, bytes, null).andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("UPLOADED"))
                .andReturn().getResponse().getContentAsString();
        String id = json.readTree(result).get("uploadId").asString();
        assertArrayEquals(bytes, Files.readAllBytes(DIRECTORY.resolve("legacy/1/" + id + ".csv")));
        assertEquals(0, count("coin_import_file")); assertEquals(0, count("batch_job_instance"));
    }
    @Test void rejectsCoveredNewOrdersAndChangedReplayOptions() throws Exception {
        var session = login("alice"); var options = options(1, "ORDER_ONLY"); var bytes = csv(row("00001", "COMPLETE"));
        upload(session, bytes, options).andExpect(status().isOk());
        upload(session, csv(row("00002", "COMPLETE")), options).andExpect(status().isConflict());
        upload(session, bytes, options(1, "TRADE_DATE_MIDNIGHT")).andExpect(status().isConflict());
        assertEquals(1, count("mutual_fund_order")); assertEquals(1, count("coin_import_period"));
    }
    @Test void requiresValidOptionsAndEnforcesServiceSizeLimit() throws Exception {
        var session = login("alice");
        upload(session, csv(row("00001", "COMPLETE")), Map.of()).andExpect(status().isBadRequest());
        var options = options(1, "ORDER_ONLY"); options.put("periodStart", "2026-11-01");
        upload(session, csv(row("00001", "COMPLETE")), options).andExpect(status().isBadRequest());
        upload(session, new byte[10 * 1024 * 1024 + 1], options(1, "ORDER_ONLY")).andExpect(status().isPayloadTooLarge());
        mvc.perform(secured(multipart(ROUTE), session)).andExpect(status().isBadRequest());
        assertEquals(0, count("coin_import_file"));
    }
    @Test void exposesMissingSchemaAsUnavailableWithoutPrivateDatabaseDetails() throws Exception {
        jdbc.execute("ALTER TABLE mutual_fund_broker_account DROP CONSTRAINT uq_broker_account_name_account_owner");
        upload(login("alice"), csv(row("00001", "COMPLETE")), options(1, "ORDER_ONLY"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.message").value("Coin import database prerequisites are not ready."));
        assertEquals(0, count("coin_import_file"));
    }
    @Test void failedHttpImportCanResumeAndDoesNotExposeSqlRows() throws Exception {
        jdbc.execute("ALTER TABLE coin_order_identity ADD CONSTRAINT fail_upload CHECK(exchange_order_id <> '00002')");
        var session = login("alice"); var bytes = csv(row("00001", "COMPLETE"), row("00002", "COMPLETE"));
        var response = upload(session, bytes, options(1, "ORDER_ONLY")).andExpect(status().isInternalServerError())
                .andReturn().getResponse().getContentAsString();
        assertFalse(response.contains("00002")); assertFalse(response.contains("Failing row")); assertEquals(1, count("mutual_fund_order"));
        jdbc.execute("ALTER TABLE coin_order_identity DROP CONSTRAINT fail_upload");
        upload(session, bytes, options(1, "ORDER_ONLY")).andExpect(status().isOk()).andExpect(jsonPath("$.importResult.inserted").value(2));
        assertEquals(2, count("mutual_fund_order")); assertEquals(1, count("batch_job_instance")); assertEquals(2, count("batch_job_execution"));
    }
    @Test void realHttpMultipartUploadRunsTheJobAndRejectsMissingCsrf() throws Exception {
        var cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        var client = HttpClient.newBuilder().cookieHandler(cookies).build();
        String base = "http://localhost:" + port;
        var token = json.readTree(client.send(HttpRequest.newBuilder(URI.create(base + "/api/auth/csrf")).GET().build(), HttpResponse.BodyHandlers.ofString()).body());
        var login = client.send(HttpRequest.newBuilder(URI.create(base + "/api/auth/login"))
                .header(token.get("headerName").asString(), token.get("token").asString()).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("username=alice&password=" + PASSWORD)).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(204, login.statusCode());
        token = json.readTree(client.send(HttpRequest.newBuilder(URI.create(base + "/api/auth/csrf")).GET().build(), HttpResponse.BodyHandlers.ofString()).body());
        String boundary = "coin-test-boundary";
        String body = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"synthetic.csv\"\r\nContent-Type: text/csv\r\n\r\n"
                + new String(csv(row("00001", "COMPLETE")), StandardCharsets.UTF_8) + "\r\n--" + boundary
                + "\r\nContent-Disposition: form-data; name=\"options\"\r\nContent-Type: application/json\r\n\r\n"
                + json.writeValueAsString(options(1, "ORDER_ONLY")) + "\r\n--" + boundary + "--\r\n";
        var request = HttpRequest.newBuilder(URI.create(base + ROUTE)).header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofString(body));
        assertEquals(403, client.send(request.build(), HttpResponse.BodyHandlers.ofString()).statusCode());
        var response = client.send(request.header(token.get("headerName").asString(), token.get("token").asString()).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        assertEquals("COMPLETED", json.readTree(response.body()).get("status").asString());
        assertEquals(1, count("mutual_fund_order")); assertEquals(0, count("mutual_fund_txn"));
    }
}
