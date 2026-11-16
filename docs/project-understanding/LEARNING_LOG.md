# Project learning log

Record material decisions and findings with date, checkout, evidence and unresolved
gaps. Correct the canonical topic reference as well as adding an entry. Follow
[AGENTS.md](../../AGENTS.md#retaining-project-knowledge); never retain credentials or
private records. Entries below summarize history; detailed prior versions remain in
Git. Current behavior is indexed by [Project understanding](../PROJECT_UNDERSTANDING.md).

## 2026-10-07 — Investigation and retention request

Baseline `main` at `70b9f2c`; documentation and AGENTS changes only. The user asked
that learning be retained every time the app is worked on. The investigation mapped
modules, SQL, APIs, UI, workflows and runtime configuration. Dated test and catalog
evidence is preserved in [Verification](VERIFICATION.md) and [Data model](DATA_MODEL.md).
No application or database change was made.

## 2026-10-07 — Coin analysis and catalog refresh

At `70b9f2c` plus pending documentation, the user requested a reusable analysis
prompt and batch requirements, not an implementation. The 16-field sample exposed
unquoted JSON-shaped tags and an ambiguous date convention; private values were
not retained. Proposed staging, matching, posting and identity defaults were not
approvals. Read-only catalogs showed V10/V11/V13-era layout, missing V12 uniqueness
and baseline-1-only history. Offline dependency inspection found Batch 6.0.3 and
JDBC 7.0.7, with a resourceless default requiring explicit JDBC restart infrastructure.
No tests/import/migration ran for this analysis. Current [mapping](../../coin-order-schema.md)
and [requirements](../COIN_ORDER_HISTORY_BATCH_REQUIREMENTS.md) supersede the long proposal.

## 2026-10-08 — Handoff and period-tracking requirement

Baseline remained `70b9f2c` plus documentation. Source review identified the need to
isolate Coin from legacy scanning in both directions; a packaged-main choice alone
was insufficient. The user then requested user/fund date tracking to prevent repeat
period loads. Any-overlap rejection and explicit bounds were proposed defaults,
not recorded approvals. This decision was later superseded by the 13 October
request to remove row/period tracking. No database change accompanied the proposal.

## 2026-10-08 — Original Coin implementation

At `70b9f2c` plus uncommitted work, the user requested the job, all-record validation,
component tests and PROCESSING-to-COMPLETE upserts. The original implementation
added V14, owner matching, source preservation, period reservations and explicit
posting/restart policies. Disposable-PostgreSQL and new-JVM checks passed; shared
class-output rewriting required an isolated source-identical verification copy.
See [historical results](VERIFICATION.md#historical-results). Live migration/master
matching and abrupt-kill recovery remained unverified. This importer was later removed.

## 2026-10-09 — Upload integration and publication

At `70b9f2c` plus the existing Coin work, the user requested REST/UI integration.
The then-implemented route returned 200 on completed options-based imports and 202
for file-only staging. A private context borrowed REST's datasource; a Coin-only
classifier and Batch core avoided legacy runners/Boot Batch auto-configuration.
Real HTTP/PostgreSQL, backend and UI checks passed with their documented limits.
The user subsequently authorized branch creation, commit and push to
`feature/coin-batch-zerodha-upload`. No live migration/deployment occurred.
The upload implementation is absent from the current checkpoint.

## 2026-10-09 — IDE workspace startup isolation

Baseline `4f2dc67` on the feature branch. Eclipse resolved the Coin classifier to
full batch workspace output, reproducing a conflicting infrastructure-bean startup
failure. Explicit REST scan exclusions and workspace tests fixed that collision.
The original supplied trace omitted its deepest cause; a later successful reflection
probe did not establish the exact original exception or live Eclipse startup.
[Current startup boundary](COMPONENTS.md#build-repair-and-current-workspace--16-november-2026)
and [historical verification](VERIFICATION.md#historical-results) retain the result.

## 2026-10-13 — Remove row/period tracking and add CoinRecord

Baseline `08099d7` with existing upload-size changes. The user requested removing
`coin_import_file_fund`, `coin_import_period` and `coin_import_row`. V15 preserved
identity snapshots/counters, rejected noncontiguous progress, then dropped the
three tables. The then-existing direct-projection importer accepted overlaps while
retaining identity/replay/lifecycle behavior; tests passed only for that historical
implementation. V14 remained immutable; no live migration was applied.

The user also requested a 16-field POJO; the original addition in `trade-model`
compiled independently while surrounding deletions/rewrite work remained unfinished.
The current class now resides in the batch inbound package. The preserved 1000 MB
upload fallback exceeds the service's 100 MiB guard if the normal 10 MB property
is absent. See [Coin status](../COIN_BATCH_RUNBOOK.md) and [Data model](DATA_MODEL.md).

## 2026-11-16 — Publish rewrite checkpoint and repository repairs

Baseline `08099d7` plus Coin/model/repository/REST changes, V15, tests and docs.
The user explicitly authorized staging, committing and pushing, excluding every
`target` directory. `719606a` was pushed to the existing feature branch; its tree
contains no `target` paths. Fresh Maven verification passed 206 tests after a
sandbox-restricted attempt was rerun with required access. No application behavior
was changed during publication. Source whitespace warnings were preserved.

The checkpoint restores REST owner-scoped beans/pagination and covers owner/ISIN
lookup regressions on H2 and PostgreSQL. Coin upload/job persistence remains
unfinished; historical import results cannot validate it. See [Components](COMPONENTS.md)
and [Verification](VERIFICATION.md#build-failure-repair--16-november-2026).

## 2026-11-16 — Reconcile and shorten Markdown

Started clean at `719606a` on `feature/coin-batch-zerodha-upload`. The user requested
reconciling Markdown with current code and removing repetition. Each topic now has
a primary reference, superseded launch/receipt claims are removed, and historical
test/schema evidence keeps its dates and scope. Contributor instructions and JSON
evidence snapshots were preserved.

**C:** confirmed Coin has no launcher main/job/step, mapper is empty and writers only
print; the draft processor has missing parent-ID and unmatched-account handling.
Legacy sources now live under `com.trading.kite`. Broker `ownerUserId` is a bean
property but its owned row mapper leaves it null; SQL still enforces principal
ownership. `api.js` preserves field errors. Production UI health gating occurs only
before first listen. Canonical references: [Coin status](../COIN_BATCH_RUNBOOK.md),
[Contracts](CONTRACTS_AND_UI.md), [Batch](../../trade-batch/ARCHITECTURE.md),
[Deployment](../../DEPLOYMENT_ARCHITECTURE.md).

Documentation paths/anchors, stale references and whitespace were checked. No
application test rerun, code/configuration change, database access, deployment,
commit or push forms part of this documentation task. Existing 206-test evidence
remains separately dated in [Verification](VERIFICATION.md).

Publication follow-up: the user subsequently authorized staging, committing and
pushing these 19 Markdown changes from `719606a` to the existing feature branch,
excluding every `target` directory. Documentation checks were rerun; application
suites were not rerun for this documentation-only commit. Git refs record the
publication result.
