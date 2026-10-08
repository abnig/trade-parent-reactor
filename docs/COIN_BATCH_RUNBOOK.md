# Coin order-history batch job

Implementation: 8 October 2026, `main` at `70b9f2c2e8621ed396019c6378c1a61b019cf701`
plus the uncommitted Coin changes. This document describes implemented behavior;
the [requirements](COIN_ORDER_HISTORY_BATCH_REQUIREMENTS.md) retain the original
source analysis and design history. The REST upload integration was added on 9 October 2026. No application database
has been migrated or imported as part of development.

## Behavior

- `VALIDATE` checks the CSV structure and every recoverable record/field in every
  supplied file without opening Spring or a database. It reports file ordinal,
  record ordinal, field and error code. Unclosed quotes, resource limits or an
  invalid header prevent interpreting further records in that file.
- `DRY_RUN` additionally checks schema prerequisites, account ownership, fund
  matching, order identity, status changes and period conflicts. It creates no
  managed copies, audit rows, period claims, orders, transactions or Batch metadata.
- `IMPORT` performs the same preflight over all supplied files, then imports
  them sequentially. Each file is rechecked while reserving its periods.
  Separate files are **not one atomic transaction**; an input conflict with an
  earlier file, concurrent change or later runtime failure can stop the command
  after an earlier file has completed.

The job has three steps: stage immutable source rows, project/upsert orders in
transactional chunks, and finalize the file/period states. There is no skip policy.
A failed chunk rolls back and the file retains its period reservation. The
launcher waits for completion and returns a nonzero process exit code on failure.

The parser supports UTF-8/BOM, CRLF/LF/CR, reordered exact headers, quoted commas,
doubled quotes and multiline cells. Quotes within an unquoted cell remain literal
so the observed Coin JSON-shaped tags survive. All 16 decoded cells and original
record bytes/offsets/header are retained. Blanks, whitespace, leading zeros and
text such as `N/A` are preserved; invalid numeric values are reported, never
rounded or silently replaced. Defaults bound a file to 100 MiB, a record to
1 MiB/1,000 physical lines, and a file to 100,000 data records.

## Ownership, matching and updates

The trusted owner ID and internal broker account ID are mandatory. Each raw
`client_id` must exactly match that owned account's external account ID. No
accounts or funds are created. Matching requires a unique owned fund with exact
scheme name, ISIN, and any supplied plan/folio. Missing folio or unusable ISIN
requires an explicit record-to-fund mapping; supplied conflicting metadata is
still rejected. This can require a mapping for pending rows with blank folios.

Order identity is the exact exchange order ID within owner/account. It is required
and must be unique within a file. A SHA-256 digest provides a bounded index and
lookups also compare the original string. This is the implemented matching
contract, not a claim that every possible broker export guarantees that identity.
Existing manual orders or potentially matching manual transactions require
reconciliation; the importer never guesses their associations.

Identical snapshots are unchanged. Only `PROCESSING` to `COMPLETE` can change an
existing source order. The order ID stays the same; amount, units, NAV and source
metadata are updated from the completion snapshot. Client, security/name/plan,
direction, trade date/time and an already supplied folio may not change. Completed
orders, backward transitions and conflicting snapshots are rejected. Original
and completion snapshots both remain in the audit tables.

Period bounds are explicitly supplied, inclusive dates. Every source trade date
must be within them. PostgreSQL excludes overlaps at owner/fund level, including
reserved and failed imports. A later file may reuse an **exact, loaded period**
only for already known unchanged or completing orders; the file must contain at
least one actual completion. This permits unchanged funds alongside another
fund's completion. New orders in an already covered fund/period and partial
overlaps are rejected. An exact byte replay returns the existing result without
creating a new job. Adjacent non-overlapping periods and other owners/funds are
independent.

## Explicit posting and date policies

No date convention or transaction date is inferred. Every launch must specify:

| Argument | Accepted value and meaning |
|---|---|
| `--date-format` | `dd/MM/uuuu`, `MM/dd/uuuu`, or `uuuu-MM-dd`; strict calendar parsing |
| `--posting-policy=ORDER_ONLY` | Preserve/upsert orders and all raw metadata; portfolio transactions/totals remain unchanged |
| `--posting-policy=TRADE_DATE_MIDNIGHT` | A COMPLETE BUY/SELL with positive amount/units/NAV creates a linked transaction at trade-date midnight |
| `--posting-policy=TRADE_DATE_ORDER_TIME` | Same posting, at the explicitly accepted combination of trade date and source order time |

Times are parsed as `hh:mm AM/PM` and stored without timezone conversion. The
posting options are operator-selected business policy, not evidence of an
execution/allotment timestamp. Unknown statuses/directions remain raw order facts;
they cannot trigger financial posting. PROCESSING rows never post. COMPLETE
posting and linking are atomic, including a later completion update. A completion
already imported under ORDER_ONLY is not subsequently posted merely by replaying
it with a different policy; that requires a separate reconciliation workflow.

