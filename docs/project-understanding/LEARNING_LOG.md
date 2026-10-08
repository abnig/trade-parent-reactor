# Project learning log

Use this log for material discoveries and decisions from work on this application.
Maintain the corresponding sections of [Project understanding](../PROJECT_UNDERSTANDING.md)
and its appendices so future sessions can find the current explanation directly.
The retention procedure is in [AGENTS.md](../../AGENTS.md#retaining-project-knowledge).

Each new entry should include its date, branch/commit and relevant local changes;
the discovery or user decision; links to source symbols or report sections;
verification performed and its limits; and any unresolved follow-up. Use the
report's code/test/runtime/inferred/unresolved evidence labels for technical claims.
Do not copy credentials, tokens, private records or personal financial data here.

## 2026-10-07 — Investigation handoff and standing retention request

- **Baseline:** `main`, commit `70b9f2c2e8621ed396019c6378c1a61b019cf701`.
  The investigation added `docs/`; this follow-up also updates `AGENTS.md`.
  Application code and configuration remain unchanged.
- **User decision:** retain learning every time we work on this app. Future tasks
  must consult the report, correct affected descriptions, and record material new
  learning, decisions, verification and unresolved gaps before finishing.
- **Handoff:** the [report](../PROJECT_UNDERSTANDING.md) and its appendices retain
  the completed module, data model, backend, UI, workflow, batch, MCP and deployment
  investigation. [Verification](VERIFICATION.md) records the dated checks and
  infrastructure limitations; those results are historical, not a new test run.
- **Scope of this follow-up:** documentation and persistent memory only; no
  application, dependency, database, migration, deployment or service changes.


## 2026-10-07 — Coin CSV field mapping and batch requirement specification

- **Baseline:** main, commit `70b9f2c2e8621ed396019c6378c1a61b019cf701`;
  pre-existing modified AGENTS.md and untracked docs preserved.
- **User request:** create a reusable field-mapping prompt and a Markdown
  requirement specification for a future import in trade-batch. This request
  does not authorize implementing or running that importer.
- **Deliverables:** [prompt](../COIN_ORDER_HISTORY_ANALYSIS_PROMPT.md) and
  [proposed specification](../COIN_ORDER_HISTORY_BATCH_REQUIREMENTS.md).
- **C:** current migrations, models and JDBC confirm the V11 pending-order
  metadata storage gap and status-independent transaction portfolio inclusion.
  The spec requires complete source preservation and separates ingestion from
  financial posting. It proposes staging, owner-scoped matching, replay/overlap
  handling, runner isolation and acceptance tests.
- **U:** lifecycle/posting dates, stable source order identity, master creation,
  deferred-row resolution, schema deployment and upload integration remain
  explicit decisions. Proposed defaults are not user approvals.
- **Verification:** read-only CSV/source analysis and documentation checks;
  no database access, migrations, application tests, batch runs or service
  changes. Prior runtime/test results were not reclassified as current evidence.
- No source account/order identifiers or personal financial records are retained
  in this learning entry.

## 2026-10-07 — Coin specification refreshed against CSV, catalogs and Batch APIs

- **Baseline:** main at `70b9f2c2e8621ed396019c6378c1a61b019cf701`;
  pre-existing modified AGENTS.md and untracked docs preserved. Documentation only.
- **Deliverable:** updated [requirements](../COIN_ORDER_HISTORY_BATCH_REQUIREMENTS.md)
  with full field profiles/mapping, concrete two-table audit proposal, exact
  ownership/lifecycle/overlap policies, batch infrastructure/runbook and tests.
  Earlier defaults remain proposals; no importer or database import approved.
- **CSV:** 16 expected headers/9 rows; unquoted JSON-shaped tags with literal
  quotes, no doubled CSV escapes/quoted commas/multiline records observed.
  Date convention cannot be proven from this sample. Private values were not
  retained; the spec records aggregate profiles and checksum only.
- **R:** fresh read-only catalog check in both configured databases confirms
  V10/V11/V13 layout and same-fund FK; V12 uniqueness absent and baseline1-only
  history. No business records, private-data counts or sequence values queried.
- **C:** dependency:tree resolved Batch6.0.3/JDBC7.0.7. Batch6's default
  EnableBatchProcessing is resourceless; explicit JDBC infrastructure is required
  for proposed durable Coin restart. Current runner wiring remains unexecuted.
- **P/U:** business file identity excludes parser version; identical row content
  cannot alone prove duplicate orders. Missing folios/date convention, source
  identity, posting/reversals and audited resolution remain policy decisions.
- **Verification:** read-only CSV/source/catalog/library analysis and document
  coverage/link/whitespace checks; no application tests/build/startup, migrations,
  records imported, services/deployments, commits or pushes.

## 2026-10-08 — Coin requirements handoff completed

- **Baseline:** `main`, commit `70b9f2c2e8621ed396019c6378c1a61b019cf701`;
  pre-existing modified `AGENTS.md` and untracked `docs/` preserved.
- **Scope recovered:** the previous request was analysis/documentation only.
  Resume completes the [requirements](../COIN_ORDER_HISTORY_BATCH_REQUIREMENTS.md)
  handoff; it does not approve its proposed business policies or implementation.
- **C/I:** [TradeBatchApplication](../../trade-batch/src/main/java/com/trading/TradeBatchApplication.java)
  and [LedgerBalancesBatchApplication](../../trade-batch/src/main/java/com/trading/LedgerBalancesBatchApplication.java)
  both use default component scanning from `com.trading`. New annotated Coin
  components could therefore enter legacy contexts. Narrowing only the proposed
  Coin scan or selecting its packaged main is insufficient. Requirements and
  planned tests now cover isolation in both directions; this consequence is
  inferred from source, not runtime-verified.
- **Verification:** source SHA-256 still matches the specification; checked all
  16 profile/mapping rows, FR-01–20 and local links; re-read affected migrations,
  JDBC storage/portfolio queries, upload staging and runner source. Documentation
  checks only; no build/application tests, DB connection, migration, import or
  service operation. Prior catalog/test results retain their original dates.
- **U:** posting/date/identity/master-resolution policy and launcher architecture
  remain review decisions in section 10. No application code changed.

## 2026-10-08 — User and mutual-fund import-period tracking

- **Baseline:** `main` at `70b9f2c2e8621ed396019c6378c1a61b019cf701`;
  pre-existing modified `AGENTS.md` and untracked `docs/` preserved.
- **User decision:** require a date-tracking table at user and mutual-fund level
  to prevent another file loading the same period. Added the
  [period table/contract](../COIN_ORDER_HISTORY_BATCH_REQUIREMENTS.md#user-and-mutual-fund-period-tracking),
  FR-21–23, job parameters, reservation/recovery rules and planned tests; updated
  the reusable prompt, report, data-model and workflow appendices.
- **P/U:** any-overlap rejection and explicitly supplied export bounds are
  proposed defaults; clarification requested, not recorded as user approval.
  Multiple funds reserve atomically before staging; failed/partial imports retain
  coverage and resume using the same claim. This changes the proposed treatment
  of overlapping files and unresolvable fund/date coverage; corrected same-period
  exports need audited reconciliation.
- **C:** current fund ownership/parent updates were checked in
  [OwnedFundRepository](../../trade-repository/src/main/java/com/trading/repository/impl/OwnedFundRepository.java).
  V1 does not declare btree_gist; its installation is a future prerequisite of
  the proposed GiST exclusion constraint. Official PostgreSQL documentation was
  reviewed; neither current extension availability nor the constraint was tested.
- **Verification scope:** documentation field/requirement/link consistency and
  source inspection only. No application code/migration created, DB connection,
  import, service start or application test. Retained historical checks keep their
  original date/scope; the new table is specified, not live.


## 2026-10-08 — Coin implementation completed and PostgreSQL/runtime tests added

- **Baseline:** `main`, `70b9f2c2e8621ed396019c6378c1a61b019cf701`. Resumed existing
  uncommitted Coin source/model/repository/V14/POM/launcher changes and preserved
  pre-existing AGENTS.md/docs. No commit or push.
- **Recovered user decision:** implement the Spring Batch job, test its components,
  validate all input records and upsert PROCESSING to COMPLETE. This supersedes
  the earlier documentation-only scope and blanket same-period correction block.
  The [runbook](../COIN_BATCH_RUNBOOK.md) is the current contract; original source
  analysis remains in the [requirements](../COIN_ORDER_HISTORY_BATCH_REQUIREMENTS.md).
- **C/T:** isolated Coin launcher, explicit JDBC Batch infrastructure, immutable
  source staging, owner/account/fund checks, five V14 audit/identity/period tables,
  atomic reservations, completion upserts and optional explicit-date transaction
  posting. Exact replay is a no-op; failed coverage persists; restart in a new JVM
  resumes committed chunks. Known unchanged funds can accompany another fund's
  completion. Date convention/posting policy are explicit parameters, not inferred
  approvals. No automatic masters or manual-record association.
- **Fixes:** Batch6 exposes a proxied JobOperator, so concrete-class lookup failed;
  corrected it. Added all-files DB preflight, read-only dry run and sanitized writer
  failures because PostgreSQL errors can include raw rows. Updated
  [components](COMPONENTS.md#coin-components--8-october-2026),
  [workflow](WORKFLOWS.md#coin-import-workflow--implemented-8-october-2026) and
  [data model](DATA_MODEL.md#coin-v14-implementation--8-october-2026).
- **T:** clean full reactor passed **258 tests, zero failures/errors/skips**.
  Coin adds47 (17 PostgreSQL integration cases plus30 parser/validator/component
  cases). Verified V14 upgrade, database overlap error/adjacent periods, concurrent
  claims, ownership, source fidelity, lifecycle/posting, failure rollback, fresh-JVM
  restart, standalone dry run/import, legacy scan exclusion and packaged validation.
- **Build finding:** target class files changed after packaging in the shared
  checkout; background compilation is inferred, process unconfirmed. Final
  source-hash-matched isolated Coin verification passed47 tests without class-data
  mismatch warnings. Shared aggregate coverage has that limitation; no coverage
  percentage is claimed. Exact commands/evidence in [Verification](VERIFICATION.md#coin-batch-implementation--8-october-2026).
- **U/boundary:** V14 written/tested only in disposable PostgreSQL; application DBs
  were not accessed or migrated. Dated missing V12/live history reconciliation
  remains deployment work. Upload endpoint still only stages files. No frontend
  change, live import, service start/restart/deployment or private records in notes.

## 2026-10-09 — Connect the Coin job to Zerodha uploads

- **Baseline/user request:** `main` at `70b9f2c2e8621ed396019c6378c1a61b019cf701`,
  existing uncommitted Coin implementation and AGENTS.md/docs preserved. User
  requested integrating the newly created job with `/zerodha-upload` and resuming
  the work; no commit, deployment or live migration was requested/performed.
- **C/T contract:** `POST /api/mutual-fund-txns/zerodha-upload` with multipart file
  plus JSON options runs the job synchronously and returns200 only on completion.
  Session ownership is authoritative. File-only requests retain202 staging.
  Explicit account/period/date/posting/matching choices are collected in the UI;
  safe record errors and persisted import counts are returned. Identical replay
  returns the original import result, without duplicate writes. See the
  [runbook](../COIN_BATCH_RUNBOOK.md#rest-and-ui-integration--9-october-2026) and
  [contracts](CONTRACTS_AND_UI.md#coin-upload-contract--9-october-2026).
- **C/T integration isolation:** the required REST→batch dependency is a small
  `coin-library` classifier attached at process-classes; it excludes standalone
  mains/legacy runners. REST includes Batch core without Boot Batch auto-config.
  [CoinImportRuntime](../../trade-batch/src/main/java/com/trading/coin/CoinImportRuntime.java)
  explicitly creates/closes a private context and registers the borrowed pool as
  a singleton so closing it does not close REST's datasource. Coin configuration
  is explicitly registered/imported, no longer component-scanned. Persistence
  stays in `trade-repository`; no new schema changes were needed.
- **C/T operational behavior:** bounded file reads and one web import per process;
  existing database identity/period locks protect CLI and other processes. HTTP
  timeouts do not imply rollback; retry identical bytes/options. Import errors
  retain safe codes without SQL/source exception causes. Failed chunks can leave
  earlier committed work, so the UI refreshes totals after attempts and the error
  explains same-file restart.
- **Fixes:** Batch6 running-execution exception is in `core.launch`; Spring7
  multipart tests use `AbstractMockHttpServletRequestBuilder`. The final build
  used an isolated source copy to avoid the previously demonstrated workspace
  class rewriting; all197 manifest entries matched workspace hashes afterward.
- **T:** full clean backend **270/270**, focused upload/controller **36/36**, UI
  **119/119**, Vite build passed. Real HTTP upload against disposable PostgreSQL
  persisted the order; tests cover CSRF/ownership, source fidelity, lifecycle,
  replay/conflicts, validation, partial failure/restart and pool/context isolation.
  Packaging retained both executable entry points. Details and limitations are
  in [Verification](VERIFICATION.md#coin-upload-integration--9-october-2026).
- **U/boundary:** no live DB/runtime verification or migration; V12/history/V14
  readiness is still a deployment prerequisite. UI browser interaction/layout,
  fresh schema installation and ungraceful-kill recovery remain unverified.

## 2026-10-09 — Prepare the feature branch for publication

- **User decision:** explicitly requested a new feature branch containing the
  current changes, followed by a commit and push to the existing remote.
- **Git scope:** created `feature/coin-batch-zerodha-upload` from `main` at
  `70b9f2c2e8621ed396019c6378c1a61b019cf701`. The commit includes the Coin batch,
  V14 migration, REST/UI integration, tests, project-understanding documentation
  and existing AGENTS.md retention instructions. Remote is `origin` for the same
  repository; no history rewrite or application deployment is part of this request.
- **Verification:** backend source/POM hashes still match the isolated successful
  build documented in [Verification](VERIFICATION.md#coin-upload-integration--9-october-2026).
  No application source changed during branch preparation, so tests were not
  rerun. The preceding results remain270 backend tests,119 UI tests and a passing
  UI build, with their documented scope and limitations. This entry records
  publication authorization and preparation; the Git commit/remote refs provide
  the resulting publication evidence.
