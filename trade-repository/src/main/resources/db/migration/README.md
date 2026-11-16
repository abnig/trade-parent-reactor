# Flyway migrations

Source baseline: `719606a`, 16 November 2026. This shared module packages **V1–V15**
under `classpath:db/migration`. [Data model](../../../../../../docs/project-understanding/DATA_MODEL.md)
owns the migration ledger, column dictionary and dated catalog evidence.

## Current startup behavior

Flyway is disabled in REST, batch and MCP application configuration. Hibernate DDL
is `none`; batch/MCP SQL and Batch schema initialization are disabled. REST's
configured baseline version is **1**, not 4. The `flyway-baseline` profile enables
baseline-on-migrate but does not itself enable Flyway or select a different version.

A table's presence does not prove that its migrations ran. The read-only 7 October
snapshot found baseline-1-only histories, missing V12 broker uniqueness and other
schema differences. That snapshot is historical and must be refreshed before
choosing a migration strategy; it does not authorize applying scripts manually.

## Upgrade rules

- Keep applied migrations immutable. Add `V<version>__<description>.sql` for new
  changes; do not rewrite checksums or `flyway_schema_history` to conceal drift.
- Reconcile the actual schema/history and extension availability before enabling
  migration. Test the chosen upgrade against a disposable representative database.
- Resolve data conflicts explicitly, with affected writers stopped where needed;
  migrations must not silently merge owners, funds, folios or financial facts.

| Migration | Important precondition or effect |
|---|---|
| V1–V4 | Extensions, Batch/business and AI tables; fresh full installation remains unverified, including vector dimension/index compatibility |
| V5–V6 | Authentication/ROLE_USER and nullable broker ownership; no automatic ownership backfill |
| V7–V8 | Recovery state and separate profile hint; case-insensitive email duplicates block the V8 unique index |
| V9 | Orders, optional fund metadata and six-place transaction units/NAV; no automatic transaction posting |
| V10 | Moves folio to fund; conflicting distinct folios abort |
| V11 | Moves five metadata fields to linked transactions; unlinked metadata/conflicts abort; no transaction invention |
| V12 | Exact `(broker_name, account_id, owner_user_id)` uniqueness; NULL owners remain distinct; existing duplicates abort |
| V13 | Optional ISIN becomes TEXT; `N/A` allowed; no source normalization |
| V14 | Coin audit/identity/row/period schema, requires `btree_gist`; retained unchanged |
| V15 | Backfills identity snapshots/file counters, rejects noncontiguous progress, drops `coin_import_row`, `coin_import_file_fund`, `coin_import_period` |

V15 leaves `coin_import_file`, `coin_order_identity` and the earlier order table.
Their Java import/order repositories have been removed in the current rewrite.
Stop old import workers before any V15 upgrade; they require the retired tables.
The migration does not drop `btree_gist` or prove the replacement importer works.

See [Coin schema notes](../../../../../../coin-order-schema.md) for field mapping and
[Verification](../../../../../../docs/project-understanding/VERIFICATION.md) for which
upgrade checks ran historically versus the current checkpoint. No database change
was performed by this documentation reconciliation.
