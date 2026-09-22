# Coin order-history persistence

As of V10, `isin`, `plan`, and `folio_number` are fund metadata on
`mutual_fund`. Each fund row now has at most one folio. Orders reference that
metadata through `mutual_fund_id` and no longer contain a folio column.

## V11 transaction metadata

V11 moves `status`, `exchange_order_id`, `remarks`, `tag`, and `settlement_id`
from `mutual_fund_order` to nullable TEXT columns on `mutual_fund_txn`. Apply
`V11__move_order_metadata_to_transactions.sql` after V10 in one transaction with
order/transaction writers stopped. Updated application code requires V11.

Metadata is copied only through the existing same-fund `mutual_fund_txn_id` link.
The migration aborts and rolls back if any unlinked order has non-null metadata,
or if orders linked to one transaction have different non-null values for any
individual field. Matching repeated values are accepted; complementary non-null
fields from multiple orders are combined. No transaction is created or inferred.
Resolve links and conflicts explicitly before retrying. The migration then drops
all five columns from orders. Orders with no metadata can remain unlinked.

Read-only preflight checks:

```sql
SELECT mutual_fund_order_id, mutual_fund_id
FROM mutual_fund_order
WHERE mutual_fund_txn_id IS NULL
  AND (status IS NOT NULL OR exchange_order_id IS NOT NULL OR remarks IS NOT NULL
       OR tag IS NOT NULL OR settlement_id IS NOT NULL);

SELECT mutual_fund_txn_id
FROM mutual_fund_order
WHERE mutual_fund_txn_id IS NOT NULL
GROUP BY mutual_fund_txn_id
HAVING COUNT(DISTINCT status) > 1 OR COUNT(DISTINCT exchange_order_id) > 1
    OR COUNT(DISTINCT remarks) > 1 OR COUNT(DISTINCT tag) > 1
    OR COUNT(DISTINCT settlement_id) > 1;
```

All transaction create/update/get/list/fund-filtered endpoints accept or return
optional JSON strings `status`, `exchangeOrderId`, `remarks`, `tag`, and
`settlementId`. For example, add these properties to a transaction request:

```json
{
  "status": "PROCESSING",
  "exchangeOrderId": "000009876543210987654321",
  "remarks": "Processing information",
  "tag": "{\"tag\": [\"coinandroid\"]}",
  "settlementId": "000123"
}
```

Status preserves arbitrary source text, including PROCESSING and COMPLETE. Tags
remain strings, including JSON-shaped text, whitespace, or `coinandroidsip`.
Exchange and settlement references are nonunique and preserve leading zeros.
On create, omitted/null metadata stays null. On update, omitted/null metadata
preserves stored values for legacy clients; an empty string is stored verbatim
and can blank a field. The transaction form exposes all five optional fields,
and each transaction has expandable metadata details.

Status is descriptive only: portfolio queries continue counting transaction rows
using their existing rules, without status-based filtering. Changing status does
not post an order or create a valuation. After V11, unlinked orders cannot store
these five fields; source ingestion must resolve the transaction first or provide
an explicitly designed staging workflow.

`PostgresOrderMetadataMigrationTest` checks backfill, shared references, exact text,
column removal, and rollback for every conflicting or unlinked metadata field.
Transaction persistence and API tests cover both repositories, all read endpoints,
legacy updates, ownership, and unchanged financial totals when metadata changes.

## V10 upgrade and fund API

Apply `V10__move_folio_to_mutual_fund.sql` after V9, atomically, with fund/order
writers stopped. Flyway runs this PostgreSQL migration transactionally. When
applying manually while Flyway is disabled, wrap the entire script in a single
transaction and stop on any SQL error. Deploy the updated application only after
the migration succeeds. No live database migration is performed by editing this repository.

The migration preserves ISIN/plan, copies distinct non-null order folios to their
funds, leaves missing folios null, and then drops the order folio column. Multiple
distinct folios for one fund cause the scalar subquery to fail and the transaction
to roll back. Resolve such funds explicitly before retrying; do not pick one folio
or combine identifiers. This read-only query identifies conflicting fund IDs:

```sql
SELECT mutual_fund_id, COUNT(DISTINCT folio_number) AS distinct_folios
FROM mutual_fund_order
WHERE folio_number IS NOT NULL
GROUP BY mutual_fund_id
HAVING COUNT(DISTINCT folio_number) > 1;
```

