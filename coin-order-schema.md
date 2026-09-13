# Coin order-history persistence

V9 adds order history to the existing JDBC mutual-fund model. Every transaction
currently contributes to portfolio calculations. Pending orders therefore need a
separate `mutual_fund_order` table. Saving or changing an order never creates,
updates, or infers a completed transaction or valuation, even for `COMPLETE`.
There is no CSV importer or new order API in this change.

## Field mapping

| CSV | Existing equivalent | Storage after V9 |
| --- | --- | --- |
| client_id | `mutual_fund_broker_account.account_id VARCHAR(100)` | Reused via fund → broker account. This is the external account identifier, not `owner_user_id BIGINT` referencing `users.id`. |
| isin | `trade_records.isin` exists only in the separate trade pipeline | New nullable `mutual_fund.isin VARCHAR(12)`, with an exact-length check. |
| scheme_name | `mutual_fund.mutual_fund_name VARCHAR(255)` | Reused. No new fund/account copies. |
| plan | None | New nullable `mutual_fund.plan TEXT`. No default, inferred vocabulary, or parsing. |
| transaction_mode | `mutual_fund_txn.txn_type VARCHAR`; Java `TransactionType` has BUY/SELL | New order `txn_type VARCHAR`, preserving BUY/SELL and other historical strings without changing the completed-transaction enum. |
| settlement_id | None | New order `settlement_id TEXT`, nullable, nonunique. |
| trade_date | Completed transaction `mutual_fund_txn.txn_date TIMESTAMP` | New order `trade_date DATE` / Java `LocalDate`. Preserve DD/MM/YYYY as a date without inferring execution/allotment or rewriting old transaction timestamps. |
| ordered_at | No equivalent; `create_date` is an audit timestamp and trade-record execution time has different semantics | New order `ordered_at TIME WITHOUT TIME ZONE` / Java `LocalTime`. Preserve hh:mm AM/PM as local time, without combining it with an inferred date/timezone. |
| folio_number | None | New order `folio_number TEXT`, preserving slashes and leading zeros. Kept per order since a fund can have multiple folios. |
| amount | Completed transaction `amount NUMERIC(18,2)` | Same name/type on orders for the independently recorded order amount in INR. |
| units | Completed transaction `units NUMERIC(18,3)` | Widened to `NUMERIC(21,6)`; same type on orders. |
| nav | Completed transaction `avg_price NUMERIC(18,3)` | Reuse `avg_price` naming, widened to `NUMERIC(21,6)`; same type on orders. |
| status | None | New order `status TEXT`, preserving arbitrary historical values, with no inferred default. |
| exchange_order_id | Trade pipeline has `trade_records.order_id`, with no mutual-fund/account relationship | New order `exchange_order_id TEXT`, nonunique even across accounts and brokers. |
| remarks | None | New order `remarks TEXT`, nullable. |
| tag | None | New order `tag TEXT`, preserving plain text and JSON-shaped text verbatim, including whitespace. |

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
settlement/folio details stay missing. Zero units/NAV are preserved as source
values, never used as evidence of completed allotment. New funds may omit ISIN
and plan; existing rows receive no historical backfill. Creation/update audit
columns use the existing `TIMESTAMP DEFAULT CURRENT_TIMESTAMP` convention.

`UserPortfolioRepositoryFactory.orders(userId)` supplies the order repository.
Every read, insert, update, list and count checks ownership in SQL, including both
the original order and a replacement fund on update. Unknown/unassigned owners
cannot access orders. Both existing fund repositories save/read the new metadata;
null ISIN/plan on a fund update preserves existing metadata for older clients.
Explicit clearing to SQL NULL is consequently not part of the existing update
contract. Existing REST DTOs and transaction enum validation are unchanged.

The fund/order index supports the implemented per-fund pagination and ownership
queries. The transaction-reference index supports foreign-key lookups. There are
no speculative ISIN, tag, settlement, status, or exchange-reference indexes.

## Migration and checks

Migration: `trade-repository/src/main/resources/db/migration/V9__mutual_fund_orders.sql`.
Apply after V8 and before running this application version. Existing Flyway
configuration remains unchanged (disabled by default); this work does not apply
anything to production. Precision changes may require a table rewrite/lock and
should be scheduled as part of the deployment's normal migration process.

Application changes: `MutualFund`, new `MutualFundOrder`, both existing fund JDBC
repositories, new `MutualFundOrderRepository`/`OwnedOrderRepository`, and the user
portfolio factory interface/implementation. Existing integration fixtures include
V9. The migration does not modify existing migration files.

`MutualFundOrderPersistenceTest` checks populated-schema upgrade, both lifecycle
cases, null/zero details, decimal precision, identifiers, raw tags, unknown status
and direction, shared references, pagination, and ownership. The PostgreSQL
subclass runs the same tests with actual SQL and Flyway baseline-at-V8 → V9,
validation, and a no-op second migration; it also tests the unscoped fund JDBC
repository. PostgreSQL tests use a container-managed `order_test` database and create/drop
a unique schema per test.

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
