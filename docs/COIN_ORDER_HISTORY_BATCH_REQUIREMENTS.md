# Coin order-history Spring Batch import requirements

**Status:** original specification with an implemented Coin job as of 8 October 2026; see the implementation update below.

**Prepared:** 7 October 2026, Asia/Kolkata.

**Handoff checked:** 8 October 2026; documentation-only continuation. Source checksum and relevant source contracts rechecked; the 7 October catalog observations below were not rerun.

**Application:** `/Users/abnig19/zerodha/trade-parent-reactor`; branch `main`; commit `70b9f2c2e8621ed396019c6378c1a61b019cf701`. Existing local changes: modified `AGENTS.md` and untracked project-understanding documentation.

**Source:** `/Users/abnig19/Downloads/coin_order_history.csv`.

**Evidence:** refreshed from actual CSV bytes, V1–V13 migrations, models, JDBC SQL, both batch pipelines and upload source. Current read-only catalogs were checked in both configured local databases. No migration, batch execution, service start or application test was performed.

**Historical readiness (7 October):** ready for policy/design review at that time, before the later implementation request. An earlier 224-line draft of this document and the other untracked `docs/` files already existed; unrelated content and the modified `AGENTS.md` were preserved. ORDER_ONLY, REQUIRE_EXISTING and deferred reconciliation remain proposals, not approved business decisions. This refresh supersedes the earlier proposal to include parser version in the business file unique key: a parser upgrade must not defeat replay protection. It also tightens equality-only overlap handling and specifies Batch 6 JDBC infrastructure.

Evidence labels: **CSV** supplied-file observation; **C** current source or resolved library source; **R** current catalog observation; **H** historical investigation; **P** proposed requirement; **U** unresolved business decision. Requirement language below describes the proposed importer, not existing functionality.

**User requirement added, 8 October 2026:** introduce a date-tracking table at application-user and mutual-fund level to prevent another file loading the same date period. The design now has three tables: file audit, raw rows and `coin_import_period`. Rejecting any overlap and requiring explicit export-period dates are proposed defaults, pending the user's answers; they are not yet confirmed choices. This updates the specification, not the deployed schema.

## Implementation update — 8 October 2026

The subsequent user request authorized implementing the job, testing its components,
validating every input record and upserting `PROCESSING` to `COMPLETE`. The
[Coin batch runbook](COIN_BATCH_RUNBOOK.md) is the current implementation contract.
The analysis and proposed requirements below preserve their original dated scope;
statements saying no importer/migration was written describe the earlier design task.

