# Verification evidence and limits

Current source checkpoint: `719606a`, branch `feature/coin-batch-zerodha-upload`.
Results below are dated execution records, not assertions about a running deployment.
The former Coin importer/tests were removed; historical success does not validate
the unfinished replacement. Full earlier narratives remain in Git history.

## Build failure repair — 16 November 2026

Before publishing `719606a`, `mvn -o verify -Pdev` passed all seven reactor projects:
**206 tests (1 batch + 205 REST), zero failures/errors/skips**, plus packaging and
coverage-report generation. It ran against `08099d7` plus the changes committed as
`719606a`. Log: `/private/tmp/trade-pre-push-20261116-authorized.log` (local, ephemeral).

The initial sandbox run failed on Docker socket access and Mockito agent attachment;
the authorized rerun passed. PostgreSQL tests used disposable containers, not the
configured application databases. UI was unchanged and its checks were not rerun.
No coverage percentage is claimed, and this was not a clean-build verification.

The current passing suite includes:

- H2 and PostgreSQL ownership, profile, recovery and portfolio analytics checks;
  `PostgresSessionAndOwnershipTest` repeats all 16 ownership cases on PostgreSQL.
- Controller/DTO/reference/pagination behavior, registration and SMTP mock tests.
- Selected V10/V11/V12 migration conflict/rollback checks.
- `RestWorkspaceStartupTest` for current REST/owned beans and exclusion of batch
  infrastructure from the full workspace output.
- One `CoinComponentTest` scan-isolation test. It does not execute a Coin import;
  remaining parser/validator test shells do not establish behavior.

Publication excluded all `target` paths, and the committed tree contained none.
Existing source whitespace warnings were preserved. No database migration,
application import, deployment or service restart occurred.

## Historical results

These runs apply to the source/worktree stated, including then-uncommitted changes.
They must not be relabeled as current coverage.

| Date / baseline | Executed checks and result | Scope and limits |
|---|---|---|
| 6 October / `70b9f2c` | `mvn -o verify -Pdev`: 211 tests; UI 114 tests; Vite build | All passed after sandbox reruns. Non-clean coverage could contain accumulated data. [Stored evidence](VERIFICATION_RESULTS.json). |
| 6–7 October / `70b9f2c` | Plist/shell syntax; artifact manifests; read-only catalogs; synthetic fast-lane SQL probe | No project HTTP listeners observed then. MCP packaged main absent. [Schema snapshot](SCHEMA_SNAPSHOT.json) is dated, not current runtime evidence. |
| 8 October / `70b9f2c` + original Coin implementation | `mvn -o clean verify -Pdev`: 258 tests, including 47 Coin | Disposable PostgreSQL, lifecycle/replay/rollback and new-JVM restart. Source-identical isolated rerun avoided shared-workspace class rewriting; no aggregate coverage percentage. No frontend rerun. |
| 9 October / `70b9f2c` + upload integration | Isolated clean Maven: 270 tests (47 batch + 223 REST); focused upload/controller 36; UI 119; Vite build | Actual authenticated multipart HTTP into disposable PostgreSQL; pool/context isolation, old file-only/options/replay contract. Source/POM hashes matched the workspace. Removed implementation now. |
| 9 October / `4f2dc67` + scan fix | `mvn -o verify -pl trade-rest -am`: 272 tests (47 batch + 225 REST) and packaging | Workspace scan collision reproduced and fixed. UI/MCP excluded from this scoped run; original pasted exception lacked its deepest cause. |
| 13 October / `08099d7` + table-removal changes | `mvn -o clean verify -Pdev`: 274 tests (49 batch + 225 REST); UI 119; Vite build; diff whitespace check | V15 populated upgrade/rollback, overlap acceptance, import/replay and restart then tested. Those Coin suites were later removed. No full fresh V1–V15 install or live migration. |
| 13 October / `08099d7` + POJO addition and surrounding rewrite | `mvn -o verify -pl trade-model -am`: compiled/packaged | Model had no tests; this did not verify the surrounding rewrite. |

Every successful test run above reported zero failures/errors/skips. The October
backend/frontend sandbox failures were followed by approved successful runs; failures
were not counted as passes. Historical logs included `/tmp/coin-full-reactor-clean-verify.log`,
`/tmp/coin-batch-isolated-verify.log`, `/tmp/coin-upload-full-verify.log`,
`/tmp/trade-rest-startup-final-verify.log` and `/private/tmp/coin-removal-full.log`.
Their continued filesystem availability is not assumed.

The Eclipse investigation found the Coin classifier resolving to the full batch
workspace, registering legacy/Coin configurations together. Explicit REST scan
exclusions fixed the reproduced collision. A later reflection probe succeeded;
it did not prove the cause of the original incomplete stack trace or live startup.

## Documentation reconciliation — 16 November 2026

Starting from clean `719606a`, the Markdown was reconciled with source, POMs,
configuration, migrations and test declarations. Only 19 Markdown files changed;
AGENTS.md and the JSON evidence snapshots were preserved. The full 20-file Markdown
set is about 73% shorter by word count. Checks passed for all 324 local links and
anchors, code-fence/table structure, stale-reference review and `git diff --check`.
Application code/configuration was unchanged and application suites were not rerun.
The earlier 206-test run above remains the latest executed backend result.

## Remaining verification gaps

- Coin parser, job/step, writers, uploads, identity/replay/lifecycle and V15 upgrade
  tests in the **current** tree; historical tests were removed.
- Fresh V1–V15 installation, actual deployment Flyway history/schema/extensions,
  owner backfill and live uniqueness. Do not use application databases as test fixtures.
- Live REST/browser cookie/CSRF flows, SMTP delivery and reset-link origin.
- Legacy batch execution, durable metadata, skips/restarts and multi-file deletion
  safety; scan tests do not run import jobs.
- Corrected MCP packaged startup, tool/resource discovery, generated schemas and
  Java mapper behavior through the transport.
- Browser event/layout/accessibility automation. UI tests use helper tests, mocked
  fetch, SSR and temporary loopback proxy servers; they are not browser E2E checks.

[PostgresTestDatabase](../../trade-rest/src/test/java/com/trading/support/PostgresTestDatabase.java)
creates fixed-purpose disposable PostgreSQL 16 containers; startup failures fail
tests. Recovery mail is mocked, and H2 contexts use in-memory schemas. Build/report
commands are maintained in [README](../../README.md#build-and-test).