## Build and launch

```bash
mvn -o verify -pl trade-batch -am
java -jar trade-batch/target/trade-batch-0.0.1-SNAPSHOT-coin.jar \
  --mode=VALIDATE \
  --input-file=/absolute/path/export.csv \
  --owner-id=1 --account-id=1 \
  --period-start=2026-10-01 --period-end=2026-10-31 \
  --date-format=dd/MM/uuuu --posting-policy=ORDER_ONLY
```

The IDs, period and convention above are examples, not the user's account data or
an approved interpretation of the source dates. Repeat `--input-file` for multiple
files. Optional `--fund-map=1:42,3:57` means data record 1 maps to existing fund 42
and record 3 to fund 57; the header is record 0. Mapping applies to each supplied
file, so use separate launches if their mappings differ.

For DRY_RUN or IMPORT, configure `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` outside
the repository and add `--work-directory=/absolute/private/coin-imports`.
Use mode DRY_RUN first; IMPORT is the mode that writes. The work directory must
be private (0700) and its path must not traverse symlinks. On macOS use canonical
`/private/tmp/...` instead of `/tmp/...`. Managed copies are 0600, grouped by
owner/account and addressed by content hash. Preserve them for restart; original
inputs are never moved or rewritten. `--batch.coin.chunk-size` defaults to 100.
Legacy discovery/truncation flags are rejected.

The `-coin.jar` has its own main class and `coin.properties`. The legacy artifact
retains its original main class. Coin imports only Coin configuration; both
legacy application scans exclude Coin. Flyway, SQL initialization, Batch schema
initialization and automatic job launch are disabled in Coin configuration.

## Database prerequisite and recovery

Apply [V14](../trade-repository/src/main/resources/db/migration/V14__coin_order_history_import.sql)
only through a separately reviewed schema deployment. It adds `btree_gist` and
five additive tables: `coin_import_file`, `coin_import_period`,
`coin_import_file_fund`, `coin_import_row` and `coin_order_identity`.
The fund join records completion files reusing an earlier reservation; the identity
table connects source snapshots to the stable order without putting pending
metadata in portfolio transactions. It changes no existing business table.

The importer requires the V12 broker uniqueness constraint, V13-shaped portfolio
schema and durable Batch JDBC tables. The dated live catalog checks found missing
V12 enforcement and baseline-only Flyway histories. Do not run an unreconciled
V1–V14 upgrade against those adopted databases; migration history, extension
privileges and schema readiness need deployment review. No live prerequisites
were changed or freshly inspected during this implementation.

After a normal FAILED execution, fix the external cause and rerun IMPORT with the
same bytes, owner/account and policy/period/mapping. Stable identifying parameters
reuse the Batch job instance; completed chunks/steps are not duplicated. Changing
the saved contract is rejected. Failed periods stay BLOCKED until the same import
completes. No release, force reload, transaction correction or reversal command is
provided. A killed process left STARTED/UNKNOWN needs reviewed Batch recovery;
the launcher does not silently abandon a potentially running execution.

Ordinary errors report codes, not raw source cells. Writer failures remove SQL
exception causes before Spring Batch logs or persists them because PostgreSQL
constraint errors can contain complete source rows. Managed source/audit storage
still contains the full original data and needs normal access/retention controls.

## Verification

Tests use synthetic fixtures and disposable PostgreSQL 16 containers, never
application database URLs. [CoinImportIntegrationTest](../trade-batch/src/test/java/com/trading/coin/CoinImportIntegrationTest.java)
exercises the actual V14 Flyway upgrade/revalidation from a prepared V13 portfolio
schema, JDBC Batch execution, source fidelity, owner isolation, period exclusion,
concurrency, multi-fund reservation rollback, completion/posting/replay, staging
and projection rollback, changed-policy/source rejection, standalone modes and
restart in a new JVM. Parser, validator and component suites cover syntax,
all-record diagnostics, precision, files, CLI, checkpoints and legacy scan isolation.