All `/api/mutual-funds` create, update, get, list, and broker-filtered list
operations use optional JSON fields `isin`, `plan`, and `folioNumber`. The create
and edit form and fund table expose all three. Example request:

```json
{
  "brokerAccountId": 1,
  "mutualFundName": "Index Fund",
  "isin": "INF123456789",
  "plan": "Direct Growth",
  "folioNumber": "00001234/05"
}
```

As of V13, ISIN is optional TEXT without length validation and may be `N/A`.
Apply `V13__relax_mutual_fund_isin_length.sql` after V12 before using the updated
form/API. Plan and folio are text, preserving
leading zeros and slashes. On creation, missing/null/empty metadata becomes SQL
NULL. On update, omitted or null metadata preserves the existing value for older
clients; an explicit empty string clears that field to SQL NULL. No order REST API
or importer exists, and no MCP tool contract changes are required.

## Original V9 order support

V9 adds order history to the existing JDBC mutual-fund model. Every transaction
currently contributes to portfolio calculations. Pending orders therefore need a
separate `mutual_fund_order` table. Saving or changing an order never creates,
updates, or infers a completed transaction or valuation.
There is no CSV importer or new order API in this change.

## Field mapping

| CSV | Existing equivalent | Current storage |
| --- | --- | --- |
| client_id | `mutual_fund_broker_account.account_id VARCHAR(100)` | Reused via fund → broker account. This is the external account identifier, not `owner_user_id BIGINT` referencing `users.id`. |
| isin | `trade_records.isin` exists only in the separate trade pipeline | Nullable `mutual_fund.isin TEXT` after V13, accepting `N/A` without length restrictions. |
| scheme_name | `mutual_fund.mutual_fund_name VARCHAR(255)` | Reused. No new fund/account copies. |
| plan | None | New nullable `mutual_fund.plan TEXT`. No default, inferred vocabulary, or parsing. |
| transaction_mode | `mutual_fund_txn.txn_type VARCHAR`; Java `TransactionType` has BUY/SELL | New order `txn_type VARCHAR`, preserving BUY/SELL and other historical strings without changing the completed-transaction enum. |
| settlement_id | Added to orders in V9 | Moved to nullable, nonunique `mutual_fund_txn.settlement_id TEXT` in V11. |
| trade_date | Completed transaction `mutual_fund_txn.txn_date TIMESTAMP` | New order `trade_date DATE` / Java `LocalDate`. Preserve DD/MM/YYYY as a date without inferring execution/allotment or rewriting old transaction timestamps. |
| ordered_at | No equivalent; `create_date` is an audit timestamp and trade-record execution time has different semantics | New order `ordered_at TIME WITHOUT TIME ZONE` / Java `LocalTime`. Preserve hh:mm AM/PM as local time, without combining it with an inferred date/timezone. |
| folio_number | Added to orders in V9 | Moved to nullable `mutual_fund.folio_number TEXT` in V10, preserving slashes and leading zeros. One folio per fund row. |
| amount | Completed transaction `amount NUMERIC(18,2)` | Same name/type on orders for the independently recorded order amount in INR. |
| units | Completed transaction `units NUMERIC(18,3)` | Widened to `NUMERIC(21,6)`; same type on orders. |
| nav | Completed transaction `avg_price NUMERIC(18,3)` | Reuse `avg_price` naming, widened to `NUMERIC(21,6)`; same type on orders. |
| status | Added to orders in V9 | Moved to nullable `mutual_fund_txn.status TEXT` in V11, preserving arbitrary source values with no default. |
| exchange_order_id | Trade pipeline has unrelated `trade_records.order_id` | Moved to nullable, nonunique `mutual_fund_txn.exchange_order_id TEXT` in V11. |
| remarks | Added to orders in V9 | Moved to nullable `mutual_fund_txn.remarks TEXT` in V11. |
| tag | Added to orders in V9 | Moved to nullable `mutual_fund_txn.tag TEXT` in V11, preserving plain text and JSON-shaped text verbatim. |

Order financial fields represent source order facts, which can exist before a
transaction and can differ from its eventual posted facts. They are not additional
columns on completed transactions. Six fractional places preserve fractional NAV
and units; increasing total precision to 21 retains the old 15 integer digits.
`BigDecimal` is used throughout. Source precision beyond six places would require
a separately reviewed widening; no full source data was supplied for verification.

