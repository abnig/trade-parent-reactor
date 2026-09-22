# Flyway migrations

This directory is packaged with the shared `trade-repository` module and is
therefore available at the standard Flyway classpath location
`classpath:db/migration` to the REST application that owns schema migration
startup.

Use one immutable SQL migration per schema change:

```text
V<version>__<description>.sql
```

The current schema is represented by V1 through V4. Existing databases are
baselined at version 4 only after their schema has been verified. `V5__create_app_user.sql` adds the registration tables and seeds `ROLE_USER`.
Existing authentication tables must match the V5 definitions.

Do not edit a migration after it has been applied to a shared database. Add a
new migration to correct it, and never modify `flyway_schema_history` manually.

`V6__portfolio_ownership.sql` adds broker-account ownership. Existing rows stay
unassigned until explicitly mapped to verified users. While Flyway is disabled,
apply V5 (if needed) and V6 manually before using login and private portfolios.

`V7__password_recovery.sql` adds recovery answers, challenges, reset tokens,
rate-limit counters, and user credential/recovery versions. Apply it after V6
and before starting REST with password-recovery support. Existing users retain
their credentials but must enroll their own recovery answers. While Flyway is
disabled, record manual application of V7 along with V5/V6. See
[password reset setup](../../../../../../password-reset-backend.md).

`V8__user_profile_hint.sql` adds a separate, single profile hint and a
case-insensitive unique email index. Check and resolve existing case-insensitive
email duplicates before applying it. Apply the script atomically after V7; see
[profile migration and API documentation](../../../../../../user-profile-management.md).

`V9__mutual_fund_orders.sql` adds optional fund ISIN/plan, widens transaction
units/NAV to six decimal places, and creates owner-linked mutual-fund order
history separately from completed transactions. Apply after V8 before running
the updated fund repositories. It performs no historical backfill. See
[Coin order field mapping and persistence notes](../../../../../../coin-order-schema.md).

`V10__move_folio_to_mutual_fund.sql` moves the order folio to a nullable
`mutual_fund.folio_number`; ISIN and plan already reside on the fund. Apply after
V9 in one transaction with fund/order writers stopped. Conflicting distinct folios
for a fund abort the migration without discarding data. Resolve these explicitly
before retrying. The updated application requires V10. See the V10 upgrade and
API contract in [Coin order notes](../../../../../../coin-order-schema.md).

`V11__move_order_metadata_to_transactions.sql` moves status, exchange order ID,
remarks, tag, and settlement ID from orders to nullable transaction TEXT columns.
Apply after V10 atomically with order/transaction writers stopped. Unlinked orders
containing metadata or conflicting values for one transaction abort the migration.
No transactions are created automatically. The application now requires V11. See
[transaction metadata and preflight checks](../../../../../../coin-order-schema.md).

`V12__unique_broker_account_per_owner.sql` adds the composite constraint
`uq_broker_account_name_account_owner` on `(broker_name, account_id, owner_user_id)`.
Apply after V11. It uses ordinary column comparison and standard UNIQUE semantics:
different owners may share broker/account values, and NULL owners remain distinct.
It does not normalize names or identifiers. Duplicate inserts/updates use the
existing REST data-constraint response (HTTP 400).

Existing duplicate owned accounts cause the migration to fail without deleting
or merging rows. Resolve those accounts and their fund references explicitly
before retrying. This read-only check finds conflicting keys:

```sql
SELECT broker_name, account_id, owner_user_id, COUNT(*) AS account_count
FROM mutual_fund_broker_account
WHERE owner_user_id IS NOT NULL
GROUP BY broker_name, account_id, owner_user_id
HAVING COUNT(*) > 1;
```

`V13__relax_mutual_fund_isin_length.sql` removes the exact-length ISIN check and
widens `mutual_fund.isin` to nullable TEXT. Apply after V12 before deploying the
updated form/API, which accept `N/A` and other source text without length limits.
Existing values and the optional-field update/clearing behavior are preserved.