See [verification history](project-understanding/VERIFICATION.md#coin-batch-implementation--8-october-2026)
for executed results. The original standalone tests do not prove a fresh V1–V14
install (V1/V4 have unrelated vector prerequisites), live master matching, deployed
ingestion, upload integration or recovery after an ungraceful JVM kill. The
9 October upload integration described below invokes this same job. Its
verification is recorded separately from the original standalone tests.


## REST and UI integration — 9 October 2026

The existing `POST /api/mutual-fund-txns/zerodha-upload` accepts multipart `file`
and an optional **application/json** part named `options`:

```json
{
  "brokerAccountId": 1,
  "periodStart": "2026-10-01",
  "periodEnd": "2026-10-31",
  "dateFormat": "dd/MM/uuuu",
  "postingPolicy": "ORDER_ONLY",
  "fundOverrides": {"2": 42}
}
```

These are synthetic example IDs. The owner comes exclusively from the authenticated
session; the request cannot choose an owner, input path, job name or truncation mode.
`fundOverrides` is optional; its keys are 1-based data record numbers and its values
are existing owned fund IDs. Other required options have the same meanings as the
CLI above. Session authentication and CSRF are required, including for multipart.

With options, the endpoint synchronously validates and executes the actual Coin
job and responds **200** only after completion. It returns the existing receipt
fields (`uploadId`, `originalFilename`, `size`, `status`) plus `importResult`:

```json
{
  "uploadId": "a416227c-80a1-4eac-b5a4-99a2645a4d7e",
  "originalFilename": "example.csv",
  "size": 450,
  "status": "COMPLETED",
  "importResult": {
    "importId": 1, "status": "COMPLETED", "records": 2,
    "inserted": 1, "updated": 1, "unchanged": 0, "posted": 1
  }
}
```

Counts describe the persisted import, so an identical replay returns the original
import ID/counts without writing its rows again. `uploadId` identifies this receipt;
there is no asynchronous queue or polling endpoint. Requests without `options`
retain the earlier **202/UPLOADED** filesystem-only behavior, including non-Coin
staging compatibility. A legacy staged file is not automatically imported later.

| HTTP status | Meaning |
|---|---|
| 200 | Coin job completed, including a successful replay |
| 202 | Legacy file-only upload staged; no job run |
| 400 | Missing/invalid options, file, owned account or required multipart part |
| 401 / 403 | Login required / missing or expired CSRF |
| 409 | Covered period, conflicting order/replay contract or already-running instance |
| 413 / 415 | Upload exceeds size limit / invalid multipart or options content type |
| 422 | CSV/matching validation failed; `fieldErrors` uses keys such as `record[2].fund` and safe error codes |
| 429 | Another web import is running in this REST process; retry shortly |
| 503 | Database/runtime/schema prerequisites unavailable |
| 500 | Import failed after validation; some chunks may have committed; retry identical bytes/options after fixing the cause |

Import admission is limited to one request per REST process, with database locks,
file identity and period constraints protecting multiple processes/CLI imports.
The HTTP request waits for completion. A client/proxy timeout is not evidence of
rollback; retry identical bytes/options to obtain the persisted result or resume a
failed instance. Abrupt process death still needs the Batch recovery described above.

The Transactions page retains its primary upload action. Selecting a file opens
an import form for account, complete export period, source date convention and
posting policy. Orders-only is visibly selected initially; posting requires a
transaction option. Optional record/fund selectors handle missing folios or other
matching ambiguity. Selectors fetch every page of owned accounts/funds. The form
shows validation diagnostics and completion counts, prevents repeat submission,
and refreshes transaction rows and totals after an attempt so partial committed
progress is also reflected. File contents, paths and SQL errors are not logged.

REST configures `COIN_IMPORT_WORK_DIRECTORY` (default
`${user.home}/.trade/coin-imports`), a private canonical directory retained for
restart. It uses REST's existing datasource and upload size limit (10 MiB default,
plus the multipart request limit). Coin validates before writing managed copies.
The legacy `ZERODHA_TRANSACTIONS_UPLOAD_DIR` remains separate. Neither context
initializes or migrates Coin tables automatically; V12/V14/history prerequisites
above still apply before live use.

The necessary integration dependency is `trade-rest` → `trade-batch:coin-library`.
This small ordinary JAR contains Coin classes only, excluding both the standalone
Coin main and all legacy trade/ledger runners. It is attached at `process-classes`
so reactor `test` builds work. REST depends on Spring Batch core, excluding the
Batch Boot starter so Batch auto-configuration is not installed in its root context.
`CoinBatchConfiguration` is now explicitly registered/imported, not component
scanned. `CoinImportRuntime` opens a private context per import and borrows REST's
pool without closing it. Persistence remains in `trade-repository`; CLI artifacts
and the legacy default batch artifact retain their entry points.

Evidence: [upload service](../trade-rest/src/main/java/com/trading/upload/ZerodhaTransactionUploadService.java),
[runtime bridge](../trade-batch/src/main/java/com/trading/coin/CoinImportRuntime.java),
[HTTP/PostgreSQL tests](../trade-rest/src/test/java/com/trading/upload/CoinUploadPostgresIntegrationTest.java),
[upload form](../trade-ui/src/components/CoinUploadForm.jsx),
and [dated verification](project-understanding/VERIFICATION.md#coin-upload-integration--9-october-2026).
