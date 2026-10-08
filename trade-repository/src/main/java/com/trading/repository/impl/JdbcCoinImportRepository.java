package com.trading.repository.impl;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import com.trading.model.coin.*;
import com.trading.model.coin.CoinValidationException.Problem;
import com.trading.repository.CoinImportRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Explicitly registered by Coin only; all mutation entry points bind owner/account and locks. */
public final class JdbcCoinImportRepository implements CoinImportRepository {
    public static final String PARSER_VERSION = "coin-csv-1", POLICY_VERSION = "coin-lifecycle-1";
    private final JdbcTemplate jdbc;
    private final PlatformTransactionManager manager;
    public JdbcCoinImportRepository(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.jdbc = jdbc; this.manager = manager;
    }
    @Override public Claim prepare(CoinImportFile file, boolean preview) {
        var tx = new TransactionTemplate(manager);
        tx.setReadOnly(preview);
        return tx.execute(status -> prepareInTransaction(file, preview));
    }
    @Override public CoinImportOptions options(long id, long owner, long account) {
        var row = jdbc.queryForMap("SELECT * FROM coin_import_file WHERE import_file_id=? AND owner_user_id=? AND broker_account_id=?", id, owner, account);
        Map<Long, Long> overrides = new TreeMap<>();
        jdbc.query("SELECT key,value FROM coin_import_file,jsonb_each_text(configuration_snapshot) WHERE import_file_id=?",
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> overrides.put(Long.valueOf(rs.getString(1)), Long.valueOf(rs.getString(2))), id);
        return new CoinImportOptions(owner, account, date(row, "period_start"), date(row, "period_end"),
                (String) row.get("date_format"), CoinImportOptions.PostingPolicy.valueOf((String) row.get("posting_policy")), overrides);
    }
    private Claim prepareInTransaction(CoinImportFile file, boolean preview) {
        var options = file.options();
        schema();
        String account = account(options, !preview);
        var previous = jdbc.queryForList("SELECT * FROM coin_import_file WHERE owner_user_id=? AND broker_account_id=? AND content_sha256=?",
                options.ownerId(), options.accountId(), file.sha256());
        if (!previous.isEmpty()) {
            Map<String, Object> old = previous.getFirst(); long id = number(old, "import_file_id");
            verifyContract(id, file);
            return new Claim(id, "COMPLETED".equals(old.get("import_status")), mapping(id));
        }
        List<Problem> errors = new ArrayList<>();
        Map<Long, Long> funds = new TreeMap<>();
        Map<Long, List<CoinRow>> byFund = new TreeMap<>();
        Map<Long, String> actions = new HashMap<>();
        for (CoinRow row : file.rows()) {
            if (!row.field("client_id").equals(account)) { errors.add(problem(row, "client_id", "ACCOUNT_MISMATCH")); continue; }
            try {
                long fund = resolveFund(options, row);
                funds.put(row.number(), fund);
                byFund.computeIfAbsent(fund, key -> new ArrayList<>()).add(row);
            } catch (IllegalArgumentException e) { errors.add(problem(row, "fund", e.getMessage())); }
        }
        // Ascending fund locks protect against parent/metadata changes through the REST repositories.
        for (long fund : byFund.keySet()) {
            if (!preview) lockFund(options, fund);
            for (CoinRow row : byFund.get(fund)) {
                try {
                    if (resolveFund(options, row) != fund) throw new IllegalArgumentException("FUND_CHANGED");
                    var identity = identity(options, row);
                    String action = action(row, fund, identity);
                    actions.put(row.number(), action);
                    if (identity == null) checkManualCandidates(options, row, fund);
                    else if ("UPDATED".equals(action)) {
                        Boolean unposted = jdbc.queryForObject("SELECT mutual_fund_txn_id IS NULL AND mutual_fund_id=? FROM mutual_fund_order WHERE mutual_fund_order_id=?",
                                Boolean.class, fund, number(identity, "mutual_fund_order_id"));
                        if (!Boolean.TRUE.equals(unposted)) throw new IllegalArgumentException("ORDER_ALREADY_POSTED_OR_CHANGED");
                        if (options.postingPolicy() != CoinImportOptions.PostingPolicy.ORDER_ONLY) checkTransactions(options, row, fund);
                    }
                } catch (IllegalArgumentException e) { errors.add(problem(row, "order", e.getMessage())); }
            }
        }
        Map<Long, Long> periods = new TreeMap<>();
        boolean reconciles = false;
        for (long fund : byFund.keySet()) {
            var overlaps = jdbc.queryForList("""
                    SELECT * FROM coin_import_period WHERE owner_user_id=? AND mutual_fund_id=?
                    AND daterange(period_start,period_end,'[]') && daterange(CAST(? AS date),CAST(? AS date),'[]')
                    """, options.ownerId(), fund, options.periodStart(), options.periodEnd());
            if (overlaps.isEmpty()) { periods.put(fund, 0L); continue; }
            var period = overlaps.getFirst();
            boolean exact = overlaps.size() == 1 && options.periodStart().equals(date(period, "period_start"))
                    && options.periodEnd().equals(date(period, "period_end"));
            boolean knownOnly = byFund.get(fund).stream().allMatch(r -> Set.of("UPDATED", "UNCHANGED").contains(actions.getOrDefault(r.number(), "INVALID")));
            if (!exact || !"LOADED".equals(period.get("period_status")) || !knownOnly)
                errors.add(new Problem(0, "period", "PERIOD_CONFLICT"));
            reconciles = true;
            periods.put(fund, number(period, "import_period_id"));
        }
        if (reconciles && actions.values().stream().noneMatch("UPDATED"::equals))
            errors.add(new Problem(0, "period", "PERIOD_CONFLICT"));
        if (!errors.isEmpty()) throw new CoinValidationException(errors);
        if (preview) return new Claim(0, false, funds);
        long id = jdbc.queryForObject("""
                INSERT INTO coin_import_file(owner_user_id,broker_account_id,content_sha256,byte_count,raw_header,
                managed_file_ref,parser_version,policy_version,date_format,posting_policy,period_start,period_end,
                configuration_snapshot,fund_mapping,import_status,record_count)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,CAST(? AS jsonb),CAST(? AS jsonb),'RESERVED',?) RETURNING import_file_id
                """, Long.class, options.ownerId(), options.accountId(), file.sha256(), file.byteCount(), file.rawHeader(),
                file.managedPath().toString(), PARSER_VERSION, POLICY_VERSION, options.dateFormat(), options.postingPolicy().name(),
                options.periodStart(), options.periodEnd(), numericMap(options.fundOverrides()), numericMap(funds), file.rows().size());
        for (var entry : periods.entrySet()) {
            long fund = entry.getKey(), period = entry.getValue(); boolean reconciliation = period != 0;
            if (period == 0) period = jdbc.queryForObject("""
                    INSERT INTO coin_import_period(owner_user_id,mutual_fund_id,import_file_id,period_start,period_end,period_status)
                    VALUES(?,?,?,?,?,'RESERVED') RETURNING import_period_id
                    """, Long.class, options.ownerId(), fund, id, options.periodStart(), options.periodEnd());
            jdbc.update("INSERT INTO coin_import_file_fund VALUES(?,?,?,?,?)", id, fund, options.ownerId(), period, reconciliation);
        }
        return new Claim(id, false, funds);
    }
    @Override public void stage(long id, CoinImportFile file, List<? extends CoinRow> rows) {
        var funds = writable(id, file);
        String columns = CoinRow.HEADERS.stream().map(f -> "raw_" + f).collect(Collectors.joining(","));
        for (CoinRow row : rows) {
            var values = new ArrayList<Object>(List.of(id, row.number(), funds.get(row.number()), row.lineStart(), row.lineEnd(),
                    row.byteStart(), row.byteEnd(), row.rawRecord(), hash(row.rawRecord())));
            CoinRow.HEADERS.forEach(field -> values.add(row.field(field)));
            int count = jdbc.update("INSERT INTO coin_import_row(import_file_id,record_number,resolved_fund_id,line_start,line_end,byte_start,byte_end,raw_record,row_sha256,"
                    + columns + ") VALUES(" + String.join(",", Collections.nCopies(values.size(), "?"))
                    + ") ON CONFLICT(import_file_id,record_number) DO NOTHING", values.toArray());
            if (count == 0) {
                byte[] existing = jdbc.queryForObject("SELECT raw_record FROM coin_import_row WHERE import_file_id=? AND record_number=?", byte[].class, id, row.number());
                if (!Arrays.equals(existing, row.rawRecord())) throw new IllegalArgumentException("STAGED_BYTES_CHANGED");
            }
        }
        jdbc.update("UPDATE coin_import_file SET import_status='STAGED',updated_at=CURRENT_TIMESTAMP WHERE import_file_id=?", id);
    }
    @Override public void project(long id, CoinImportFile file, List<? extends CoinRow> rows) {
        Map<Long, Long> funds = writable(id, file);
        for (CoinRow row : rows) {
            long fund = funds.get(row.number());
            if (resolveFund(file.options(), row) != fund) throw new IllegalArgumentException("FUND_CHANGED");
            var staged = jdbc.queryForMap("SELECT * FROM coin_import_row WHERE import_file_id=? AND record_number=? FOR UPDATE", id, row.number());
            if (!Arrays.equals((byte[]) staged.get("raw_record"), row.rawRecord())) throw new IllegalArgumentException("STAGED_BYTES_CHANGED");
            if (!"STAGED".equals(staged.get("outcome"))) continue;
            long rowId = number(staged, "import_row_id");
            Map<String, Object> identity = identity(file.options(), row);
            String action = action(row, fund, identity);
            long order;
            if (identity == null) {
                checkManualCandidates(file.options(), row, fund);
                boolean reconciliation = jdbc.queryForObject("SELECT reconciliation FROM coin_import_file_fund WHERE import_file_id=? AND mutual_fund_id=?", Boolean.class, id, fund);
                if (reconciliation) throw new IllegalArgumentException("NEW_ORDER_IN_COVERED_PERIOD");
                order = jdbc.queryForObject("""
                        INSERT INTO mutual_fund_order(mutual_fund_id,txn_type,trade_date,ordered_at,amount,units,avg_price)
                        VALUES(?,?,?,?,?,?,?) RETURNING mutual_fund_order_id
                        """, Long.class, fund, row.field("transaction_mode"), row.tradeDate(), row.orderedAt(), row.amount(), row.units(), row.nav());
                jdbc.update("""
                        INSERT INTO coin_order_identity(owner_user_id,broker_account_id,exchange_id_sha256,exchange_order_id,
                        mutual_fund_id,mutual_fund_order_id,latest_row_id,source_status) VALUES(?,?,?,?,?,?,?,?)
                        """, file.options().ownerId(), file.options().accountId(), exchangeHash(row), row.field("exchange_order_id"), fund, order, rowId, row.field("status"));
            } else {
                order = number(identity, "mutual_fund_order_id");
                if ("UPDATED".equals(action)) {
                    int changed = jdbc.update("""
                            UPDATE mutual_fund_order SET txn_type=?,trade_date=?,ordered_at=?,amount=?,units=?,avg_price=?,update_date=CURRENT_TIMESTAMP
                            WHERE mutual_fund_order_id=? AND mutual_fund_id=? AND mutual_fund_txn_id IS NULL
                            """, row.field("transaction_mode"), row.tradeDate(), row.orderedAt(), row.amount(), row.units(), row.nav(), order, fund);
                    if (changed != 1) throw new IllegalArgumentException("ORDER_ALREADY_POSTED_OR_CHANGED");
                    jdbc.update("UPDATE coin_order_identity SET latest_row_id=?,source_status=?,updated_at=CURRENT_TIMESTAMP WHERE mutual_fund_order_id=?",
                            rowId, row.field("status"), order);
                }
            }
            boolean posted = false;
            if (!"UNCHANGED".equals(action) && row.field("status").equals("COMPLETE")
                    && file.options().postingPolicy() != CoinImportOptions.PostingPolicy.ORDER_ONLY) {
                // Do not guess associations to manually entered transactions, even with matching amounts.
                checkTransactions(file.options(), row, fund);
                LocalDateTime timestamp = file.options().postingPolicy() == CoinImportOptions.PostingPolicy.TRADE_DATE_MIDNIGHT
                        ? row.tradeDate().atStartOfDay() : row.tradeDate().atTime(row.orderedAt());
                long txn = jdbc.queryForObject("""
                        INSERT INTO mutual_fund_txn(mutual_fund_id,txn_type,txn_date,amount,units,avg_price,status,exchange_order_id,settlement_id,remarks,tag)
                        VALUES(?,?,?,?,?,?,?,?,?,?,?) RETURNING mutual_fund_txn_id
                        """, Long.class, fund, row.field("transaction_mode"), timestamp, row.amount(), row.units(), row.nav(),
                        row.field("status"), row.field("exchange_order_id"), row.field("settlement_id"), row.field("remarks"), row.field("tag"));
                if (jdbc.update("UPDATE mutual_fund_order SET mutual_fund_txn_id=?,update_date=CURRENT_TIMESTAMP WHERE mutual_fund_order_id=? AND mutual_fund_id=? AND mutual_fund_txn_id IS NULL",
                        txn, order, fund) != 1) throw new IllegalArgumentException("TRANSACTION_LINK_CONFLICT");
                posted = true;
            }
            jdbc.update("UPDATE coin_import_row SET outcome=?,mutual_fund_order_id=?,posted=?,updated_at=CURRENT_TIMESTAMP WHERE import_row_id=?",
                    action, order, posted, rowId);
        }
    }
    @Override public void complete(long id, CoinImportFile file) {
        writable(id, file);
        long terminal = jdbc.queryForObject("SELECT COUNT(*) FROM coin_import_row WHERE import_file_id=? AND outcome<>'STAGED'", Long.class, id);
        if (terminal != file.rows().size()) throw new IllegalArgumentException("INCOMPLETE_IMPORT");
        jdbc.update("UPDATE coin_import_file SET import_status='COMPLETED',completed_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE import_file_id=?", id);
        jdbc.update("UPDATE coin_import_period SET period_status='LOADED',loaded_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE import_file_id=?", id);
    }
    @Override public void failed(long id) {
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            jdbc.update("UPDATE coin_import_file SET import_status='FAILED',updated_at=CURRENT_TIMESTAMP WHERE import_file_id=? AND import_status<>'COMPLETED'", id);
            jdbc.update("UPDATE coin_import_period SET period_status='BLOCKED',updated_at=CURRENT_TIMESTAMP WHERE import_file_id=? AND period_status<>'LOADED'", id);
        });
    }
    @Override public CoinImportResult result(long id) {
        return jdbc.queryForObject("""
                SELECT f.import_status,f.record_count,
                  COUNT(*) FILTER(WHERE r.outcome='INSERTED') inserted,
                  COUNT(*) FILTER(WHERE r.outcome='UPDATED') updated,
                  COUNT(*) FILTER(WHERE r.outcome='UNCHANGED') unchanged,
                  COUNT(*) FILTER(WHERE r.posted) posted
                FROM coin_import_file f LEFT JOIN coin_import_row r ON r.import_file_id=f.import_file_id
                WHERE f.import_file_id=? GROUP BY f.import_status,f.record_count
                """, (rs, n) -> new CoinImportResult(id, rs.getString(1), rs.getLong(2), rs.getLong(3), rs.getLong(4), rs.getLong(5), rs.getLong(6)), id);
    }
    private Map<Long, Long> writable(long id, CoinImportFile file) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("JDBC chunk transaction required");
        account(file.options(), true);
        var mapping = mapping(id);
        mapping.values().stream().distinct().sorted().forEach(fund -> lockFund(file.options(), fund));
        verifyContract(id, file);
        String state = jdbc.queryForObject("SELECT import_status FROM coin_import_file WHERE import_file_id=? FOR UPDATE", String.class, id);
        if (state.equals("COMPLETED")) throw new IllegalArgumentException("IMPORT_ALREADY_COMPLETED");
        return mapping;
    }
    private void verifyContract(long id, CoinImportFile file) {
        var o = file.options();
        Boolean same = jdbc.queryForObject("""
                SELECT owner_user_id=? AND broker_account_id=? AND content_sha256=? AND byte_count=?
                AND date_format=? AND posting_policy=? AND period_start=? AND period_end=?
                AND parser_version=? AND policy_version=? AND record_count=? AND configuration_snapshot=CAST(? AS jsonb)
                FROM coin_import_file WHERE import_file_id=?
                """, Boolean.class, o.ownerId(), o.accountId(), file.sha256(), file.byteCount(), o.dateFormat(), o.postingPolicy().name(),
                o.periodStart(), o.periodEnd(), PARSER_VERSION, POLICY_VERSION, file.rows().size(), numericMap(o.fundOverrides()), id);
        if (!Boolean.TRUE.equals(same)) throw new IllegalArgumentException("IMPORT_CONTRACT_CHANGED");
    }
    private Map<Long, Long> mapping(long id) {
        Map<Long, Long> result = new TreeMap<>();
        jdbc.query("SELECT key,value FROM coin_import_file,jsonb_each_text(fund_mapping) WHERE import_file_id=?",
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> result.put(Long.valueOf(rs.getString(1)), Long.valueOf(rs.getString(2))), id);
        return result;
    }
    private String account(CoinImportOptions o, boolean lock) {
        var rows = jdbc.queryForList("SELECT account_id,broker_name FROM mutual_fund_broker_account WHERE broker_account_id=? AND owner_user_id=?" + (lock ? " FOR UPDATE" : ""), o.accountId(), o.ownerId());
        if (rows.size() != 1) throw new IllegalArgumentException("ACCOUNT_NOT_OWNED");
        String client = (String) rows.getFirst().get("account_id");
        long count = jdbc.queryForObject("SELECT COUNT(*) FROM mutual_fund_broker_account WHERE owner_user_id=? AND broker_name=? AND account_id=?", Long.class,
                o.ownerId(), rows.getFirst().get("broker_name"), client);
        if (count != 1) throw new IllegalArgumentException("AMBIGUOUS_ACCOUNT");
        return client;
    }
    private void lockFund(CoinImportOptions o, long fund) {
        var owned = jdbc.queryForList("""
                SELECT f.mutual_fund_id FROM mutual_fund f JOIN mutual_fund_broker_account b USING(broker_account_id)
                WHERE f.mutual_fund_id=? AND b.broker_account_id=? AND b.owner_user_id=? FOR UPDATE OF f
                """, fund, o.accountId(), o.ownerId());
        if (owned.size() != 1) throw new IllegalArgumentException("FUND_NOT_OWNED");
    }
    private long resolveFund(CoinImportOptions o, CoinRow row) {
        var candidates = jdbc.queryForList("""
                SELECT f.* FROM mutual_fund f JOIN mutual_fund_broker_account b USING(broker_account_id)
                WHERE b.broker_account_id=? AND b.owner_user_id=?
                """, o.accountId(), o.ownerId());
        Long explicit = o.fundOverrides().get(row.number());
        if (explicit == null && (row.field("isin").isBlank() || row.field("isin").equals("N/A") || row.field("folio_number").isEmpty()))
            throw new IllegalArgumentException("FUND_MAPPING_REQUIRED");
        var matches = candidates.stream().filter(f -> explicit == null || number(f, "mutual_fund_id") == explicit)
                .filter(f -> row.field("scheme_name").equals(f.get("mutual_fund_name")))
                .filter(f -> row.field("isin").isEmpty() && explicit != null || row.field("isin").equals(f.get("isin")))
                .filter(f -> row.field("plan").isEmpty() || row.field("plan").equals(f.get("plan")))
                .filter(f -> row.field("folio_number").isEmpty() || row.field("folio_number").equals(f.get("folio_number"))).toList();
        if (matches.size() != 1) throw new IllegalArgumentException("FUND_MISSING_OR_AMBIGUOUS");
        return number(matches.getFirst(), "mutual_fund_id");
    }
    private Map<String, Object> identity(CoinImportOptions o, CoinRow row) {
        var found = jdbc.queryForList("""
                SELECT i.*,r.raw_record,""" + CoinRow.HEADERS.stream().map(f -> "r.raw_" + f).collect(Collectors.joining(",")) + """
                 FROM coin_order_identity i JOIN coin_import_row r ON r.import_row_id=i.latest_row_id
                WHERE i.owner_user_id=? AND i.broker_account_id=? AND i.exchange_id_sha256=?
                """, o.ownerId(), o.accountId(), exchangeHash(row));
        if (found.isEmpty()) return null;
        var old = found.getFirst();
        if (!row.field("exchange_order_id").equals(old.get("exchange_order_id"))) throw new IllegalArgumentException("REFERENCE_HASH_COLLISION");
        return old;
    }
    private String action(CoinRow row, long fund, Map<String, Object> old) {
        if (old == null) return "INSERTED";
        if (number(old, "mutual_fund_id") != fund) throw new IllegalArgumentException("ORDER_FUND_CONFLICT");
        if (CoinRow.HEADERS.stream().allMatch(f -> row.field(f).equals(old.get("raw_" + f)))) return "UNCHANGED";
        if (!"PROCESSING".equals(old.get("source_status")) || !"COMPLETE".equals(row.field("status")))
            throw new IllegalArgumentException("INVALID_STATUS_TRANSITION_OR_CHANGED_SNAPSHOT");
        for (String key : List.of("client_id", "isin", "scheme_name", "plan", "transaction_mode", "trade_date", "ordered_at"))
            if (!row.field(key).equals(old.get("raw_" + key))) throw new IllegalArgumentException("ORDER_FACT_CONFLICT");
        String folio = (String) old.get("raw_folio_number");
        if (!folio.isEmpty() && !folio.equals(row.field("folio_number"))) throw new IllegalArgumentException("ORDER_FOLIO_CONFLICT");
        return "UPDATED";
    }
    private void checkManualCandidates(CoinImportOptions o, CoinRow row, long fund) {
        checkTransactions(o, row, fund);
        long untracked = jdbc.queryForObject("""
                SELECT COUNT(*) FROM mutual_fund_order o WHERE o.mutual_fund_id=?
                AND NOT EXISTS(SELECT 1 FROM coin_order_identity i WHERE i.mutual_fund_order_id=o.mutual_fund_order_id)
                """, Long.class, fund);
        if (untracked != 0) throw new IllegalArgumentException("MANUAL_ORDER_RECONCILIATION_REQUIRED");
    }
    private void checkTransactions(CoinImportOptions o, CoinRow row, long fund) {
        long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM mutual_fund_txn t JOIN mutual_fund f USING(mutual_fund_id)
                JOIN mutual_fund_broker_account b USING(broker_account_id)
                WHERE b.owner_user_id=? AND b.broker_account_id=?
                AND (t.exchange_order_id=? OR (t.mutual_fund_id=? AND t.txn_date::date=?
                AND NOT EXISTS(SELECT 1 FROM mutual_fund_order mo JOIN coin_order_identity i USING(mutual_fund_order_id)
                               WHERE mo.mutual_fund_txn_id=t.mutual_fund_txn_id)))
                """, Long.class, o.ownerId(), o.accountId(), row.field("exchange_order_id"), fund, row.tradeDate());
        if (count != 0) throw new IllegalArgumentException("MANUAL_TRANSACTION_RECONCILIATION_REQUIRED");
    }
    private void schema() {
        Boolean valid = jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM pg_constraint WHERE conrelid=to_regclass('mutual_fund_broker_account')
                  AND conname='uq_broker_account_name_account_owner' AND contype='u')
                AND EXISTS(SELECT 1 FROM pg_constraint WHERE conrelid=to_regclass('coin_import_period')
                  AND conname='coin_import_period_no_overlap' AND contype='x')
                AND to_regclass('batch_job_instance') IS NOT NULL
                """, Boolean.class);
        if (!Boolean.TRUE.equals(valid)) throw new IllegalArgumentException("SCHEMA_PREFLIGHT_FAILED");
    }
    private static long number(Map<String, Object> map, String key) { return ((Number) map.get(key)).longValue(); }
    private static LocalDate date(Map<String, Object> map, String key) { return ((java.sql.Date) map.get(key)).toLocalDate(); }
    private static Problem problem(CoinRow row, String field, String code) { return new Problem(row.number(), field, code); }
    private static String numericMap(Map<Long, Long> map) {
        return new TreeMap<>(map).entrySet().stream().map(e -> "\"" + e.getKey() + "\":" + e.getValue()).collect(Collectors.joining(",", "{", "}"));
    }
    private static String exchangeHash(CoinRow row) { return hash(row.field("exchange_order_id").getBytes(StandardCharsets.UTF_8)); }
    private static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
