package com.trading.coin;

import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import com.trading.model.coin.*;
import com.trading.repository.CoinImportRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.dao.DataIntegrityViolationException;
import static org.junit.jupiter.api.Assertions.*;

class CoinImportIntegrationTest {
    @TempDir Path directory;
    CoinPostgresFixture db;
    @BeforeEach void setup() throws Exception { directory = directory.toRealPath(); db = new CoinPostgresFixture(); }
    @AfterEach void cleanup() { if (db != null) db.close(); }
    AnnotationConfigApplicationContext context() {
        var context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of("batch.coin.chunk-size", "1")));
        context.registerBean("dataSource", javax.sql.DataSource.class, () -> db.source);
        context.register(CoinBatchConfiguration.class);
        context.refresh();
        return context;
    }
    CoinImportResult execute(CoinImportFile file) throws Exception {
        try (var context = context()) {
            return new CoinImportService(context.getBean(CoinImportRepository.class), context.getBean(JobOperator.class), context.getBean(Job.class)).execute(file, false);
        }
    }
    @SafeVarargs final CoinImportFile file(CoinImportOptions options, Map<String,String>... rows) throws Exception {
        byte[] bytes = CoinFixtures.csv(rows);
        return new CoinFileStore(directory.resolve("private")).store(CoinFixtures.file(bytes, options), bytes);
    }
    CoinImportOptions options(long owner, long account, LocalDate start, LocalDate end) {
        return new CoinImportOptions(owner, account, start, end, "dd/MM/uuuu", CoinImportOptions.PostingPolicy.ORDER_ONLY, Map.of());
    }
    void assertEmpty() {
        for (String table : List.of("coin_import_file", "coin_import_period", "coin_import_row", "coin_order_identity", "mutual_fund_order", "mutual_fund_txn", "batch_job_instance")) assertEquals(0, db.count(table), table);
    }
    @Test void stagesEveryFieldAndProjectsOrdersWithoutChangingPortfolioThenReplays() throws Exception {
        var a = CoinFixtures.fields("00001", "PROCESSING"); a.put("remarks", "  private note  ");
        var file = file(CoinFixtures.options(), a, CoinFixtures.fields("00002", "COMPLETE"));
        var result = execute(file);
        assertEquals("COMPLETED", result.status()); assertEquals(2, result.inserted()); assertEquals(0, result.posted());
        assertEquals(2, db.count("mutual_fund_order")); assertEquals(0, db.count("mutual_fund_txn"));
        var stored = db.jdbc.queryForMap("SELECT * FROM coin_import_row WHERE record_number=1");
        for (String field : CoinRow.HEADERS) assertEquals(a.get(field), stored.get("raw_" + field), field);
        assertArrayEquals(file.rows().getFirst().rawRecord(), (byte[]) stored.get("raw_record"));
        assertEquals("LOADED", db.jdbc.queryForObject("SELECT period_status FROM coin_import_period", String.class));
        assertEquals(result, execute(file)); assertEquals(1, db.count("batch_job_instance"));
        assertEquals(3, db.count("batch_step_execution"));
    }
    @Test void processingToCompleteUpdatesSameOrderAndPostsExactlyOnce() throws Exception {
        var options = CoinFixtures.options(CoinImportOptions.PostingPolicy.TRADE_DATE_ORDER_TIME);
        var initial = file(options, CoinFixtures.fields("00001", "PROCESSING"));
        execute(initial);
        long order = db.jdbc.queryForObject("SELECT mutual_fund_order_id FROM mutual_fund_order", Long.class);
        assertEquals(0, db.count("mutual_fund_txn"));
        var complete = CoinFixtures.fields("00001", "COMPLETE"); complete.put("amount", "125.00"); complete.put("remarks", "  done  ");
        var updated = file(options, complete);
        var result = execute(updated);
        assertEquals(1, result.updated()); assertEquals(1, result.posted());
        assertEquals(1, db.count("coin_import_period")); assertEquals(2, db.count("coin_import_file_fund"));
        assertEquals(order, db.jdbc.queryForObject("SELECT mutual_fund_order_id FROM mutual_fund_order", Long.class));
        assertEquals(1, db.count("mutual_fund_txn"));
        var transaction = db.jdbc.queryForMap("SELECT * FROM mutual_fund_txn");
        assertEquals("00001", transaction.get("exchange_order_id")); assertEquals("0001", transaction.get("settlement_id"));
        assertEquals("  done  ", transaction.get("remarks"));
        assertEquals(java.sql.Timestamp.valueOf("2026-10-01 00:00:00"), transaction.get("txn_date"));
        assertEquals(result, execute(updated)); assertEquals(1, db.count("mutual_fund_txn"));
        assertEquals("PROCESSING", db.jdbc.queryForObject("SELECT raw_status FROM coin_import_row ORDER BY import_row_id LIMIT 1", String.class));
        assertEquals("COMPLETE", db.jdbc.queryForObject("SELECT source_status FROM coin_order_identity", String.class));
    }
    @Test void completionSnapshotMayIncludeUnchangedFunds() throws Exception {
        var second = CoinFixtures.fields("00002", "COMPLETE"); second.put("scheme_name", "Second Fund"); second.put("isin", "INFTEST00002"); second.put("folio_number", "000456/01");
        execute(file(CoinFixtures.options(), CoinFixtures.fields("00001", "PROCESSING"), second));
        var result = execute(file(CoinFixtures.options(), CoinFixtures.fields("00001", "COMPLETE"), second));
        assertEquals(1, result.updated()); assertEquals(1, result.unchanged()); assertEquals(2, db.count("coin_import_period"));
    }
    @Test void rejectsNewOrdersOverlapsAndBackwardOrChangedCompletedSnapshots() throws Exception {
        execute(file(CoinFixtures.options(), CoinFixtures.fields("00001", "COMPLETE")));
        for (var row : List.of(CoinFixtures.fields("00002", "COMPLETE"), CoinFixtures.fields("00001", "PROCESSING")))
            assertThrows(CoinValidationException.class, () -> db.repository.prepare(file(CoinFixtures.options(), row), false));
        var changed = CoinFixtures.fields("00001", "COMPLETE"); changed.put("amount", "200");
        assertThrows(CoinValidationException.class, () -> db.repository.prepare(file(CoinFixtures.options(), changed), false));
        var overlapping = options(1, 1, LocalDate.of(2026,9,1), LocalDate.of(2026,10,1));
        assertThrows(CoinValidationException.class, () -> db.repository.prepare(file(overlapping, CoinFixtures.fields("00003", "COMPLETE")), false));
        assertEquals(1, db.count("coin_import_file")); assertEquals(1, db.count("mutual_fund_order"));
    }
    @Test void dryRunAndDatabaseValidationCollectEveryBadRecordWithoutWriting() throws Exception {
        var valid = file(CoinFixtures.options(), CoinFixtures.fields("00001", "COMPLETE"));
        assertEquals(0, db.repository.prepare(valid, true).importId()); assertEmpty();
        var a = CoinFixtures.fields("a", "PROCESSING"); a.put("client_id", "FOREIGN");
        var b = CoinFixtures.fields("b", "PROCESSING"); b.put("isin", "NO_MATCH");
        var error = assertThrows(CoinValidationException.class, () -> db.repository.prepare(file(CoinFixtures.options(), a, b), false));
        assertEquals(Set.of(1L, 2L), new HashSet<>(error.problems().stream().map(CoinValidationException.Problem::record).filter(n -> n > 0).toList()));
        assertEmpty();
    }
    @Test void foreignOwnerRejectedAndIndependentOwnersCanLoadSameSourcePeriod() throws Exception {
        var foreign = options(2, 1, LocalDate.of(2026,10,1), LocalDate.of(2026,10,31));
        assertThrows(IllegalArgumentException.class, () -> db.repository.prepare(file(foreign, CoinFixtures.fields("00001", "COMPLETE")), false)); assertEmpty();
        execute(file(CoinFixtures.options(), CoinFixtures.fields("00001", "COMPLETE")));
        execute(file(options(2, 2, foreign.periodStart(), foreign.periodEnd()), CoinFixtures.fields("00001", "COMPLETE")));
        assertEquals(2, db.count("coin_import_period")); assertEquals(2, db.count("coin_order_identity"));
    }
    @Test void explicitMappingRequiredForMissingFolioAndNeverCreatesMasters() throws Exception {
        var row = CoinFixtures.fields("00001", "PROCESSING"); row.put("folio_number", "");
        assertThrows(CoinValidationException.class, () -> db.repository.prepare(file(CoinFixtures.options(), row), false));
        var base = CoinFixtures.options();
        var mapped = new CoinImportOptions(1, 1, base.periodStart(), base.periodEnd(), base.dateFormat(), base.postingPolicy(), Map.of(1L, 1L));
        assertEquals(1, execute(file(mapped, row)).inserted()); assertEquals(3, db.count("mutual_fund"));
        assertEquals(mapped, db.repository.options(1, 1, 1));
        assertThrows(org.springframework.dao.EmptyResultDataAccessException.class, () -> db.repository.options(1, 2, 1));
    }
    @Test void manualTransactionsAndOrdersRequireReconciliation() throws Exception {
        db.jdbc.update("INSERT INTO mutual_fund_order(mutual_fund_id) VALUES(1)");
        var input = file(CoinFixtures.options(), CoinFixtures.fields("00001", "COMPLETE"));
        assertThrows(CoinValidationException.class, () -> db.repository.prepare(input, false));
        assertEquals(0, db.count("coin_import_file"));
        db.jdbc.update("DELETE FROM mutual_fund_order");
        db.jdbc.update("INSERT INTO mutual_fund_txn(mutual_fund_id,amount,txn_date) VALUES(1,100,'2026-10-01')");
        assertThrows(CoinValidationException.class, () -> db.repository.prepare(input, false)); assertEquals(0, db.count("coin_import_file"));
    }
    @Test void restartInNewJvmResumesCommittedChunksAndRetainsFailedPeriod() throws Exception {
        var input = file(CoinFixtures.options(), CoinFixtures.fields("00001", "PROCESSING"), CoinFixtures.fields("00002", "COMPLETE"));
        db.jdbc.execute("ALTER TABLE coin_order_identity ADD CONSTRAINT injected_failure CHECK(exchange_order_id <> '00002')");
        assertThrows(IllegalStateException.class, () -> execute(input));
        assertEquals(2, db.count("coin_import_row")); assertEquals(1, db.count("mutual_fund_order"));
        assertEquals("FAILED", db.repository.result(1).status());
        String failure = db.jdbc.queryForObject("SELECT exit_message FROM batch_step_execution WHERE status='FAILED'", String.class);
        assertFalse(failure.contains("00002")); assertFalse(failure.contains("Failing row contains"));
        assertEquals("BLOCKED", db.jdbc.queryForObject("SELECT period_status FROM coin_import_period", String.class));
        assertThrows(CoinValidationException.class, () -> db.repository.prepare(file(CoinFixtures.options(), CoinFixtures.fields("new", "COMPLETE")), false));
        db.jdbc.execute("ALTER TABLE coin_order_identity DROP CONSTRAINT injected_failure");
        // Real process boundary: no reader, scope, repository or launcher state survives.
        assertEquals(0, launch("IMPORT", input.managedPath()));
        var result = db.repository.result(1);
        assertEquals("COMPLETED", result.status()); assertEquals(2, result.inserted());
        assertEquals(2, db.count("mutual_fund_order")); assertEquals(1, db.count("batch_job_instance")); assertEquals(2, db.count("batch_job_execution"));
        assertEquals(1, db.jdbc.queryForObject("SELECT COUNT(*) FROM batch_step_execution WHERE step_name='coinStageRowsStep'", Long.class));
    }
    @Test void restartRejectsChangedPolicyAndManagedBytes() throws Exception {
        var input = file(CoinFixtures.options(), CoinFixtures.fields("00001", "PROCESSING"));
        var claim = db.repository.prepare(input, false);
        var otherPolicy = CoinFixtures.file(Files.readAllBytes(input.managedPath()), CoinFixtures.options(CoinImportOptions.PostingPolicy.TRADE_DATE_MIDNIGHT));
        assertThrows(IllegalArgumentException.class, () -> db.repository.prepare(otherPolicy, false));
        Files.writeString(input.managedPath(), "changed");
        assertThrows(IllegalArgumentException.class, () -> new CoinJobInput(claim.importId(), 1, 1, input.managedPath().toString(), input.sha256(), db.repository, new CoinCsvReader(CoinCsvReader.Limits.defaults()), new CoinRecordValidator()));
    }
    @Test void concurrentFilesCannotClaimSamePeriodAndMultiFundClaimRollsBack() throws Exception {
        var a = file(CoinFixtures.options(), CoinFixtures.fields("00001", "PROCESSING"));
        var b = file(CoinFixtures.options(), CoinFixtures.fields("00002", "PROCESSING"));
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (var input : List.of(a, b)) futures.add(pool.submit(() -> { start.await(); try { db.repository.prepare(input, false); return true; } catch (CoinValidationException e) { return false; } }));
            start.countDown(); int accepted = 0;
            for (var f : futures) if (f.get(20, TimeUnit.SECONDS)) accepted++;
            assertEquals(1, accepted);
        }
        assertEquals(1, db.count("coin_import_file")); assertEquals(1, db.count("coin_import_period"));
        var second = CoinFixtures.fields("00003", "PROCESSING"); second.put("scheme_name", "Second Fund"); second.put("isin", "INFTEST00002"); second.put("folio_number", "000456/01");
        assertThrows(CoinValidationException.class, () -> db.repository.prepare(file(CoinFixtures.options(), second, CoinFixtures.fields("00004", "COMPLETE")), false));
        assertEquals(1, db.count("coin_import_period"));
        var next = CoinFixtures.fields("next", "PROCESSING"); next.put("trade_date", "01/11/2026");
        var adjacent = db.repository.prepare(file(options(1, 1, LocalDate.of(2026,11,1), LocalDate.of(2026,11,30)), next), false);
        assertEquals(2, db.count("coin_import_period"));
        var conflict = assertThrows(DataIntegrityViolationException.class, () -> db.jdbc.update(
                "UPDATE coin_import_period SET period_start='2026-10-15' WHERE import_file_id=?", adjacent.importId()));
        assertEquals("23P01", ((java.sql.SQLException) conflict.getMostSpecificCause()).getSQLState());
    }
    @Test void schemaPreflightAndChunkTransactionAreMandatory() throws Exception {
        var input = file(CoinFixtures.options(), CoinFixtures.fields("00001", "PROCESSING"));
        assertThrows(IllegalStateException.class, () -> db.repository.stage(1, input, input.rows()));
        db.jdbc.execute("ALTER TABLE mutual_fund_broker_account DROP CONSTRAINT uq_broker_account_name_account_owner");
        assertEquals("SCHEMA_PREFLIGHT_FAILED", assertThrows(IllegalArgumentException.class, () -> db.repository.prepare(input, false)).getMessage());
        assertEmpty();
    }
    int launch(String mode, Path... inputs) throws Exception {
        var args = CoinComponentTest.arguments(inputs[0]);
        args.set(0, "--mode=" + mode);
        args.add("--work-directory=" + directory.resolve("private"));
        for (int i = 1; i < inputs.length; i++) args.add("--input-file=" + inputs[i]);
        args.add("--spring.main.banner-mode=off");
        args.add("--batch.coin.chunk-size=1");
        String classPath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        var command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-cp", classPath,
                CoinOrderHistoryBatchApplication.class.getName()));
        command.addAll(args);
        Path output = directory.resolve("launch-" + UUID.randomUUID() + ".log");
        var process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile());
        process.environment().put("DB_URL", db.source.getUrl() + (db.source.getUrl().contains("?") ? "&" : "?") + "currentSchema=" + db.schema + ",public");
        process.environment().put("DB_USERNAME", db.source.getUsername());
        process.environment().put("DB_PASSWORD", db.source.getPassword());
        var running = process.start();
        if (!running.waitFor(60, TimeUnit.SECONDS)) { running.destroyForcibly(); fail("Coin process did not terminate"); }
        if (running.exitValue() != 0) System.err.println(Files.readString(output));
        return running.exitValue();
    }
    @Test void standaloneLauncherDryRunIsReadOnlyAndNeverLaunchesLegacyJobs() throws Exception {
        var input = directory.resolve("input.csv"); Files.write(input, CoinFixtures.csv(CoinFixtures.fields("00001", "COMPLETE")));
        assertEquals(0, launch("DRY_RUN", input)); assertEmpty(); assertFalse(Files.exists(directory.resolve("private")));
        assertEquals(0, launch("IMPORT", input));
        assertEquals(1, db.count("mutual_fund_order")); assertEquals(0, db.count("trade_records")); assertEquals(0, db.count("ledger_records"));
        assertEquals(List.of("coinOrderHistoryImportJob"), db.jdbc.queryForList("SELECT job_name FROM batch_job_instance", String.class));
    }
    @Test void standaloneLauncherChecksAllFilesBeforeAnyImport() throws Exception {
        var input = directory.resolve("valid.csv"); Files.write(input, CoinFixtures.csv(CoinFixtures.fields("00001", "COMPLETE")));
        var row = CoinFixtures.fields("00002", "COMPLETE"); row.put("isin", "MISSING");
        var invalid = directory.resolve("invalid.csv"); Files.write(invalid, CoinFixtures.csv(row));
        assertEquals(1, launch("IMPORT", input, invalid)); assertEmpty();
    }
    @Test void stagingFailureRollsBackRawRowsAndCanRestartWithoutProjection() throws Exception {
        var input = file(CoinFixtures.options(), CoinFixtures.fields("00001", "COMPLETE"));
        db.jdbc.execute("ALTER TABLE coin_import_row ADD CONSTRAINT reject_rows CHECK(raw_status <> 'COMPLETE')");
        assertThrows(IllegalStateException.class, () -> execute(input));
        assertEquals(0, db.count("coin_import_row")); assertEquals(0, db.count("mutual_fund_order"));
        assertEquals("FAILED", db.repository.result(1).status());
        db.jdbc.execute("ALTER TABLE coin_import_row DROP CONSTRAINT reject_rows");
        assertEquals("COMPLETED", execute(input).status());
    }
    @Test void postingPreservesSellValuesDecimalsAndDeclaredMidnightPolicy() throws Exception {
        var row = CoinFixtures.fields("00001", "COMPLETE"); row.put("transaction_mode", "SELL"); row.put("ordered_at", "03:25 PM");
        row.put("units", "1.123456"); row.put("nav", "89.123456"); row.put("tag", "  source text  ");
        assertEquals(1, execute(file(CoinFixtures.options(CoinImportOptions.PostingPolicy.TRADE_DATE_MIDNIGHT), row)).posted());
        var stored = db.jdbc.queryForMap("SELECT * FROM mutual_fund_txn");
        assertEquals("SELL", stored.get("txn_type")); assertEquals(new java.math.BigDecimal("1.123456"), stored.get("units"));
        assertEquals(new java.math.BigDecimal("89.123456"), stored.get("avg_price")); assertEquals("  source text  ", stored.get("tag"));
        assertEquals(java.sql.Timestamp.valueOf("2026-10-01 00:00:00"), stored.get("txn_date"));
    }
    @Test void databasePreflightDetectsManuallyPostedPendingOrderBeforeReservingCompletion() throws Exception {
        var options = CoinFixtures.options(CoinImportOptions.PostingPolicy.TRADE_DATE_MIDNIGHT);
        execute(file(options, CoinFixtures.fields("00001", "PROCESSING")));
        db.jdbc.update("INSERT INTO mutual_fund_txn(mutual_fund_id,amount,txn_date) VALUES(1,100,'2026-10-01')");
        assertThrows(CoinValidationException.class, () -> db.repository.prepare(file(options, CoinFixtures.fields("00001", "COMPLETE")), true));
        assertEquals(1, db.count("coin_import_file"));
    }
}
