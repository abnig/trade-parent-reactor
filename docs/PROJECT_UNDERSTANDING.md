# Project understanding

Current source baseline: **`719606a1f836a3bbba2c3071a509c98048b2a1e8`**, branch
**`feature/coin-batch-zerodha-upload`**, reconciled **16 November 2026** from a clean
checkout. This document is the handoff index; each linked reference owns its topic.

## Current state

The application implements session-authenticated mutual-fund CRUD, profile and
recovery, portfolio/fund analytics, legacy trade/ledger CSV imports, and MCP read
tools. The Coin importer is an unfinished rewrite. The upload controller returns
`null`, the field mapper returns an empty record, writers only print, and no Coin
job/step is wired. Existing UI import controls still expect the removed backend
contract. See [Coin status](COIN_BATCH_RUNBOOK.md).

REST uses four owner-scoped primary repository beans and explicit owner IDs for
analytics. Legacy trade/ledger and MCP data remain global. Orders and Coin audit
tables remain in SQL migrations even though their Java import/order repositories
were removed. Module boundaries are summarized in [Architecture](../ARCHITECTURE.md).

## Reading map

| Topic | Canonical reference |
|---|---|
| Setup, build and local run | [README](../README.md) |
| Dependencies and runtime boundaries | [Architecture](../ARCHITECTURE.md) |
| Backend classes and responsibilities | [Components](project-understanding/COMPONENTS.md) |
| HTTP routes, payloads and UI integration | [Contracts and UI](project-understanding/CONTRACTS_AND_UI.md) |
| Auth, recovery, financial calculations and MCP | [Workflows](project-understanding/WORKFLOWS.md) |
| Migration ledger, column dictionary and historical catalog | [Data model](project-understanding/DATA_MODEL.md) |
| Legacy CSV inputs, configuration and execution | [Batch architecture](../trade-batch/ARCHITECTURE.md) |
| Coin scaffolding and remaining decisions | [Coin status](COIN_BATCH_RUNBOOK.md), [requirements](COIN_ORDER_HISTORY_BATCH_REQUIREMENTS.md) |
| Production process layout and installation | [Deployment](../DEPLOYMENT_ARCHITECTURE.md) |
| Executed checks and limits | [Verification](project-understanding/VERIFICATION.md) |
| Decisions and investigation history | [Learning log](project-understanding/LEARNING_LOG.md) |

## Evidence rules

- **C — source:** verified from code/configuration at the stated commit.
- **T — tests:** executed checks, with date and scope; test presence alone is not a pass.
- **R — runtime:** an observed process, artifact or database snapshot at a stated date.
- **I — inference / U — unresolved:** consequences not reproduced or questions still open.

Current descriptions are C unless stated otherwise. The original 6–7 October
investigation used `70b9f2c`; its [catalog snapshot](project-understanding/SCHEMA_SNAPSHOT.json)
and [test evidence](project-understanding/VERIFICATION_RESULTS.json) retain that
date. They do not establish the current deployed schema or service behavior.
The 16 November pre-publication Maven run passed 206 tests; this documentation
reconciliation did not rerun application suites.

## Known gaps and change-impact guide

| Area | Gap / what a future change must verify |
|---|---|
| Coin | Finish mapping, job/step wiring, persistence and ownership; define replay/lifecycle policy and restore integration tests before describing imports as usable |
| Schema deployment | Reconcile actual schema/Flyway history; October snapshots lacked V12 uniqueness and showed baseline 1 only; no current live check |
| Batch | Both legacy runners are discoverable; per-file deletion can race; no source deduplication/archive/reject files; durable metadata wiring remains unverified |
| MCP | Incorrect packaged main class; misspelled `veersion` property; transport/discovery and global access need verification |
| UI | No recovery-enrollment screen, broker selector limited to first 100, latest-value overview uses one request per fund; no browser automation |
| Portfolio | No duplicate-submission protection, optimistic locking or common CRUD transaction; status text never excludes transactions from totals |
| Security | Externalize fallback credentials; no admin/audit workflow or login throttling; production TLS/cookie/email-origin settings need deployment review |
| Analytics | Recorded snapshots only; unsupported return metrics are unavailable, not inferred; no benchmark or synchronized portfolio advanced-return history |

Before changing schema, check [migration guidance](../trade-repository/src/main/resources/db/migration/README.md).
For REST changes, inspect controllers, DTOs, owned repositories, API helpers and
corresponding H2/PostgreSQL tests. For analytics, inspect SQL and client calculation
helpers together. For batch/MCP, do not launch import runners merely to check wiring.

## Maintaining this handoff

Follow [AGENTS.md](../AGENTS.md): verify checkout and source assumptions, correct the
canonical topic document, and add one concise dated learning entry for material
new evidence. Keep historical test/schema dates explicit. Never retain secrets or
private records, and do not infer permission for deployments or database changes.