Implemented: isolated executable, strict all-record/multi-file preflight, private
source copies, JDBC staging/order persistence, durable Batch checkpoints, owner/fund
period reservations, completion reconciliation, read-only dry run and component/
PostgreSQL tests. V14 is written and tested on disposable PostgreSQL, **not applied
to either application database**. Upload REST/UI behavior was unchanged by that
8 October implementation. The subsequent 9 October integration request connects
the same job to the existing upload endpoint and adds explicit import options to
the Transactions page; see the [current REST/UI contract](COIN_BATCH_RUNBOOK.md#rest-and-ui-integration--9-october-2026).

The period rule now permits a later file to update known PROCESSING orders to
COMPLETE using an exact loaded reservation; unchanged known rows/funds can accompany
it. This implements the later upsert request and supersedes the earlier blanket
rejection of corrected same-period exports. New orders in a covered period,
partial overlaps and conflicting/backward changes remain rejected.

The final schema has five tables: the three proposed audit/period tables plus
`coin_import_file_fund` (links completion files to existing claims) and
`coin_order_identity` (owner/account/exchange-ID identity and latest snapshot).
Date convention and posting date remain explicit launch choices; no live-source
time semantics or automatic master-creation policy is inferred. ORDER_ONLY and
both explicit transaction-date policies are documented in the runbook.

## 1. Objective and scope

Specify a new `coinOrderHistoryImportJob` in `trade-batch` that preserves every source field in PostgreSQL, resolves the correct owner's broker account and fund, and imports order facts without duplicating orders or changing portfolio totals prematurely.

The recommended initial behavior stores every structurally valid row of an accepted file in durable import staging and projects unambiguous rows into `mutual_fund_order`. The period gate described below runs before source-row staging; a conflicting file is not loaded. Completed source rows also remain orders until an explicit transaction-posting policy is approved. This delivers database ingestion; it does **not** promise immediate appearance in the transactions UI. Automatic posting of the six completed sample rows is an open business decision, not an implicit requirement.

Included: parsing, staging, owner-scoped matching, order persistence, repeat-import protection, restart, row outcomes, dry run and tests. Excluded from this documentation task: implementation, live imports, schema changes, services and deployments. Proposed implementation scope excludes valuation creation, equity/ledger imports, automatic master creation and REST/UI integration unless separately selected.

## 2. Source evidence

| Property | Observation |
| --- | --- |
| File | 1,602 bytes; strictly UTF-8 decodable, all bytes ASCII; no BOM; comma-delimited CSV |
| Content checksum | SHA-256 `1294147070c15f54dc1a4965551af98d4d2782ec66521267210a4bf32ae5f52b` |
| Shape | 16 header columns and 9 data records; every record has 16 fields |
| Physical structure | 10 physical lines, 10 CRLF terminators, no bare LF or blank lines; final record terminated |
| Header comparison | All expected names in the exact requested order; no discrepancy, duplicate or missing header |
| Source direction | 9 `BUY` records |
| Source status | 6 `COMPLETE`; 3 `PROCESSING` |
| Missing values | `plan`: 9 blanks; `settlement_id`: 3; `folio_number`: 3; `remarks`: 6; other columns have no blanks |
| Numeric detail | Maximum observed fractional places: amount 0, units 3, NAV 2; 3 rows each have zero units and NAV |
| Text detail | Tags include plain text and JSON-shaped text; no observed leading/trailing whitespace |
| Identifiers | One client; four distinct ISINs and scheme names; 9 distinct exchange order IDs in this sample |
| Repetition | No identical complete rows; a nonblank settlement reference is shared by multiple rows |
| Date/time | Slash-separated date text and 12-hour AM/PM times; no timezone or separate execution/allotment date |
| Quoting | No quoted-comma cells, doubled-quote escape pairs or multiline records. Three tags contain literal quotes inside unquoted JSON-shaped text; all three diagnostically parse as JSON objects. Six tags are plain text. This is not evidence of strict RFC-only quoting. |
| Missing markers | No literal `N/A` in any cell. No leading-zero client, settlement, folio or exchange identifiers in this sample. These require synthetic preservation tests. |
| Status correlations | All three PROCESSING rows have blank folio/settlement, zero units/NAV and nonblank remarks; all six COMPLETE rows have nonblank folio/settlement, nonzero units/NAV and blank remarks. These are correlations, not posting rules. |

Verified header order:

```text
client_id,isin,scheme_name,plan,transaction_mode,settlement_id,trade_date,ordered_at,folio_number,amount,units,nav,status,exchange_order_id,remarks,tag
```

Complete field profile (**CSV**): lengths count decoded characters, which also equal cell-byte lengths for this ASCII file. Distinct values exclude blanks. Numeric precision/scale describe source representations, not schema limits. A blank CSV cell is an empty string, not automatically SQL NULL.

| Pos | Field | Observed format | Blanks / literal N/A | Maximum length | Distinct nonblank | Decimal precision / scale / integer digits |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | `client_id` | Three uppercase letters and three digits | 0 / 0 | 6 | 1 | Identifier, not numeric |
| 2 | `isin` | Twelve uppercase alphanumeric characters | 0 / 0 | 12 | 4 | n/a |
| 3 | `scheme_name` | Text with spaces | 0 / 0 | 44 | 4 | n/a |
| 4 | `plan` | Empty only | 9 / 0 | 0 | 0 | n/a |
| 5 | `transaction_mode` | BUY only | 0 / 0 | 3 | 1 | n/a |
| 6 | `settlement_id` | Seven digits or empty | 3 / 0 | 7 | 4 | Identifier, not numeric |
| 7 | `trade_date` | NN/NN/NNNN; both components <=12 | 0 / 0 | 10 | 5 | n/a |
| 8 | `ordered_at` | hh:mm AM/PM | 0 / 0 | 8 | 4 | n/a |
| 9 | `folio_number` | Eight/eleven digits, or eight digits/slash/two digits | 3 / 0 | 11 | 4 | Identifier, not numeric |
| 10 | `amount` | Unsigned integer decimal text; no zero or negative | 0 / 0 | 4 | 6 | 4 / 0 / 4 |
| 11 | `units` | Unsigned decimals; three zero values | 0 / 0 | 7 | 7 | 6 / 3 / 3 |
| 12 | `nav` | Unsigned decimals; three zero values | 0 / 0 | 6 | 7 | 5 / 2 / 3 |
| 13 | `status` | COMPLETE (6), PROCESSING (3) | 0 / 0 | 10 | 2 | n/a |
| 14 | `exchange_order_id` | Ten digits | 0 / 0 | 10 | 9 | Identifier, not numeric |
| 15 | `remarks` | One repeated nonblank phrase | 6 / 0 | 36 | 1 | n/a |
| 16 | `tag` | Plain text or unquoted JSON-shaped text | 0 / 0 | 24 | 2 | n/a |

One nonblank settlement appears three times; two nonblank folios appear twice each. No cell has edge whitespace, but internal spaces occur. Observed formats/lengths cannot become restrictive business vocabularies or limits. Synthetic examples only: client `ABC000`, folio `00001234/05`, exchange `00000000000000000042`, tag `{"label":["example"]}`, decimal `12.345600`. Quoted commas, escaped CSV quotes, embedded LF/CRLF, edge whitespace, literal N/A and leading zeros must be tested synthetically, not claimed as observed.

These observations describe this file only. They do not establish global uniqueness, a complete status vocabulary, maximum field lengths, a universal precision limit or completed-transaction semantics. All observed date components are at most 12, so this sample alone cannot prove day/month order. The proposed parser convention is `dd/MM/uuuu`; confirm the export convention before posting dates to financial transactions. Do not infer plan or tag semantics from names.

Private account IDs, fund names, order IDs and individual financial amounts are intentionally omitted from this document. Use synthetic fixtures that retain the relevant structural cases.

## 3. Current schema and mapping

The table below describes the schema composed from the complete V1–V13 history, corroborated by current JDBC SQL/models and the limited current catalogs described below. All 16 raw strings must additionally be retained, including blank strings. Source profiles above and meaning/relationship classifications below complete the mapping matrix; no field is dropped.

### Migration composition and current catalog evidence

V1 creates extensions; V2 Batch metadata; V3 business tables; V4 AI tables; V5 users/roles; V6 nullable broker ownership and parent indexes; V7 recovery; V8 profile/email rules. V9 adds fund ISIN/plan, order facts and the same-fund FK, and widens transaction units/NAV to NUMERIC(21,6). V10 moves folio to funds and aborts on distinct conflicting folios. V11 moves five metadata fields to transactions, rejecting unlinked metadata or conflicting linked values before dropping order columns; it never creates transactions. Its temporary guard constraint is dropped, not a current order constraint. V12 specifies owner/broker/account uniqueness. V13 removes exact-12 ISIN validation and changes ISIN to nullable TEXT. V1/V4 remain full fresh-install dependencies, not Coin destinations.

**R, 7 October 2026 around 19:03–19:04 IST:** inspected configured loopback databases `postgresd/public` and `postgresp/public`, PostgreSQL 18.4 (Homebrew), using read-only sessions (`transaction_read_only=on`, `BEGIN READ ONLY`, timeouts, `ROLLBACK`). Queries covered information_schema/pg_catalog, indexes and only Flyway version/type/success. No business records, private-data counts, credentials or sequence current values were queried.

All 39 columns across broker/fund/order/transaction tables match the mapped types/nullability in both targets; 44 Batch columns were also inspected. V10 fund folio, V11 transaction metadata, V13 TEXT ISIN/no length check, widened decimals and the same-fund FK are present. **V12 uniqueness and any equivalent unique index are absent in both.** Broker IDs use a legacy BIGINT sequence default rather than V3's BY DEFAULT identity; fund/order/transaction IDs use ALWAYS identity. Both Flyway histories contain only one successful BASELINE version 1 entry. Present columns do not prove ordered migration execution or correct backfills. The earlier project schema snapshot remains historical and was not relabelled by this narrower recheck. Both targets require schema/history reconciliation before a future import.

| # / CSV field | Observed format / nullability | Meaning / confidence | Target table.column / SQL type / nullability | Java property / type | Conversion / validation | Lookup relationship | Storage disposition |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 `client_id` | Alphanumeric; 0/9 blank; N/A 0 | External broker account; high C, not application user | `mutual_fund_broker_account.account_id VARCHAR(100) NOT NULL` | `MutualFundBrokerAccount.accountId`: String | External identifier; resolve with trusted owner and broker context. Do not parse as a number or map to `users.id`. | Trusted owner + broker + external account -> internal account -> fund | Existing master lookup only; no master write; proposed `coin_import_row.raw_client_id` TEXT/String for every row |
| 2 `isin` | 12 alphanumeric; 0/9 blank; N/A 0 | Source security label; high mapping C, validity unproven | `mutual_fund.isin TEXT NULL` | `MutualFund.isin`: String | Source text; preserve `N/A`. V13 removed the 12-character constraint. Blank may map to NULL on a new fund; must not clear an existing fund on import. | Exact account-scoped ISIN/name/plan/folio or reviewed mapping | Existing master lookup only; no master write; proposed `coin_import_row.raw_isin` TEXT/String for every row |
| 3 `scheme_name` | Text; 0/9 blank; N/A 0 | Source scheme name; high mapping C | `mutual_fund.mutual_fund_name VARCHAR(255) NOT NULL` | `MutualFund.mutualFundName`: String | Source scheme label; use within account-scoped matching. No global name-only match, rename or truncation. | Exact name consistency in selected account; no global/fuzzy match | Existing master lookup only; no master write; proposed `coin_import_row.raw_scheme_name` TEXT/String for every row |
| 4 `plan` | Empty only; 9/9 blank; N/A 0 | Fund metadata mapping C; blank meaning/vocabulary U | `mutual_fund.plan TEXT NULL` | `MutualFund.plan`: String | Blank in all sample rows. Preserve raw blank; no inferred default, vocabulary or extraction from scheme name. | Nonblank compatibility with selected fund; blank cannot erase | Existing master lookup only; no master write; proposed `coin_import_row.raw_plan` TEXT/String for every row |
| 5 `transaction_mode` | BUY; 0/9 blank; N/A 0 | Source direction; high mapping C, vocabulary unproven | `mutual_fund_order.txn_type VARCHAR NULL`; conditional future posting to `mutual_fund_txn.txn_type VARCHAR NOT NULL` | Order `transactionType`: String; transaction `transactionType`: `TransactionType` | Preserve order text. Transaction enum supports BUY/SELL; unknown direction is retained and deferred from posting. | Direct order; future transaction requires BUY/SELL approval | Existing order destination; proposed `coin_import_row.raw_transaction_mode` TEXT/String for every row |
| 6 `settlement_id` | Digit text; 3/9 blank; N/A 0 | Settlement reference by label; medium, nonunique CSV | `mutual_fund_txn.settlement_id TEXT NULL` | `MutualFundTxn.settlementId`: String | Nonunique source text, leading zeros preserved. V11 removed this column from orders; staging is required when no linked transaction exists. | No unique lookup; shared settlements allowed | Existing transaction-only destination; unposted storage gap; proposed `coin_import_row.raw_settlement_id` TEXT/String for every row |
| 7 `trade_date` | Slash date; 0/9 blank; N/A 0 | Source business date; medium, convention/execution meaning U | `mutual_fund_order.trade_date DATE NULL` | `MutualFundOrder.tradeDate`: LocalDate | Proposed strict `dd/MM/uuuu`. Do not equate with `mutual_fund_txn.txn_date TIMESTAMP NOT NULL` without a posting rule. | Direct independent order date; not txn_date | Existing order destination; proposed `coin_import_row.raw_trade_date` TEXT/String for every row |
| 8 `ordered_at` | hh:mm AM/PM; 0/9 blank; N/A 0 | Order-time label; medium, date/zone U | `mutual_fund_order.ordered_at TIME WITHOUT TIME ZONE NULL` | `MutualFundOrder.orderedAt`: LocalTime | Strict `hh:mm a` with English AM/PM; test midnight/noon. Do not invent an offset, execution time or combine with the trade date as a proven timestamp. | Direct independent LocalTime; no inferred timestamp | Existing order destination; proposed `coin_import_row.raw_ordered_at` TEXT/String for every row |
| 9 `folio_number` | Digits/slash; 3/9 blank; N/A 0 | Source folio identifier; high mapping C | `mutual_fund.folio_number TEXT NULL` | `MutualFund.folioNumber`: String | V10 moved folio from orders to funds. Preserve slashes/zeros; one folio per fund row. Missing folio is incomplete source information, not permission to erase a known folio. | Fund-level discriminant; unresolved identity stops period preflight | Existing master lookup only; no master write; proposed `coin_import_row.raw_folio_number` TEXT/String for every row |
| 10 `amount` | Decimal; 0/9 blank; N/A 0 | Source amount; high mapping, currency absent from header | `mutual_fund_order.amount NUMERIC(18,2) NULL`; future posted `mutual_fund_txn.amount NUMERIC(18,2) NOT NULL` | `amount`: BigDecimal | Parse exact decimal; maximum 16 integer and 2 fractional digits for exact storage. Reject/defer values requiring rounding or overflow; do not recompute from units × NAV. | Direct order financial fact | Existing order destination; proposed `coin_import_row.raw_amount` TEXT/String for every row |
| 11 `units` | Decimal/zero; 0/9 blank; N/A 0 | Source units; high mapping, zero meaning U | `mutual_fund_order.units NUMERIC(21,6) NULL`; future posted `mutual_fund_txn.units NUMERIC(21,6) NOT NULL` | `units`: BigDecimal | Maximum 15 integer and 6 fractional digits. Preserve zero separately from missing; neither proves allotment. | Direct order financial fact | Existing order destination; proposed `coin_import_row.raw_units` TEXT/String for every row |
| 12 `nav` | Decimal/zero; 0/9 blank; N/A 0 | Source NAV; high mapping, not a valuation snapshot | `mutual_fund_order.avg_price NUMERIC(21,6) NULL`; future posted `mutual_fund_txn.avg_price NUMERIC(21,6) NOT NULL` | `avgPrice`: BigDecimal | Map NAV to existing `avg_price` naming. Same precision rules as units. Do not create a valuation snapshot from an order NAV. | Direct order avg_price; no valuation write | Existing order destination; proposed `coin_import_row.raw_nav` TEXT/String for every row |
| 13 `status` | COMPLETE/PROCESSING; 0/9 blank; N/A 0 | Source status; high, financial finality U | `mutual_fund_txn.status TEXT NULL` | `MutualFundTxn.status`: String | V11 transaction metadata; preserve arbitrary source text in staging for every order. No default or implicit financial effect. | Direct source snapshot; no implicit posting | Existing transaction-only destination; unposted storage gap; proposed `coin_import_row.raw_status` TEXT/String for every row |
| 14 `exchange_order_id` | Digit text; 0/9 blank; N/A 0 | External reference; medium, uniqueness/cardinality U | `mutual_fund_txn.exchange_order_id TEXT NULL` | `MutualFundTxn.exchangeOrderId`: String | V11 removed it from orders. Existing column is nonunique. Candidate reconciliation key only within verified owner/broker/account scope. | Owner/account-scoped candidate evidence only | Existing transaction-only destination; unposted storage gap; proposed `coin_import_row.raw_exchange_order_id` TEXT/String for every row |
| 15 `remarks` | Text/empty; 6/9 blank; N/A 0 | Source annotation; high, business purpose U | `mutual_fund_txn.remarks TEXT NULL` | `MutualFundTxn.remarks`: String | Preserve verbatim, including empty text and whitespace; no instruction execution or destructive cleanup. Unposted records retain it in staging. | No lookup; annotation only | Existing transaction-only destination; unposted storage gap; proposed `coin_import_row.raw_remarks` TEXT/String for every row |
| 16 `tag` | Plain/JSON-shaped text; 0/9 blank; N/A 0 | Source annotation; high, vocabulary/purpose U | `mutual_fund_txn.tag TEXT NULL` | `MutualFundTxn.tag`: String | Preserve plain and JSON-shaped strings verbatim. Do not deserialize and reserialize the value or enforce JSON-only storage. Unposted records retain it in staging. | No lookup; preserve raw string | Existing transaction-only destination; unposted storage gap; proposed `coin_import_row.raw_tag` TEXT/String for every row |

Relationships: `users.id` → `mutual_fund_broker_account.owner_user_id` → `mutual_fund.broker_account_id` → `mutual_fund_order.mutual_fund_id`. Internal IDs are generated database keys. The optional order `mutual_fund_txn_id` link has a composite foreign key with `mutual_fund_id`, so the transaction must belong to the same fund. The schema does not enforce one order per transaction.

Values absent from the CSV include trusted owner ID, broker identity, internal account/fund/order/transaction IDs, optional transaction link, file/job identity, parser/policy versions and audit timestamps. Supply them through trusted launch context, verified lookups or database generation. Do not obtain them by guessing from CSV fields. Broker Java `id`, fund `mutualFundId`, order `mutualFundOrderId` and transaction `mutualFundTxnId` are Long. Owner is bound in repositories, not exposed as a broker POJO property. Existing `create_date`/`update_date` are NOT NULL TIMESTAMP WITHOUT TIME ZONE, default CURRENT_TIMESTAMP, mapped to LocalDateTime; repository updates refresh update_date explicitly. They are persistence audit times, never source dates. Required T.txn_date has no proven source mapping.

### Current limitations that affect the design

- **C1 — pending metadata:** after V11, unlinked orders cannot hold five source metadata fields. Writing only to `mutual_fund_order` loses data; inserting a transaction merely to retain metadata affects existing portfolio calculations.
- **C2 — portfolio behavior:** current transaction and analytics queries include transaction financial values without filtering by descriptive status. Orders are excluded. A `PROCESSING` transaction is not automatically harmless.
- **C3 — duplicate protection:** neither exchange references nor order source identity have an existing uniqueness contract. Current order repository operations are by generated ID or fund, not source order ID.
- **C4 — master matching:** broker uniqueness is specified by V12 for `(broker_name, account_id, owner_user_id)`; funds have no source matching uniqueness constraint. Current catalog checks confirm missing V12 enforcement in both configured databases.
- **C5 — startup:** existing trade and ledger applications implement startup runners and submit jobs independently of Boot's automatic job flag. Coin startup must explicitly exclude unintended runners; copying existing startup behavior is insufficient.
- **C6 — upload:** current REST upload writes a CSV to an owner-specific directory and returns `UPLOADED`. It does not parse, enqueue or launch this job.

## 4. Recommended storage additions

The following tables are **proposals**, not existing schema. Final names, migration number and deployment plan must be reviewed against the then-current repository and live catalog. Prefer one forward migration after the latest migration; never modify applied migrations.

| Proposed table | Minimum columns and constraints | Purpose |
| --- | --- | --- |
| `coin_import_file` | `import_file_id BIGINT GENERATED ALWAYS AS IDENTITY` PK; `owner_user_id BIGINT NOT NULL` FK users(id); `broker_account_id BIGINT NOT NULL` FK B(broker_account_id); `source_system TEXT NOT NULL`; `period_start`, `period_end DATE NOT NULL` checked finite/ordered and frozen with the proposed coverage policy; `content_sha256 VARCHAR(64) NOT NULL` checked lowercase hex; `byte_count BIGINT NOT NULL CHECK >=0`; `parser_version`, `policy_version`, `date_format` TEXT NOT NULL; `original_filename TEXT NULL`; `managed_file_ref TEXT NOT NULL`; `raw_header BYTEA NULL`; `configuration_snapshot JSONB NOT NULL`; `import_status TEXT NOT NULL`; UNIQUE(owner_user_id, broker_account_id, source_system, content_sha256) | Durable identity independent of filename, parser version, mode or launch time. FKs individually do not prove account ownership; enforce owner/account relationship in SQL. Store only safe configuration in JSONB; raw tags stay TEXT. |
| `coin_import_row` | `import_row_id BIGINT GENERATED ALWAYS AS IDENTITY` PK; `import_file_id BIGINT NOT NULL` FK file; `record_number BIGINT NOT NULL CHECK >0`; `line_start`, `line_end`, `byte_start`, `byte_end` BIGINT NOT NULL checked ordered/nonnegative; `field_count INTEGER NOT NULL`; all 16 `raw_<csv_name> TEXT` columns; `raw_record BYTEA NOT NULL`; `row_sha256 VARCHAR(64) NOT NULL`; `parse_state`, `outcome` TEXT NOT NULL; `reason_code`, `reason_detail` TEXT NULL; `mutual_fund_order_id BIGINT NULL` FK order; `duplicate_of_row_id BIGINT NULL` self FK; UNIQUE(import_file_id, record_number) | Logical data-record identity excluding header, with exact bytes/terminator and end-exclusive offsets. Derive resolved fund and transaction through the order; no redundant resolved fund field required. |

Both tables add `created_at`, `updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP`, plus `first_job_execution_id`, `last_job_execution_id BIGINT NULL` correlation fields. File also adds `completed_at TIMESTAMPTZ NULL`, `failure_code`, `failure_detail TEXT NULL`, `archive_status TEXT NOT NULL DEFAULT 'NOT_REQUESTED'`, and nullable BIGINT counters `record_count`, `staged_count`, `inserted_count`, `updated_count`, `duplicate_count`, `deferred_count`, `rejected_count`, `remaining_count` (nonnegative when known). Operational timestamps are actual audit instants; they impose no timezone on CSV fields. Batch execution IDs deliberately have no FK, allowing framework retention independent of business audit. All other FKs use NO ACTION; no automatic cascade/purge.

The 16 proposed raw columns are `raw_client_id`, `raw_isin`, `raw_scheme_name`, `raw_plan`, `raw_transaction_mode`, `raw_settlement_id`, `raw_trade_date`, `raw_ordered_at`, `raw_folio_number`, `raw_amount`, `raw_units`, `raw_nav`, `raw_status`, `raw_exchange_order_id`, `raw_remarks`, `raw_tag`. Proposed CoinImportRow Java raw properties are String. A parse_state=VALID CHECK requires field_count=16 and all 16 fields IS NOT NULL, allowing empty strings. SQL NULL in a raw column is reserved for structural/decoding failure, never an ordinary blank. Immutable raw bytes protect even invalid UTF-8/NUL values that cannot be stored in PostgreSQL TEXT; reject their text projection without removing/replacing source bytes.

Index files by `(owner_user_id, broker_account_id, import_status, import_file_id)`, rows by `(import_file_id, outcome, record_number)`, and nonnull order links. A nonunique bounded hash index for raw exchange ID supports candidate lookup without indexing arbitrarily large TEXT; compare exact source text after hash hits and scope through file owner/account. A nonunique row_sha256 supports comparison; define its versioned length-prefixed field encoding and verify actual fields after a hit. Neither hash, exchange nor settlement is a unique order key. Record ordinals preserve legitimate identical rows. A file checksum hit must also agree on size/content.

For a structurally malformed record with a recoverable boundary, retain the raw record and rejection details even if 16 parsed fields are unavailable. A file with unrecoverable quoting/record boundaries fails structurally; preserve its immutable file and failure position rather than fabricating row counts.

If business rules later establish a stable external order key and automatic updates, add an explicit owner/account-scoped reconciliation identity with a database uniqueness constraint and append-only resolution events (actor, reason, previous/new links, policy version). Do not implement an unsafe read-then-insert upsert against nonunique transaction metadata. File hashes alone do not prevent overlapping exports; the third table below adds the requested period protection. Audited re-resolution remains a separate operation, not an undocumented update on replay.

### User and mutual-fund period tracking

**Confirmed requirement:** track loaded periods per `(owner_user_id, mutual_fund_id)` and prevent a second file for the same period. **Proposed interpretation:** reject every intersecting inclusive date range, including equal, contained and enclosing periods. Exact-start/end uniqueness alone would allow partial overlaps. Different users or different owned fund records may import the same calendar period. Fund means the existing internal `mutual_fund_id` (account/folio-specific), not a scheme name or an ISIN shared across accounts; do not create duplicate masters to bypass the guard.

Add `coin_import_period` with this minimum contract:

| Column / constraint | Type and meaning |
| --- | --- |
| `import_period_id` | BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY |
| `owner_user_id` | BIGINT NOT NULL, FK `users(id)`; authenticated/trusted owner |
| `mutual_fund_id` | BIGINT NOT NULL, FK `mutual_fund(mutual_fund_id)`; verified through its broker account to that owner |
| `import_file_id` | BIGINT NOT NULL, FK `coin_import_file(import_file_id)`; one file can reserve many funds |
| `period_start`, `period_end` | DATE NOT NULL; inclusive business/export dates, with finite values and start <= end; never upload timestamps |
| `period_status` | TEXT NOT NULL CHECK IN (`RESERVED`, `LOADED`, `BLOCKED`, `RELEASED`) |
| `created_at`, `updated_at`, `loaded_at` | TIMESTAMPTZ; first two NOT NULL DEFAULT CURRENT_TIMESTAMP, loaded_at nullable until finalized |
| `first_job_execution_id`, `last_job_execution_id` | Nullable BIGINT correlation IDs, independent of Batch metadata retention |
| `released_at`, `released_by_user_id`, `release_reason` | Nullable audit fields; actor FK users(id); all required for the one-way RELEASED transition |
| Import/fund uniqueness | UNIQUE (`import_file_id`, `mutual_fund_id`); one period per represented fund per file |
| Overlap protection | Non-overlap constraint on owner equality, fund equality and the inclusive date range, for every status except RELEASED |

Require a database CHECK for finite/ordered period dates and lifecycle audit consistency (LOADED has loaded_at; RELEASED has all release fields). Use NO ACTION foreign keys and index `import_file_id` plus `(owner_user_id, mutual_fund_id, period_status)` for status/report lookup. Add UNIQUE `(import_file_id, owner_user_id)` on the proposed file table and a corresponding composite period FK so a period cannot point to a different owner's file. Fund-to-file account consistency still requires owner/account-bound SQL, revalidated under the account/fund locks; independent foreign keys alone do not prove it. Keep the accepted per-record fund mapping in the file's frozen configuration snapshot so projection cannot quietly choose a fund outside its reservations. A fund moved to another account after claim must stop the import pending review; preserve its earlier period history.

Recommended database enforcement (illustrative constraint, **not an applied migration**):

```sql
-- Provision btree_gist in the reviewed forward migration/deployment plan.
ALTER TABLE coin_import_period
    ADD CONSTRAINT coin_import_period_no_overlap
    EXCLUDE USING gist (
        owner_user_id WITH =,
        mutual_fund_id WITH =,
        daterange(period_start, period_end, '[]') WITH &&
    ) WHERE (period_status IN ('RESERVED', 'LOADED', 'BLOCKED'));
```

PostgreSQL supports range exclusion constraints and `btree_gist` provides GiST equality support for BIGINT identifiers. This makes the overlap rule effective when competing sessions try to claim the same range; a prior SELECT or an exact-range UNIQUE constraint is insufficient. The extension is a proposed new prerequisite: it is not declared by current V1, and its installation/privileges have not been checked. Include it in the future migration and isolated PostgreSQL tests; fail preflight if the required enforcement is absent. Sources: [PostgreSQL range constraints](https://www.postgresql.org/docs/18/rangetypes.html#RANGETYPES-CONSTRAINT), [btree_gist](https://www.postgresql.org/docs/18/btree-gist.html).

Period origin and acceptance, proposed defaults:

- Require `periodStart` and `periodEnd` as strict ISO dates supplied with the export/import request, frozen on the file and every period row. The CSV has trade dates but no export-period header. Its minimum/maximum trade dates do not establish coverage of quiet days; filenames and upload time are not evidence either.
- Require confirmed source-date parsing and verify every source trade date lies within the declared period. Unconfirmed, missing, malformed or out-of-range dates fail this period preflight. This deliberately tightens the earlier raw/deferred date proposal: preserve the source file outside the DB until coverage is verifiable.
- Resolve every represented fund before claiming the file. Missing/ambiguous fund identity stops the whole file with `PERIOD_FUND_UNRESOLVED`; do not bypass tracking by staging an unresolved fund. Other business uncertainties may still produce DEFERRED rows after acceptance. No automatic master creation or enrichment is introduced.
- Reserve the same declared period separately for each distinct fund represented in the file. Do not reserve absent funds or interpret an empty export as coverage for every fund; empty input needs an explicit, separately reviewed fund manifest.
- In one JDBC transaction, validate/lock the account and funds in ascending ID order, claim the business file identity and reserve **all** its fund periods. One conflict rolls back all new reservations and the file claim. Return `PERIOD_ALREADY_LOADED`, `PERIOD_IN_PROGRESS` or `PERIOD_BLOCKED` with safe, owner-scoped conflicting period/import details. Load no new source rows or orders; retain the rejected original/managed bytes and an operator report outside the DB.
- A byte-identical, same-contract replay returns the existing result or resumes the same failed import. It reuses that import's period rows instead of attempting new ones. Different filenames, hashes, parser versions or declared dates cannot override a held period. Period dates are frozen nonidentifying job parameters; the per-record fund mapping is frozen in the configuration snapshot, not copied wholesale into Batch parameters. Neither creates another job instance for identical bytes.

Period lifecycle and correction rules:

| State | Meaning and whether another file can use the period |
| --- | --- |
| RESERVED | Claim committed before staging; blocks other files even across process crashes. A launch failure must be recovered using that same claim. |
| LOADED | Finalization committed after all source rows for that fund have durable terminal outcomes; blocks another file. COMPLETED_WITH_ISSUES still consumes the period; LOADED means accepted source ingestion, not posted investments or guaranteed source completeness. |
| BLOCKED | Failed/stopped import, strict validation gate or partial commits; still blocks other files. Restart the same file/claim to finish; never auto-release based on elapsed time. |
| RELEASED | Explicit audited abandonment only after confirming no active execution and **zero staged rows, orders or other business writes** for the whole import. Retain the claim history; only then may a different file reserve the range. An import with partial writes cannot use this path. |

Finalize file results and period statuses in the same transaction. Earlier per-chunk source/order commits remain protected if finalization crashes; RESERVED/BLOCKED still exclude overlaps. Reconcile counts and checkpoints before a recovery transition. An explicit release changes all of an import's reservations together and closes that file as abandoned; a stale execution must not subsequently stage/project, and an abandoned file cannot be silently restarted. Claim/release/stage/project must use the same lock order and recheck claim status within their write transactions.

Example: after User A / Fund X loads 2026-10-01 through 2026-10-31, another identical, smaller or partially overlapping period for that pair is rejected. A period starting 2026-11-01 is allowed; one starting 2026-10-31 shares a covered day and is rejected. User A / Fund Y and User B / their own Fund X record remain independent.

This rule intentionally prevents a later corrected or PROCESSING-to-COMPLETE export from entering through ordinary import for an already held range. Keep that file outside DB ingestion and route it to a future audited correction/reconciliation process. It supersedes the earlier suggestion to stage every overlapping export automatically. Non-overlapping periods still need source-order identity checks: the date table complements file hashes and row reconciliation; it does not prove two orders are different. Existing manual/history data has no automatic coverage record; establish reviewed baseline coverage before enabling the guard for such portfolios, without inventing coverage from transaction min/max dates.

## 5. Functional requirements

| ID | Requirement |
| --- | --- |
| FR-01 | Treat the file as data. Preserve the original file and raw field strings; never evaluate cells or embedded instructions. |
| FR-02 | Validate UTF-8 input, optional BOM, the exact 16 header names, duplicate/missing/unexpected headers and record shape. Map by header name; allow reordered valid headers. Header errors fail before order writes. Support CSV quoting, escaped quotes, embedded commas and quoted multiline fields. |
| FR-03 | Parse decimals with `BigDecimal`; dates and times strictly with an explicit locale. Preserve raw representations. Blank numeric/time fields become nullable order values; the period gate requires verifiable trade dates before accepting a file. `N/A` in identifiers remains text; numeric/time conversion failures remain explicit row rejections. |
| FR-04 | Validate trusted owner and broker account before processing. Every row's `client_id` must exactly match the selected external account ID. Mixed accounts fail preflight for this single-account job. No global fallback or account auto-creation. |
| FR-05 | Resolve funds only within that account. Prefer an exact meaningful ISIN plus compatible nonblank plan/folio, with scheme-name consistency checks. For absent/placeholder ISIN, require an explicit mapping or an unambiguous reviewed fallback. An unresolved fund identity stops period preflight for the whole file; other uncertainty may produce DEFERRED after acceptance. Never select the first candidate arbitrarily. |
| FR-06 | Proposed master policy is `REQUIRE_EXISTING`: do not create or rename accounts/funds, overwrite plan/folio, or clear metadata on blank input. Missing masters stop period preflight with resolution reasons and retained source bytes. Future creation/enrichment needs a separate deterministic policy and concurrency protection. |
| FR-07 | Store order facts for unambiguous rows and link each staging row to its generated order ID in the same database transaction. Preserve unknown order direction text, zero values and nullable facts. Do not insert into `trade_records`, ledger tables or `mutual_fund_value`. |
| FR-08 | Initial `ORDER_ONLY` policy creates no transactions and changes no existing transactions, including for `COMPLETE` rows. Preserve all metadata in staging. Report completed rows as awaiting posting-policy/reconciliation work, separate from parse failures. |
| FR-09 | Any future posting mode requires an approved status/direction/date rule and an explicit strategy for existing manual transactions, partial fills and multiple orders per transaction. Posting must atomically record the transaction, same-fund link and reconciliation identity. Never use amount/date similarity alone as proof of identity. |
| FR-10 | Same-file resubmission for the same owner/account/source is a reported no-op after success and a restart after failure. Stable file identity survives renaming and parser upgrades; changed parser policy cannot launch another automatic projection. |
| FR-11 | Reject date-conflicting exports before DB source staging and retain their bytes outside DB ingestion. For accepted periods, historical/repeated/missing references defer order identity unless an audited association proves a repeated snapshot. Full equality cannot collapse legitimate identical orders. No arrival-time overwrite or settlement-based identity; period coverage does not replace row reconciliation. |
| FR-12 | Serialize projection/reconciliation chunks for the same owner/account with a database row lock; concurrent identical files produce one claim. Distinct accounts may run independently. Do not rely on process-local locks. |
| FR-13 | Commit staging outcome, order write and generated-ID link atomically per processed row within the chunk. On rollback none of those changes may remain partially committed. Previously committed chunks remain durable and are reported accurately. |
| FR-14 | Dry run performs read-only parsing, validation, matching and outcome prediction through a preview service, without business/staging or Batch metadata writes. It bypasses job submission/claim and never blocks a subsequent real import. |
| FR-15 | Produce durable outcomes with import ID, job execution ID, logical record number, source line range and safe reason codes. Separate framework read/write counts from committed business insert counts. Avoid dumping full records or secrets to ordinary logs. |
| FR-16 | Coin selection isolates legacy runners, discovery services, configurations and deletion steps. Conversely, both legacy entry points must exclude Coin launchers, jobs and infrastructure. Incompatible legacy arguments fail before launch. |
| FR-17 | Explicit Batch 6.0.3 JDBC JobRepository and qualified JdbcTransactionManager share the staging/order datasource; checkpoint/restart survives a new JVM. |
| FR-18 | Preserve immutable managed bytes, bounded parsing and safe post-completion archive; no automatic purge or original deletion. |
| FR-19 | Preflight actual target schema/history and ownership; no automatic schema repair or Flyway enabling. |
| FR-20 | Sanitized fixtures, isolated PostgreSQL and existing ownership/portfolio/pipeline regression checks are mandatory at implementation. |
| FR-21 | Maintain `coin_import_period` for each trusted owner/fund represented in an accepted file. Prevent repeated periods regardless of filename/checksum; proposed default also rejects any inclusive-date overlap. |
| FR-22 | Reserve all fund periods and the file claim atomically before staging. Enforce concurrency in PostgreSQL, report an owner-scoped conflict, and write no new source rows/orders when any fund conflicts. DRY_RUN checks but reserves nothing. |
| FR-23 | Freeze period bounds/fund mapping on restart. Keep failed/partial claims blocking, finalize coverage with durable outcomes and allow only audited, zero-write abandonment. A corrected same-period export needs a separate reconciliation workflow. |

Default treatment of negative financial values is to preserve the raw source and reject typed projection pending a direction/sign rule. Do not rewrite signs or recalculate amounts. Excess numeric scale may be accepted only when reducing scale is exact, such as trailing zero removal; never round significant digits silently.

### Ownership and master-data matching rules

The first launcher is a restricted operator CLI, not an unauthenticated public API. It must establish permission to act for the selected owner/account; a positive owner ID alone is not authentication. If users can access the launcher directly, add an authorization layer binding the authenticated principal to owner before accepting that parameter. Later REST wiring must use AccountPrincipal, never CSV or multipart owner fields.

Select the internal broker account with bound `broker_account_id=:account AND owner_user_id=:owner`; verify configured broker/source association and exact external account text. Count matches for the exact owner/broker/external tuple; unknown, ownerless, foreign or duplicate accounts fail preflight, even if an internal ID was supplied. This is particularly necessary while live V12 enforcement is absent. Check the entire file for client consistency before projection.

Fund lookup joins fund to broker with both selected account and owner predicates. Exact non-placeholder ISIN plus exact name consistency is the proposed primary match; nonblank plan/folio must agree. A master NULL where source supplies a value is a resolution gap, not permission to enrich. Blank source metadata never clears a known value. No fuzzy/global name lookup or parsing of plan from scheme name. For blank/N/A ISIN require an explicit reviewed mapping; N/A is valid text but not proof of security identity. Additional placeholder vocabulary needs policy.

One fund row holds one folio. Multiple matching folios/candidates stop period preflight; do not merge or choose first. Conservative missing-folio default: stop acceptance unless a reviewed account/ISIN/name mapping establishes the intended fund. Missing master, ambiguous fund and conflicting metadata get distinct reasons; if the intended fund cannot be established, period preflight fails before DB staging. After a valid period claim, remaining non-identity uncertainty can be DEFERRED. New exact matching APIs belong in trade-repository; do not scan only the first page from existing paged APIs.

Before committing projection, recheck ownership/matching under locks: REST can update fund names/parent relationships between preflight and writer. SQL INSERT…SELECT/update must enforce ownership and selected account and require the expected row count. Validate that each audit order/duplicate link remains in that file's owner/account; individual FKs alone do not enforce this full relationship. Do not inject unscoped legacy repositories or depend on REST request-scope beans in batch.

### Source conversion rules

Raw text remains authoritative. Empty numeric/time cells become nullable order values; the new period gate requires a nonempty verifiable trade date even though the order schema permits NULL. Whitespace-only text remains raw whitespace and is invalid for typed conversion, not silently blank. Literal N/A is legal identifier/annotation text; numeric/date/time N/A rejects typed projection unless a different policy is approved. Accept a documented plain decimal grammar; reject exponent, thousands separators, currency symbols, NaN and Infinity. Use BigDecimal(String), range validation and `setScale(targetScale, RoundingMode.UNNECESSARY)`; retain lexical zeros/scale in raw storage. PostgreSQL assignment must never perform implicit rounding.

Date conversion needs explicit policy: proposed dd/MM/uuuu with Locale.ENGLISH and ResolverStyle.STRICT. With the period gate, default `dateFormat=UNCONFIRMED` stops file acceptance with DATE_CONVENTION_UNCONFIRMED and retains bytes outside DB ingestion; do not project a NULL date as though source were blank. Strict hh:mm a converts only the local time. No timezone is inferred from the user's location/server/broker, and no execution date is invented by joining source fields.

### Lifecycle and posting rules

| Source condition | Immediate ORDER_ONLY behavior | Future unresolved policy |
| --- | --- | --- |
| PROCESSING/other pending labels | All raw fields retained; eligible order only, no transaction | Finality signal, execution date and final units/NAV; only PROCESSING is observed pending text |
| COMPLETE | Eligible order/raw snapshot, explicitly awaiting posting policy | Status/nonzero units alone cannot prove an unposted execution |
| REJECTED/CANCELLED | Source/raw order evidence only | Never infer reversal of a prior transaction; labels not observed in sample |
| Unknown/blank status or direction | Preserve exact text, flag review; unknown order direction is String | Never default to COMPLETE/BUY or use transaction enum coercion |
| Pending→complete, changed quantities/folio/status | Held-period conflict rejects normal import; retain file for reviewed correction. If no period conflict, defer ambiguous association. | Stable identity, cardinality and authoritative effective version needed |
| Older export, complete→pending | Reject held-period conflict; otherwise preserve source without regression or last-arrival overwrite | Filename/mtime/import time is not source event chronology |
| Existing manual transaction | Retain source order; no automatic link or new transaction | Amount/date/ISIN similarity alone is insufficient; explicit same-fund reconciliation required |
| Partial fill, correction or reversal | No financial mutation; defer | Approved cardinality, correction/reversal model and audit are prerequisites |

Future posting requires verified owner/fund, approved status/direction/finality, exact financial values, an execution date/time rule, a resolved unique order/fill identity and reconciliation against existing/manual postings. Create/link transaction and record resolution atomically. Preserve V9's composite same-fund FK; it permits many orders per transaction and does not establish a 1:1 order/fill rule. Do not delete or rewrite earlier transactions to resolve a changed snapshot. Current status text does not suppress portfolio inclusion.

## 6. Proposed Spring Batch contract

**Name:** `coinOrderHistoryImportJob`. New classes belong under the existing `com.trading` package structure. Use constructor injection, explicitly qualified beans and the Spring Batch APIs resolved by the current build. The current POM configures Java 25 and Boot 4.0.6; do not change those versions incidentally.

**C, resolved this review:** offline dependency:tree confirms Spring Batch core/infrastructure **6.0.3** and Spring JDBC/Tx **7.0.7**. Local 6.0.3 source shows `@EnableBatchProcessing` defaults to **ResourcelessJobRepository**; no explicit JDBC JobRepository configuration exists in the current batch source. V2 tables plus a datasource do not establish durable checkpoints. Coin shall explicitly configure `@EnableBatchProcessing` and `@EnableJdbcJobRepository(dataSourceRef="dataSource", transactionManagerRef="coinTransactionManager")`, using a qualified JdbcTransactionManager over the same datasource as staging/orders. Do not accidentally select legacy JPA/resourceless managers. The actual current context was not started or runtime-verified. [Batch 6 migration guide](https://github.com/spring-projects/spring-batch/wiki/Spring-Batch-6.0-Migration-Guide), [tagged JDBC annotation](https://github.com/spring-projects/spring-batch/blob/v6.0.3/spring-batch-core/src/main/java/org/springframework/batch/core/configuration/annotation/EnableJdbcJobRepository.java).

Use resolved `.core.job.Job`, `.core.job.parameters.JobParametersBuilder`, `.core.step.builder.StepBuilder`, `.core.launch.support.TaskExecutorJobOperator` and `.infrastructure.item.*` packages. Current code uses `new StepBuilder(name, jobRepository).<I,O>chunk(size).transactionManager(manager)` and `new JobBuilder(name, jobRepository)`. Compile against 6.0.3, not Batch 5 imports or newer 6.0.4+ reader limit methods. V2 columns match the bundled 6.0.3 schema-postgresql.sql; live sequences/privileges and atomic restart still require preflight/tests.

### Existing pipeline inventory and preservation boundary

| Inspected components | Current behavior / relevance |
| --- | --- |
| Both Boot runners and [BatchJobService](../trade-batch/src/main/java/com/trading/service/BatchJobService.java) / [LedgerBalancesBatchJobService](../trade-batch/src/main/java/com/trading/service/LedgerBalancesBatchJobService.java) | Both scanned CommandLineRunners submit async jobs from directory/recursive discovery; parameters include inputFile, truncateFlag and timestamp. Neither binds portfolio owner. |
| [BatchConfiguration](../trade-batch/src/main/java/com/trading/batch/config/BatchConfiguration.java) / [LedgerBalancesBatchConfiguration](../trade-batch/src/main/java/com/trading/batch/config/LedgerBalancesBatchConfiguration.java) | csvProcessingJob/ledgerCsvProcessingJob each run optional table-wide deletion before chunks. Trade properties chunk1000/skip100; ledger chunk1000/skip3. Broad Exception skipping; separate executors. No Coin reuse of delete/discovery/skip behavior. |
| [TradeFileItemReader](../trade-batch/src/main/java/com/trading/batch/reader/TradeFileItemReader.java) / [LedgerFileItemReader](../trade-batch/src/main/java/com/trading/batch/reader/LedgerFileItemReader.java) | beforeStep attaches inputFile resource. Read-count logging is not commit evidence; no Coin header contract. |
| [TradeFieldSetMapper](../trade-batch/src/main/java/com/trading/batch/reader/mapper/TradeFieldSetMapper.java) / [LedgerRecordFieldSetMapper](../trade-batch/src/main/java/com/trading/batch/reader/mapper/LedgerRecordFieldSetMapper.java) | Different positional schemas, ISO dates, trimToNull and numeric conversions; unsuitable for lossless Coin source mapping. |
| [TradeRecordItemProcessor](../trade-batch/src/main/java/com/trading/batch/processor/TradeRecordItemProcessor.java) / [LedgerRecordItemProcessor](../trade-batch/src/main/java/com/trading/batch/processor/LedgerRecordItemProcessor.java) | Trade pass-through; ledger supplies filename/current audit time; debug toString logging must not be copied. |
| [TradeRecordItemWriter](../trade-batch/src/main/java/com/trading/batch/writer/TradeRecordItemWriter.java) / [LedgerRecordItemWriter](../trade-batch/src/main/java/com/trading/batch/writer/LedgerRecordItemWriter.java) | JPA saveAll in chunks; exceptions rethrow. Preserve these pipelines' technology and behavior. |
| [JobExecutionListener](../trade-batch/src/main/java/com/trading/batch/listener/JobExecutionListener.java) / [properties](../trade-batch/src/main/resources/application.properties) | Logs generic completion banner/parameters/counts; no durable business import report/archive logic despite declared path properties. Flyway and SQL/Batch schema init disabled. No batch-local test sources found. |

### Parameters and configuration

| Parameter/configuration | Contract |
| --- | --- |
| `inputFile` | Required readable file; copied/claimed into an immutable managed location before parsing. Managed copy must match the supplied checksum. Path is not business identity. |
| `ownerUserId` | Required trusted internal owner; never derived from CSV client ID. |
| `brokerAccountId` | Required existing account owned by that user; broker/source configuration must identify Coin input. |
| `fileSha256` | Computed from actual bytes; verify again before restart. Identifying parameter. |
| `sourceSystem` | Fixed `ZERODHA_COIN` for this format; identifying parameter. |
| `parserVersion`, `policyVersion` | Explicit supported String versions; nonidentifying but immutable for the claimed file. Mismatch requires audited reprocessing, not a new automatic import. |
| `dateFormat` | UNCONFIRMED default; dd/MM/uuuu only after policy approval. Frozen across restart; unconfirmed dates prevent period preflight. |
| `periodStart`, `periodEnd` | Proposed required strict ISO `uuuu-MM-dd` export bounds, inclusive, start <= end. Frozen nonidentifying parameters/snapshot fields; changing them cannot bypass checksum identity or held periods. |
| `fundMapping` | Validated per-record mapping to owned internal fund IDs, frozen in the safe configuration snapshot. Every represented fund receives a period claim; caller-supplied IDs are revalidated. |
| `mode` | Required launcher option DRY_RUN or IMPORT. DRY_RUN uses a read-only preview service and bypasses JobRepository/claim; IMPORT launches the job. |
| `postingPolicy` | Initially only `ORDER_ONLY`; alternative modes require the decision in section 10. |
| `masterPolicy` | Only REQUIRE_EXISTING initially; frozen in safe configuration snapshot. |
| `importFileId` | Nonidentifying Long from claim; never trusted merely because caller supplied it. Must match scope/hash. |
| `restartExecutionId` | Optional launcher control for a failed/stopped execution; validate original owner/hash/contract. Not a new identifying parameter. |
| `batch.coin.chunk-size` | Proposed default 100; positive and configurable. |
| `batch.coin.max-row-rejections` | Proposed default 0; explicit opt-in for partial acceptance. Header/ownership/security/infrastructure failures are never skippable. |
| `batch.coin.retry-attempts` | Proposed 3 total attempts for SQLSTATE 40001/40P01 only; backoffs 250ms then 1s. No retry for validation/ownership/unknown commit outcome. |
| `batch.coin.work-directory` | Required private managed location; not the upload receipt directory by assumption. |
| `batch.coin.archive-directory`, `archive-enabled`, `purge-enabled` | Private external path; archive opt-in false; purge false/unsupported initially. |
| `batch.coin.max-file-bytes`, `max-record-bytes`, `max-record-lines` | Proposed 100 MiB, 1 MiB, 1,000 physical lines; fail rather than truncate; implement limits in 6.0.3 wrapper. |
| `batch.coin.lock-timeout`, `statement-timeout` | Proposed 5s/30s transaction-local; timeout fails unless explicitly retry-classified. |
| Framework configuration | Explicit environment/profile and externalized credentials; Flyway=false; SQL init=never; Batch initialize-schema=never; Boot job.enabled=false; custom Coin launcher only. |
| Logging | INFO operational summaries; disable JDBC parameter/raw-record DEBUG in Coin profile. |

Owner/account (Long), checksum/source (String) define stable job identity with the job name. Path, parser/policy versions and operational timestamps do not create another identity. Freeze period bounds, per-record fund mapping, conversion, matching and rejection policy in the file configuration snapshot; reject incompatible restart parameters. No timestamp/run.id/RunIdIncrementer may bypass replay. Operational request IDs are logging-only. Dry runs can repeat against current masters without reserving a completed instance. Do not put credentials/raw cells/external client IDs in JobParameters.

Steps:

1. **Preflight and claim:** verify configuration, owner/account, schema capabilities, immutable bytes, complete header/account consistency, declared period, all trade dates and all fund mappings. Atomically claim the file and every fund period before launching staging. Any period conflict rolls back the new claim/reservations. A same-file restart reuses its reservations. Dry run uses a read-only path.
2. **coinStageRowsStep:** step-scoped lossless ItemStreamReader, saveState=true, unique stream name, logical ordinal/byte offset/hash checkpoints. Pure processor validates fields; JDBC writer stores immutable raw rows/reasons in chunks of 100. No private cells in ExecutionContext. Invalid typed values remain raw REJECTED evidence.
3. **coinValidationGateStep:** separate tasklet after committed staging checks full shape/hash/count and rejection threshold. At default zero, any rejection blocks all order projection while preserving staging. Business uncertainty becomes DEFERRED, not guessed typed values.
4. **coinProjectOrdersStep:** staged-row keyset reader by file/record number, no outcome-filtered OFFSET pagination; deterministic resolver plus writer rechecks under locks. O insert, raw-row order link/outcome and checkpoint commit atomically. Do not return processor null to hide records. Terminal outcomes are explicit no-ops.
5. **coinFinalizeStep:** derive/reconcile counts and atomically finalize file plus per-fund period statuses. Dedicated completion listener reports real status, safely handles absent endTime and sets custom exit status; archives only after final commit. Archive failure never requires another order import.

Reader dialect must support actual unquoted JSON-shaped tag text and standard quoted CSV. Structural quote opens at field start; literal quotes inside unquoted text remain literal. Quoted fields support doubled quotes, embedded commas and exact CRLF/LF. A bounded state-machine/wrapper is required; no new framework/library is mandated. If using FlatFileItemReader/tokenizer, prove losslessness: 6.0.3 DefaultFieldSet.readString trims; readRawString preserves only already-tokenized values, and tokenizer/record-separator processing may have changed whitespace/newlines earlier. Retain original byte slices regardless. Disable comment suppression and silent blank-line loss. Unclosed quotes fail on known offset, never guessed boundaries. [Tagged FieldSet implementation](https://github.com/spring-projects/spring-batch/blob/v6.0.3/spring-batch-infrastructure/src/main/java/org/springframework/batch/infrastructure/item/file/transform/DefaultFieldSet.java), [reader reference](https://docs.spring.io/spring-batch/reference/readers-and-writers/flat-files/file-item-reader.html).

Use the portfolio datasource and JDBC transaction manager consistently for staging and mutual-fund writes. Keep persistence contracts/implementations in `trade-repository`; batch orchestrates them. Existing JPA trade/ledger jobs keep their current persistence technology. Verify job-repository transaction coordination in an isolated context test.

**Concrete startup proposal:** dedicated `com.trading.coin.CoinOrderHistoryBatchApplication` with narrow component scanning/explicit imports for Coin and required JDBC components; never scan com.trading root, either legacy Boot application, configurations or discovery services. Add an explicit coin Maven packaging profile/classifier selecting this main; preserve the existing packaged legacy main and JPA jobs. The ordinary batch JAR is not a Coin launcher. Reject legacy directory/truncate parameters before launch. Use a synchronous TaskExecutorJobOperator/SyncTaskExecutor for initial CLI delivery and wait for completion. Packaging choice remains an architectural proposal requiring confirmation before implementation, not a change made by this task.

**C/I, 8 October handoff review — isolation in both directions:** both existing application classes are in `com.trading` and use `@SpringBootApplication` without a narrower scan. Adding annotated Coin components beneath that package makes them candidates for the legacy scans. A narrow Coin scan does not prevent that reverse discovery, and changing the packaged main class alone does not exclude other classes. Require explicit legacy scan exclusions or an equally tested registration boundary that keeps every Coin launcher/configuration/job out of both legacy contexts. Register shared Coin repository implementations explicitly where needed so their addition cannot unexpectedly initialize Coin infrastructure in REST or MCP. The proposed effect is inferred from source, not reproduced by starting a runner. See the [trade runner](../trade-batch/src/main/java/com/trading/TradeBatchApplication.java), [ledger runner](../trade-batch/src/main/java/com/trading/LedgerBalancesBatchApplication.java) and [Boot 4.0.6 annotation source](https://raw.githubusercontent.com/spring-projects/spring-boot/v4.0.6/core/spring-boot-autoconfigure/src/main/java/org/springframework/boot/autoconfigure/SpringBootApplication.java). Context tests must check registered beans, not assume absent classes in a classifier JAR.

## 7. Errors, restart and operating results

### Concurrency and duplicate identity

Logical record ordinal, not row hash, identifies a row within a file. Identical rows remain separate raw rows; reordered/newline-changed/overlapping exports are separate byte-level files. Missing or repeated references defer rather than drop. Full field equality is candidate evidence only; DUPLICATE_SNAPSHOT requires a proven association and may be zero initially. A same-file replay is a file-level no-op, not nine newly read duplicate rows. Historical manual orders lacking references still need review before enabling new-reference projection for an existing portfolio.

First enforce the period gate for all funds in the file; only accepted non-conflicting files reach staging/projection. Use business-file unique constraint plus JDBC JobRepository's stable instance uniqueness for same-file races. One launch proceeds; other reports IN_PROGRESS or existing durable result. For accepted files that may reference the same source orders, lock the selected broker row with `FOR UPDATE` and owner predicate inside each projection chunk. Use consistent account→funds in ascending ID→file/period claim→import-row lock order and READ COMMITTED; recheck the held claim in each staging/projection write transaction and recheck candidates only after lock acquisition so a waiting writer sees earlier commits. No cached pre-lock match decision. Lock/revalidate fund parent before insert. Short chunk locks release on rollback/crash and need no expiry lease; different accounts can run independently. Future reconciliation writers must use this protocol too. Ordinary FKs and process-local locks alone cannot prevent overlap races.

Raw staging commits before projection. Projection atomically writes order + raw-row outcome/order link + checkpoint with the same datasource/JDBC transaction manager; no REQUIRES_NEW fragments. Crash after order insertion but before link must roll back that chunk. Crash after commit but before summary must not insert it twice. Revalidate durable terminal links and derive totals rather than incrementing counters outside transactions.

File-level problems fail before domain writes: unreadable/changed file, invalid encoding/header/record boundaries, untrusted owner, wrong account or incompatible schema. Persist safe file failure only after authority is validated; if DB is unavailable use a secure operator report. Never attach foreign records to an owner's audit. No `skip(Exception.class)` fallback is allowed. Framework skip limit is **0**; recoverable row rejections are explicit result objects written to audit. Retry only PostgreSQL serialization failure 40001/deadlock 40P01, three total attempts with 250ms/1s backoff and full chunk rollback. Connection loss, constraint/security/SQL/programmer failures fail. Unknown commit outcome requires read-only checkpoint/outcome reconciliation before restart, not a blind insert retry.

For recoverable row errors, persist a deterministic rejection record before enforcing the configured rejection threshold. Stage-only evidence can survive even when projection is stopped; distinguish that from business writes. At threshold zero, validate/stage the file first and stop before order projection if any row is rejected. Above zero, project only eligible rows and report partial acceptance. A deferred business match is retained for resolution and is not silently counted as loaded.

For every structurally valid file, final mutually exclusive row outcomes must reconcile:

`staged rows = inserted orders + updated orders + duplicate snapshots + deferred rows + rejected rows + remaining rows`

After complete staging, source record count equals staged count; a completed import requires remaining=0. A failed strict rejection gate leaves valid nonterminal rows STAGED/remaining, with zero order projection. If quoting prevents reliable boundaries, total source rows is unknown, not zero; report only recognized/staged counts. Initially updated orders=0 and posted transactions=0; changed snapshots are deferred. A sample file may be successfully ingested as orders while completed orders still await posting. Framework reads across stage/project steps are not additive source counts.

Recommended business results: COMPLETED, COMPLETED_WITH_ISSUES, FAILED, plus a distinct dry-run result. CLI exit codes: 0 completion/verified no-op; 2 completed ingestion or preview with issues; 1 failed execution/preflight/strict rejection gate; 3 concurrent IN_PROGRESS. Framework BatchStatus and custom ExitStatus must both be visible (COMPLETED can accompany COMPLETED_WITH_ISSUES); report dry-run committed=0. Archive failure after DB success is ARCHIVE_FAILED/exit2, repaired independently without another import. Await actual execution completion, not submission.

Restart must recheck immutable bytes, owner/account, schema, held period reservations/fund mapping, frozen parser/policy/date/rejection contract and absence of an active execution, then use JobOperator.restart(failedExecutionId) or an equivalent documented same-instance launch. Resume completed staging/projection checkpoints and durable row outcomes; do not add a timestamp. Chunk locks need no stale lease recovery. A crashed execution left STARTED/UNKNOWN requires confirmed process death and supported Batch recovery to a restartable state; elapsed time alone cannot authorize a second writer. No manual metadata edits as the normal runbook. Reports distinguish original execution/restart and persisted counts. Different bytes or policies require new evidence or a separate audited reprocessing operation, not mutation of the old identity.

Keep the source file unchanged. Managed archive moves happen only after success; on failure retain the work copy, checkpoint and rejection report. Retention period and manual-resolution entry point are open operational decisions. No automatic purge or broad directory scan is part of the initial job.

### Batch metadata, observability and operational runbook

Batch metadata retains instance parameters/key, execution/step statuses/times, read/write/skip/rollback counts and ExecutionContext checkpoints. It does not inherently store all source cells, row rejection reasons, master-matching evidence, business deduplication or archive state. Those belong in the import audit tables. A staging write count is not an order/transaction count. Report import/execution IDs, hash, policy, committed outcome totals, safe reason counts, status distribution and separate awaiting-posting counts. Do not log full rows, private paths, account identifiers or raw JDBC parameters. Rejection reports must render formula-like cells safely without modifying retained source strings.

Proposed runbook (future tooling; no launcher exists yet):

1. Establish authorized owner/account, approved policies, environment/profile and correct dedicated Coin artifact. Do not use a legacy batch startup to test connectivity.
2. Read-only preflight target information_schema/pg_catalog: V10/V11/V13 columns/types/defaults, owner/parent/same-fund FKs, V12 equivalent unique constraint, V2 metadata tables/sequences, required SQL/sequence privileges and Flyway history/checksums. Catalog presence is not backfill proof. Resolve drift through separately approved migration work; never auto-enable Flyway or edit history.
3. Review owner-scoped missing/duplicate master matches and manually entered orders. This investigation did not query those records. Use access-controlled matching results with sanitized summaries.
4. After implementation/testing and separate operational authorization, create immutable private managed copy via temporary file/atomic move, hash verified against source. Opaque managed names, bounded paths and no symlink/cross-owner escape. Run DRY_RUN, examine predictions and outstanding policy decisions; no DB writes of any kind.
5. Run explicit IMPORT only when authorized, supplying the proposed required export-period dates and resolved fund mapping. Reserve all periods atomically; stop the whole file on conflict. Compare read vs committed counts, period states, reasons/remaining work and controlled portfolio checks. Nonzero exit may coexist with previously committed chunks.
6. For FAILED/STOPPED follow the restart contract above. For completed-with-issues retain raw data for audited resolution; successful replay does not resolve deferred records.
7. Optional archive only after durable completion, verify archived checksum and record archive_status. Resume archive independently after a crash. Preserve original; no purge until retention policy separately approved.

Current upload integration: `POST /api/mutual-fund-txns/zerodha-upload` returns HTTP202/UPLOADED. ZerodhaTransactionUploadService.stage checks positive owner/nonempty CSV filename, sanitizes basename and writes to owner/UUID.csv. It performs no parsing, queuing, job execution or database persistence. Future REST/UI wiring must carry AccountPrincipal ownership, selected owned account, durable upload→import association and execution statuses while preserving the existing API. It remains outside the initial importer scope.

## 8. Implementation impact and migration readiness

| Area | Proposed changes |
| --- | --- |
| `trade-model` | Import row/result types only where shared; preserve existing public model contracts. |
| `trade-repository` | Add source staging/audit and period-claim repositories, owner-scoped exact matching/reconciliation operations, atomic order projection, forward migration (including reviewed btree_gist prerequisite) and PostgreSQL tests. Reuse `UserPortfolioRepositoryFactory` ownership patterns. |
| `trade-batch` | Coin-specific config, validator, reader/mapper, processor, writer, launcher selection, listener and focused job/context tests. Add isolated Coin properties rather than reusing `CSV_INPUT_FILE`. |
| Existing runners | Minimal, regression-tested job selection/isolation change only; no unrelated pipeline refactoring or table deletion. |
| Packaging/infrastructure | Proposed Coin-specific main/context and Maven profile/classifier in trade-batch/pom.xml; leave legacy main/JPA behavior intact. Configure explicit Coin JDBC JobRepository/transaction manager and external Coin properties. |
| REST/UI | Initial scope: no changes. Future upload-to-job integration must persist ownership and execution state; an `UPLOADED` receipt must not imply imported rows. |
| Documentation | Keep this specification, job runbook and affected data/workflow descriptions consistent with implementation. |

Before an operational import, inspect the target database catalogs and migration history read-only, verify account ownership constraints and matching cardinality, and reconcile schema drift. Applications currently disable Flyway. Do not simply enable it based on the presence of source migration files. Use a new reviewed migration and isolated PostgreSQL migration tests; production migration/import needs its own explicit operational authorization.

Proposed files: CoinImportRow/File/Outcome types in trade-model only when shared; CoinImportRepository and owner-bound JDBC/RowMapper implementation in trade-repository; CoinOrderHistoryBatchApplication, CoinBatchConfiguration, reader/converter/stage/project writers/gate/listener and tests in trade-batch. Next free migration is currently a **candidate V14**, not a reserved version: add the file/row audit tables plus `coin_import_period`, their keys/indexes and period exclusion enforcement described above. Include `btree_gist` provisioning in the reviewed migration plan, not as an automatic startup action. Do not recreate existing metadata or modify applied scripts. Validate populated upgrade and fresh schema on isolated PostgreSQL; full V1/V4 extension dependencies cannot be bypassed and then claimed verified. Flyway second migrate is a no-op; individual SQL scripts are not necessarily independently rerunnable.

## 9. Acceptance criteria and test plan

These are **planned checks**, not test results. Seed owned broker/fund masters in an isolated database; never use production data for tests.

| Test | Required outcome | Requirements |
| --- | --- | --- |
| Supplied-file structure using sanitized equivalent | 9 raw rows; all 16 fields retained; 6 COMPLETE, 3 PROCESSING and 9 BUY, with blank/zero correlations. With confirmed date policy and reviewed/seeded unambiguous master mappings: 9 orders/0 transactions, unchanged valuations/totals. Under UNCONFIRMED date or unresolved fund identity (including missing folios without a reviewed mapping), expect period preflight to stop acceptance and retain the source outside DB staging. | FR-01–08, FR-15 |
| Pending metadata | All five V11 metadata fields retained even without transaction links; no loss or fabricated transaction. | FR-07–09 |
| Exact text | Leading-zero/slashed IDs, `N/A` ISIN, empty/whitespace metadata, plain/JSON-shaped tags and arbitrary status round-trip exactly in raw storage. | FR-01–03 |
| CSV dialect | Actual unquoted JSON-with-quotes dialect plus synthetic BOM/no BOM, LF/CRLF, final no-newline, reordered headers, quoted commas, escaped quotes and multiline fields; exact raw bytes/decoded cells/ordinals survive restart. Duplicate/missing/unknown headers fail before order writes. | FR-01–02, FR-10, FR-18 |
| Numeric/date/time boundaries | Six-place decimals, precision overflow, excess significant scale, blank versus zero, negative values, invalid calendar dates, ambiguous source convention and 12 AM/PM have explicit outcomes with no silent coercion. | FR-03 |
| Ownership and matching | Two owners, same client text across broker contexts, foreign/unknown account, mixed-account file, duplicate masters, missing/placeholder ISIN and multiple folios cannot cause cross-owner writes or arbitrary matching. | FR-04–06 |
| Same-file replay | Second run and renamed identical file produce zero additional orders/rows. Dry run does not block real import. | FR-10, FR-14 |
| Overlap and lifecycle | Date-conflicting exports are rejected before source staging; their bytes remain available for review. Within accepted periods, repeated/changed/missing references and identical legitimate rows remain preserved and reconciled/deferred. No duplicate posting or last-arrival overwrite. | FR-09–12, FR-21–23 |
| Concurrent launch | Two identical submissions produce one claimed import; simultaneous overlapping files for an account cannot race into duplicate orders. Different owners remain isolated. | FR-10–12 |
| Rollback and restart | Fail after a committed chunk and during an order/link write; rerun resumes without duplicate or orphan data. Changed input bytes block restart. | FR-10, FR-13 |
| Fault policy | Header/security/SQL failures are fatal; transient retry is bounded; rejection threshold zero prevents projection on row errors; permitted partial acceptance is reported honestly. | FR-13–15 |
| Startup regression | Context launched by the dedicated Coin artifact registers only the Coin runner/job and explicit JDBC infrastructure; legacy runner/services/configs/delete beans absent; incompatible truncate arguments rejected. Conversely, both legacy entry-point contexts register no Coin runner/job/infrastructure and preserve trade/ledger behavior. Use controlled test inputs and isolated databases; check bean registration separately from JAR class contents. Shared Coin repository additions must not initialize Coin jobs in REST/MCP. | FR-16–17, FR-20 |
| Schema migration | Fresh proposed schema additions and upgrade preserve existing rows; keys/foreign keys/ownership checks work on PostgreSQL; repeat migration is a no-op. | Section 8 |
| Future posting, if approved | COMPLETE eligibility/date rule, same-fund links, existing manual transactions, partial fills, correction/reversal and duplicate completion cannot double-count holdings. | FR-09 |
| Boundary corruption/limits | Invalid UTF-8/NUL, blank/comment-looking lines, malformed width, unclosed quotes and record/file limit overflow: exact retained bytes, safe reasons, no guessed boundaries or silent filtering. | FR-01–03, FR-13, FR-18 |
| Durable restart and infrastructure | Separate JVM resumes staged/projected checkpoints; inject failures mid-chunk, after order insert/before link, after commit/before finalize; no orphan/duplicate rows. Verify actual JDBC manager/repository, not just mocked callbacks. | FR-10, FR-13, FR-17 |
| Dry run and reporting | Repeat preview against changed masters: no changes to business/audit/Batch tables; predictions labelled. Count equations, strict-gate remaining, unknown totals, IN_PROGRESS and exit codes reconcile. Archive failure/recovery never duplicates DB rows. | FR-14–15, FR-18 |
| Both-owner portfolio invariance | Compare OwnedTxnRepository summaries/fund investments and JdbcAnalyticsRepository portfolio for both synthetic owners before/after pending, COMPLETE-unposted and unknown-status orders; all totals unchanged. | FR-07–09, FR-20 |
| Schema drift/preflight | Missing V12/FK/metadata/sequence/privileges or incompatible baseline history blocks import; preflight never migrates. PostgreSQL fresh/upgrade/rollback/second-migrate checks use synthetic data only. | FR-19–20 |
| Period equality and boundaries | Same owner/fund: equal, contained, enclosing and partially overlapping ranges fail, including one shared boundary day; adjacent non-overlapping and single-day ranges behave correctly. Different owned funds/users remain independent. | FR-21 |
| Changed files and period provenance | Renamed/changed/reordered files cannot bypass held coverage. Missing/reversed/infinite bounds, missing/ambiguous source dates and out-of-range rows fail preflight. Declared empty/quiet days remain covered; no min/max substitution. | FR-21–22 |
| Multi-fund claim atomicity | A file with several resolved funds creates one period per fund. A conflict on one fund rolls back every new period/file claim, with zero staged rows/orders. An unresolved fund stops acceptance; absent funds receive no coverage. | FR-21–22 |
| PostgreSQL period concurrency | Independent connections race for equal/overlapping user/fund ranges: one claims, the other receives a safe conflict. Exercise the real exclusion constraint and btree_gist prerequisite, not only a mocked precheck. No cross-owner file/fund association. | FR-21–22 |
| Period failure and restart | Crash after claim/before launch, after staging, after order commit and before finalization: held coverage always remains; same-file recovery reuses its reservations and never duplicates rows. Partial/failed imports cannot be bypassed by a different file. | FR-22–23 |
| Period release and dry run | Preview creates no file/row/period/Batch records. Audited release requires no active job and zero staged/business writes, retains history, atomically abandons the file and all its claims, and prevents a stale execution from writing or restarting. Release with partial data is rejected. | FR-14, FR-23 |
| Period coverage and lifecycle | COMPLETED_WITH_ISSUES consumes coverage without implying posted investments. Later same-period status corrections fail normal import and require audited reconciliation; changing dates cannot bypass frozen same-file identity. Existing portfolio baseline coverage is reviewed, not inferred from min/max. | FR-08–11, FR-23 |

Run focused batch/repository tests and relevant REST ownership/analytics regression tests when implemented, then the required Maven reactor checks. Suggested commands are `mvn -pl trade-batch -am test` and `mvn -pl trade-rest -am test`; use isolated PostgreSQL/Testcontainers. Report Docker/runtime limitations rather than claiming skipped checks passed. Maven version/API compatibility and packaged launcher behavior also need verification.

## 10. Decisions before implementation or posting

| Decision | Proposed default | Impact if unresolved |
| --- | --- | --- |
| Should completed rows post transactions automatically? | ORDER_ONLY until approved; all fields remain in DB staging/orders. | UI transactions/holdings will not reflect newly imported orders. |
| Does trade date mean the posted transaction date, and what time is stored? | Preserve source date/time separately; no invented timestamp. | Blocks automatic transaction posting; does not block raw ingestion. |
| Is day/month order guaranteed by the source? | UNCONFIRMED prevents period acceptance; propose dd/MM/uuuu after confirmation, frozen in policy. | Source dates must be verified against the declared range before claiming/staging. |
| What constitutes a stable source order/fill key? | Account-scoped candidate matching; defer collisions/changed snapshots. | Required in addition to period tracking; same-period corrections use a separate audited workflow. |
| Exact period or any overlap? | User requires same-period prevention; propose rejecting every overlapping inclusive range. | Overlap breadth remains unconfirmed; the proposed exclusion constraint implements the stricter rule. |
| Where do coverage dates come from? | Require explicit export start/end, validate all source dates; never assume min/max proves export coverage. | Await period-origin preference and source date convention. |
| Are new accounts/funds or metadata enrichment allowed? | REQUIRE_EXISTING; explicit mapping for ambiguity. | Unresolved fund identity now blocks period reservation/file acceptance. |
| What should happen for rejected/cancelled/unknown statuses? | Preserve as source order evidence; never post automatically. | Any reversal or prior transaction correction needs its own rule. |
| Are partial imports acceptable? | Rejection threshold 0; business ambiguity is explicit DEFERRED. | Nonzero rejection allowance must be configured deliberately. |
| How are deferred rows resolved and reprojected? | Retain source and reason; require an explicit audited resolution/reprocessing operation. | Ingestion may finish with issues until a resolution interface/process is specified. |
| CLI/packaging versus upload-driven launch? | Restricted operator CLI, narrow Coin context, explicit packaging profile/classifier; existing legacy main unchanged. | Architectural selection must be confirmed before implementation; authenticated queue/status UI is separate. |
| Retention, archive and size limits? | Bounded private managed files; archive opt-in; preserve original and audit; no automatic purge. | Operations must choose retention/capacity/access policy. |
| Which environment/schema is the deployment target? | Resolve through externalized profile/config; verify catalogs first. | No live migration or import should run until target schema is reconciled. |

This document does not convert proposed defaults into user-approved business policy. The reusable [analysis prompt](COIN_ORDER_HISTORY_ANALYSIS_PROMPT.md) can refresh it with confirmed decisions and current code.

## 11. Evidence references

- [Repository instructions](../AGENTS.md): module boundaries, JDBC policy and batch preservation.
- [Root build](../pom.xml) and [batch build](../trade-batch/pom.xml): configured versions, dependencies and packaged main class.
- [V3 schema](../trade-repository/src/main/resources/db/migration/V3__application_schema.sql), [V6 ownership](../trade-repository/src/main/resources/db/migration/V6__portfolio_ownership.sql), [V9 orders](../trade-repository/src/main/resources/db/migration/V9__mutual_fund_orders.sql), [V10 folios](../trade-repository/src/main/resources/db/migration/V10__move_folio_to_mutual_fund.sql), [V11 metadata](../trade-repository/src/main/resources/db/migration/V11__move_order_metadata_to_transactions.sql), [V12 account uniqueness](../trade-repository/src/main/resources/db/migration/V12__unique_broker_account_per_owner.sql), [V13 source ISIN](../trade-repository/src/main/resources/db/migration/V13__relax_mutual_fund_isin_length.sql).
- [MutualFundOrder](../trade-model/src/main/java/com/trading/model/MutualFundOrder.java), [MutualFundTxn](../trade-model/src/main/java/com/trading/model/MutualFundTxn.java), [owner factory](../trade-repository/src/main/java/com/trading/repository/UserPortfolioRepositoryFactory.java), [OwnedOrderRepository.save/update](../trade-repository/src/main/java/com/trading/repository/impl/OwnedOrderRepository.java).
- [MutualFundBrokerAccount](../trade-model/src/main/java/com/trading/model/MutualFundBrokerAccount.java), [MutualFund](../trade-model/src/main/java/com/trading/model/MutualFund.java), [TransactionType](../trade-model/src/main/java/com/trading/model/enums/TransactionType.java), [OwnedBrokerRepository](../trade-repository/src/main/java/com/trading/repository/impl/OwnedBrokerRepository.java), [OwnedFundRepository](../trade-repository/src/main/java/com/trading/repository/impl/OwnedFundRepository.java), [OwnedPortfolioJdbc.brokerOwned/fundOwned/insert](../trade-repository/src/main/java/com/trading/repository/impl/OwnedPortfolioJdbc.java), [JdbcUserPortfolioRepositoryFactory](../trade-repository/src/main/java/com/trading/repository/impl/JdbcUserPortfolioRepositoryFactory.java).
- Contracts: [broker](../trade-repository/src/main/java/com/trading/repository/MutualFundBrokerAccountRepository.java), [fund](../trade-repository/src/main/java/com/trading/repository/MutualFundRepository.java), [order](../trade-repository/src/main/java/com/trading/repository/MutualFundOrderRepository.java), [transaction](../trade-repository/src/main/java/com/trading/repository/MutualFundTxnRepository.java). Legacy [fund SQL](../trade-repository/src/main/java/com/trading/repository/impl/MutualFundRepositoryImpl.java) and [transaction SQL](../trade-repository/src/main/java/com/trading/repository/impl/MutualFundTxnRepositoryImpl.java) corroborate metadata/null behavior but are unscoped and unsuitable for new imports.
- [OwnedTxnRepository](../trade-repository/src/main/java/com/trading/repository/impl/OwnedTxnRepository.java) and [JdbcAnalyticsRepository](../trade-repository/src/main/java/com/trading/repository/impl/JdbcAnalyticsRepository.java): metadata storage and portfolio query behavior.
- [BatchConfiguration](../trade-batch/src/main/java/com/trading/batch/config/BatchConfiguration.java), [BatchJobService](../trade-batch/src/main/java/com/trading/service/BatchJobService.java), [TradeFileItemReader](../trade-batch/src/main/java/com/trading/batch/reader/TradeFileItemReader.java), [trade runner](../trade-batch/src/main/java/com/trading/TradeBatchApplication.java), [ledger runner](../trade-batch/src/main/java/com/trading/LedgerBalancesBatchApplication.java).
- [ZerodhaTransactionUploadService.stage](../trade-rest/src/main/java/com/trading/upload/ZerodhaTransactionUploadService.java): owner-directory staging only.
- [MutualFundTxnController.uploadZerodhaTransactions](../trade-rest/src/main/java/com/trading/controller/MutualFundTxnController.java): exact upload route and HTTP202; [V2](../trade-repository/src/main/resources/db/migration/V2__spring_batch_schema.sql): Batch metadata schema; [dev](../trade-batch/src/main/resources/application-dev.properties) / [prod](../trade-batch/src/main/resources/application-prod.properties): configured catalog targets.
- [Coin schema explanation](../coin-order-schema.md), [data dictionary](project-understanding/DATA_MODEL.md), [batch/upload workflows](project-understanding/WORKFLOWS.md), [project report](PROJECT_UNDERSTANDING.md): context corroborated where used; historical runtime observations remain dated.

Verification performed on 7 October for this specification: source CSV byte/profile analysis; all 16 mappings checked against migration/model/JDBC evidence; limited read-only catalogs in both configured local databases; offline dependency resolution; documentation coverage/links and source checksum checks. No business records were queried or changed, no job/service was started, and no application test result is claimed. Python CSV parsing does not prove the future Java reader accepts/preserves this dialect. Actual master cardinalities, ownership backfill, full privileges/sequences, application deployment/wiring, lifecycle semantics, migration execution and new importer restart/concurrency remain unverified.

The successful inspection command was `mvn -o -pl trade-batch -am dependency:tree '-Dincludes=org.springframework.batch:*,org.springframework:spring-jdbc,org.springframework:spring-tx'`; an initial unquoted wildcard invocation failed in the shell and was corrected. This is not compile/test verification. Initial sandbox loopback denial required an authorized read-only rerun; local catalog JSON formatting was corrected without changing query scope. Historical project test counts remain dated results, not checks performed for this refresh.

On 8 October the resumed handoff confirmed the same branch/commit, unchanged source-file SHA-256, all 16 mapping/profile rows, FR-01 through FR-20, and local evidence-link targets. Re-read V9–V11/V13 storage changes, owner-scoped order/transaction/analytics SQL, upload staging and both legacy entry points; tightened the proposed startup isolation and its acceptance checks above. No database connection, build, application test, importer execution, service change or implementation was performed in this continuation. Policy decisions in section 10 remain open.

The later 8 October user request adds user/fund period tracking as a required design capability. Reviewed current ownership/fund SQL, V1 extension declarations and PostgreSQL range-constraint documentation; updated period schema, claim/retry rules, parameters, acceptance tests and related project notes. No SQL was executed and the proposed constraint is not runtime-tested. Reject-any-overlap and explicitly supplied bounds remain proposed defaults until the user confirms them.