## Relationships and lifecycle

The order's generated primary key is `mutual_fund_order_id BIGINT`.
`mutual_fund_id BIGINT NOT NULL` reuses the fund/account/owner relationship.
`mutual_fund_txn_id BIGINT` optionally references an explicitly reconciled completed
transaction. A composite foreign key requires that transaction to belong to the
same fund. A supporting unique key on transaction `(mutual_fund_txn_id,
mutual_fund_id)` exists solely for referential integrity. No one-to-one order/fill
cardinality or source-reference uniqueness is assumed. Referenced transactions
cannot be deleted or moved to another fund without first resolving their links.

All source order fields and the optional transaction link are nullable. Missing
settlement metadata remains null on the transaction; missing folios remain null
on the fund. Zero
units/NAV are preserved as source values, never used as evidence of completed allotment. New funds may omit ISIN
and plan; existing rows receive no historical backfill. Creation/update audit
columns use the existing `TIMESTAMP DEFAULT CURRENT_TIMESTAMP` convention.

`UserPortfolioRepositoryFactory.orders(userId)` supplies the order repository.
Every read, insert, update, list and count checks ownership in SQL, including both
the original order and a replacement fund on update. Unknown/unassigned owners
cannot access orders. Both existing fund repositories save/read fund metadata.
Null or omitted metadata on a fund update preserves existing values for older clients; V10 adds explicit
clearing through empty strings. Transaction DTOs include the five V11 fields; transaction type enum validation is unchanged.

The fund/order index supports the implemented per-fund pagination and ownership
queries. The transaction-reference index supports foreign-key lookups. There are
no speculative ISIN, tag, settlement, status, or exchange-reference indexes.

## Migration and checks

Migrations: V9 adds orders, V10 moves folios to funds, and V11 moves the five
metadata fields to transactions. All are required before running this application version. Existing Flyway
configuration remains unchanged (disabled by default); this work does not apply
anything to production. Precision changes may require a table rewrite/lock and
should be scheduled as part of the deployment's normal migration process.

Application changes: `MutualFund`, new `MutualFundOrder`, both existing fund JDBC
repositories, new `MutualFundOrderRepository`/`OwnedOrderRepository`, and the user
portfolio factory interface/implementation. Existing integration fixtures include
V9 through V11. Existing migration files remain unchanged.

`MutualFundOrderPersistenceTest` checks populated-schema upgrade, both lifecycle
cases, null/zero details, decimal precision, dates, unknown direction, pagination,
and ownership. Transaction metadata checks cover identifiers, raw tags, statuses,
shared references, and legacy updates. The PostgreSQL
subclass runs the same tests with actual SQL and Flyway baseline-at-V8 → V9 → V10 → V11,
validation, and a no-op second migration; it also tests the unscoped fund JDBC
repository. PostgreSQL tests use a container-managed `order_test` database and create/drop
a unique schema per test. `PostgresFundFolioMigrationTest` additionally verifies
folio backfill and transactional rollback when a fund has conflicting folios.

Run the REST reactor with `mvn -pl trade-rest -am test`, or the full clean build
with `mvn clean verify`. Start Docker first. All PostgreSQL suites now run by
default through Testcontainers, using separate container-managed databases.
`ORDER_TEST_POSTGRES_URL` and the other previous opt-in variables are no longer
used. Docker startup errors fail the tests rather than skipping them. Containers
are removed when the test JVM exits.

## Unresolved source semantics

Plan is treated as optional fund metadata, consistent with the existing
account-scoped scheme model; the blank sample provides no vocabulary or evidence
of per-order plan changes. Timezone, execution/allotment date, order/fill
cardinality, source deduplication scope, and automatic lifecycle transitions are
not inferred. Account/fund matching and CSV date/time parsing belong to a future
import task. No existing transaction is automatically linked to an order.

Verified locally on 2026-09-13 with plain `mvn clean verify`, without test database
URL environment variables: all five modules built successfully, 180 tests
passed, zero failures/errors/skips. All 15 order checks ran (7 H2, 8 PostgreSQL).
PostgreSQL containers were provisioned automatically by Testcontainers.
`git diff --check` also passed. No production migration, commit, or push was
performed.
