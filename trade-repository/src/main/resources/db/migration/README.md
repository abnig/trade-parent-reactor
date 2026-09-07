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
