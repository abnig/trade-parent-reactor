# Trade Parent Reactor

## Executive summary

This repository is a Java trading-data platform with three backend workloads and a React UI:

- A REST API for broker accounts, mutual funds, transactions, and valuation history.
- Spring Batch command-line applications that import trade-book and ledger CSV files into PostgreSQL.
- A Spring AI MCP server exposing trade and ledger analytics over SSE.
- A React/Vite single-page application that calls the REST API.

It is a Maven multi-module modular monolith. Mutual-fund persistence uses explicit JDBC SQL, while trade and ledger ingestion/analytics retain Spring Data JPA.

## Repository map

```text
trade-parent-reactor/
├── pom.xml                  Maven reactor and dependency management
├── trade-model/             Shared domain POJOs, JPA entities, enums, result DTOs
├── trade-repository/        Repository contracts and JDBC/JPA implementations
├── trade-rest/              Spring MVC mutual-fund REST API and tests
├── trade-batch/             Spring Batch CSV import applications
├── trade-mcp-server/        Spring AI MCP server, tools, resources and configuration
└── trade-ui/                Independent React/Vite SPA
```

Dependency direction:

```text
trade-model
   └── trade-repository
         ├── trade-rest
         ├── trade-batch
         └── trade-mcp-server

trade-ui communicates with trade-rest over HTTP.
```

## Technology stack

- Java 25 and Maven
- Spring Boot 4.0.6
- Spring MVC (`trade-rest`)
- Spring WebFlux and Spring AI MCP Server (`trade-mcp-server`)
- Spring Batch (`trade-batch`)
- Spring Data JPA/Hibernate for trade and ledger records
- `JdbcTemplate` / `NamedParameterJdbcTemplate` for mutual funds and trade analytics
- PostgreSQL and HikariCP
- React 19, Vite 7, JavaScript and CSS
- JUnit 5, Spring MVC slice tests, Mockito

No broker, queue, cache, cloud service, outgoing HTTP client, Kafka, or RabbitMQ integration was found.

## Runtime architecture

```text
                    React/Vite SPA
                         │ HTTP /api/*
                         ▼
              trade-rest : Spring MVC, port 8082
                         │
                         ▼
             trade-repository : JDBC contracts/impls
                         │
                         ▼
                    PostgreSQL

CSV directories ─► trade-batch : Spring Batch CLI ─► JPA repositories ─► PostgreSQL
                                                        ▲
MCP client ─ SSE/messages ─► trade-mcp-server, port 8081 │
                           │ Spring AI tools/resources   │
                           └─────────────────────────────┘
```

### Major layers

- **Domain model:** Shared mutual-fund POJOs and JPA entities for trade/ledger records.
- **Persistence:** Repository contracts in `com.trading.repository`; JDBC and JPA implementations in `com.trading.repository.impl`.
- **REST orchestration:** Controllers directly use repositories; there is no dedicated service layer.
- **Validation/error handling:** Bean Validation, `MutualFundReferenceValidator`, and `GlobalExceptionHandler`.
- **Batch orchestration:** file discovery → asynchronous job launch → reader → mapper → processor → writer.
- **MCP adapter:** Spring AI annotations publish tool/resource methods to MCP clients.

Patterns in use include the Repository pattern, CSV mapper adapters, dependency injection, Spring Batch step/job lifecycle listeners, and central REST exception translation.

## Domain model

```text
MutualFundBrokerAccount (broker_account_id)
        1
        │
        └── * MutualFund (mutual_fund_id, broker_account_id)
                    │
                    ├── * MutualFundTxn (mutual_fund_txn_id, mutual_fund_id)
                    └── * MutualFundValue (val_id, mutual_fund_id)
```

The relationships above are inferred from the JDBC SQL and REST reference validation. No DDL or migration scripts are stored in this repository to confirm physical foreign-key constraints.

### Important domain types

- `MutualFundBrokerAccount`: broker and account identity with timestamps.
- `MutualFund`: a named fund associated with a broker account.
- `MutualFundTxn`: dated `BUY` or `SELL` transaction with amount, units, and average price.
- `MutualFundValue`: a point-in-time total fund value.
- `TradeRecord`: imported equity execution data.
- `LedgerRecord`: imported debit/credit ledger record.
- `TradeDetailsResult`: result DTO for trade aggregation queries.
- `LedgerBalances`: a mapped entity with no repository, controller, batch writer, or MCP usage; it appears unused.

There is no explicit domain state machine. Mutual-fund lifecycle is CRUD, with `TransactionType` as the principal domain-state field.

## Entry points

| Entry point | Responsibility |
|---|---|
| `com.trading.TradeRestApplication` | Starts REST API on port 8082. |
| `com.trading.TradeBatchApplication` | Starts then launches trade CSV jobs. |
| `com.trading.LedgerBalancesBatchApplication` | Starts then launches ledger CSV jobs. |
| `com.trading.TradeMcpServerApplication` | Starts loopback-only MCP server on port 8081. |
| `trade-ui/src/main.jsx` | Browser entry point rendering the React application. |

## REST API inventory

| Resource | Endpoints |
|---|---|
| Broker accounts | `GET/POST /api/broker-accounts`, `GET/PUT/DELETE /api/broker-accounts/{id}` |
| Mutual funds | `GET/POST /api/mutual-funds`, `GET/PUT/DELETE /api/mutual-funds/{id}`, `GET /api/mutual-funds/broker-account/{brokerAccountId}` |
| Fund transactions | `GET/POST /api/mutual-fund-txns`, `GET/PUT/DELETE /api/mutual-fund-txns/{id}`, `GET /api/mutual-fund-txns/mutual-fund/{mutualFundId}` |
| Fund values | `GET/POST /api/mutual-fund-values`, `GET/PUT/DELETE /api/mutual-fund-values/{id}`, `GET /api/mutual-fund-values/mutual-fund/{mutualFundId}` |

The React client centralizes these requests in `trade-ui/src/api/api.js`. During development, Vite proxies `/api` to `http://localhost:8082`.

### Pagination contract

All REST collection endpoints accept zero-based `page` and `size` parameters. Defaults are `page=0` and `size=20`; the maximum size is 100. Collection responses are paged objects rather than raw arrays:

```json
{
  "content": [],
  "page": 1,
  "size": 20,
  "totalElements": 137,
  "totalPages": 7,
  "first": false,
  "last": false
}
```

For example: `GET /api/mutual-fund-txns?page=1&size=20`. Existing UI callers currently expect arrays and must be updated separately to consume `response.content`.

## Important execution flows

### Create a mutual fund

```text
POST /api/mutual-funds
→ MutualFundController.create()
→ MutualFundReferenceValidator.requireBrokerAccount()
→ MutualFundBrokerAccountRepository.findById()
→ MutualFundRepository.save()
→ PostgreSQL mutual_fund
→ MutualFundRepository.findById()
→ 201 Created
```

The controller validates the request and checks the parent broker account before `MutualFundRepositoryImpl` issues `INSERT ... RETURNING mutual_fund_id` and rereads the inserted row.

### Create a fund transaction

```text
POST /api/mutual-fund-txns
→ MutualFundTxnController.create()
→ Bean Validation and enum deserialization
→ MutualFundReferenceValidator.requireMutualFund()
→ MutualFundTxnRepositoryImpl.save()
→ INSERT ... RETURNING mutual_fund_txn_id
→ findById()
→ 201 Created
```

The server persists transactions but does not calculate positions, returns, or valuations. The transaction screen calculates net totals client-side.

### Fund value chart

```text
Analytics.jsx
→ GET /api/mutual-funds and GET /api/mutual-fund-values
→ controllers
→ JDBC repositories
→ PostgreSQL
→ browser filters by mutualFundId and renders SVG chart
```

### Trade CSV import

```text
TradeBatchApplication.run()
→ BatchJobService.runCsvProcessingJob()
→ TaskExecutorJobOperator.start(csvProcessingJob)
→ optional truncate step
→ TradeFileItemReader
→ TradeFieldSetMapper
→ TradeRecordItemProcessor
→ TradeRecordItemWriter
→ TradeRecordRepository.saveAll()
→ trade_records
```

CSV fields are mapped using ISO `yyyy-MM-dd` and `yyyy-MM-dd'T'HH:mm:ss` formats. Header skipping is not configured.

### Ledger CSV import

```text
LedgerBalancesBatchApplication.run()
→ LedgerBalancesBatchJobService.runCsvProcessingJob()
→ ledgerCsvProcessingJob
→ optional truncate step
→ LedgerFileItemReader
→ LedgerRecordFieldSetMapper
→ LedgerRecordItemProcessor adds filename/create timestamp
→ LedgerRecordItemWriter
→ LedgerRecordRepository.saveAll()
→ ledger_records
```

Despite the `LedgerBalances` naming, this pipeline persists `LedgerRecord`, not `LedgerBalances`.

### MCP analytical lookup

```text
MCP request via /sse and /mcp/message
→ Spring AI MethodToolCallbackProvider
→ TradeMcpServerTools
→ JPA repository or TradeRecordFastLaneRepositoryImpl
→ PostgreSQL
→ MCP response
```

MCP exposes ledger date-range retrieval, average buy/sell queries, buy/sell sequencing analytics, and a resource containing valid stock symbols.

## Persistence

### JDBC tables

- `mutual_fund_broker_account`
- `mutual_fund`
- `mutual_fund_txn`
- `mutual_fund_value`

These use parameterized SQL and manual row mapping in the four `*RepositoryImpl` classes.

### JPA tables

- `trade_records`
- `ledger_records`
- `ledger_balances` is mapped but apparently unused.

`TradeRecordRepository` and `LedgerRecordRepository` extend `JpaRepository`. Their table-wide deletion methods are explicitly transactional. Spring Batch chunks also use the configured transaction manager.

REST JDBC write methods have no application-level transaction spanning their insert/update and subsequent reread.

## MCP tools and resources

`TradeMcpServerTools` exposes:

- `Get-Ledger-Details-By-Date-Range`
- `Average-Buy-Price-and-Count`
- `Average-Sell-Price-and-Count`
- `Average-Buy-Sell-Price-and-Count`

`TradeMcpServerResources` exposes `All-Valid-Symbols` at resource URI `symbols`.

## Configuration

Each backend executable has `src/main/resources/application.properties`.

- PostgreSQL defaults to `jdbc:postgresql://localhost:5432/postgres`.
- `DB_USERNAME` and `DB_PASSWORD` can override configured credentials.
- Batch properties control CSV directories, truncation, recursion, chunk sizes and skip limits.
- Default CSV paths are developer-local paths under `/Users/abnig19/zerodha/...`.
- MCP runs at `127.0.0.1:8081`; REST runs at port 8082.
- No Spring profiles, environment-specific property files, secret store, or feature-flag mechanism was found.
- `spring.ai.mcp.server.veersion` is misspelled and likely ignored.

### Database migration strategy

Flyway owns versioned PostgreSQL schema evolution. The one shared migration source is `trade-repository/src/main/resources/db/migration`; the REST application runs it, while Batch and MCP use the resulting shared schema without running Flyway. Migration files are immutable and follow `V<version>__<description>.sql`.

The current migration chain is `V1__extensions.sql`, `V2__spring_batch_schema.sql`, `V3__application_schema.sql`, and `V4__spring_ai_schema.sql`. It creates the `uuid-ossp` and pgvector (`vector`) extensions, Spring Batch metadata, business tables, and Spring AI/vector tables in dependency order. The next reserved migration is `V5__create_app_user.sql`.

For a fresh PostgreSQL database, normal REST startup applies V1–V4 and records them in `flyway_schema_history`. For an existing populated database, first verify it matches the migration prerequisites, then start REST exactly once with the `flyway-baseline` profile. This records a Flyway baseline at version 4 without rerunning V1–V4 or modifying application data. Normal startup keeps `baseline-on-migrate=false`, validates history checksums, and disables Flyway clean.

Future workflow: pull current migrations; never manually alter a shared schema; create and test the next migration; commit it with the code change; never edit an applied migration; and correct mistakes with a new versioned migration.

## Error handling and resilience

REST exposes structured `ApiError` responses through `GlobalExceptionHandler`:

- Bean validation and path constraints return 400.
- Invalid parent references return 400.
- Empty JDBC query results return 404.
- Data-integrity violations return a generic 400.
- Unexpected failures are logged and return a generic 500 without database details.

Batch steps can skip malformed records up to configured limits, but broadly skip `Exception.class`. There are no retries, idempotency keys, duplicate protections, archive handling, or error-file output despite archive/error properties existing in configuration.

MCP has no explicit timeouts, retries, fallbacks, or application-level exception translation. It uses synchronous JPA/JDBC operations in a WebFlux application.

## Security

- REST has no authentication or authorization.
- REST exposes all CRUD operations to callers that can reach it.
- MCP binds to loopback by default, reducing remote exposure.
- MCP CORS allows all origin patterns, methods and headers while allowing credentials; this is unsafe if it is later exposed beyond loopback.
- JDBC query values are parameterized.
- Mutual-fund endpoints validate parent references, but broker-account request bodies have no Bean Validation constraints.
- Database credentials are externalizable, but committed defaults include `postgres` / `password`.

## Observability

- SLF4J logging exists in REST, batch, and MCP code.
- The batch `JobExecutionListener` logs job metadata, duration, read/write/skip counts, and failure messages.
- Batch configuration enables metrics and tracing properties, but no exporter, trace backend, custom metrics, correlation IDs, or audit-log system is configured.
- MCP enables verbose Spring AI DEBUG logging.

## Testing and verification

Existing tests are limited to 12 Spring MVC/Mockito tests in `trade-rest`. They document validation behavior, response shape, reference errors, not-found behavior, and hiding unexpected repository failures.

Verification performed during this review:

| Command | Result |
|---|---|
| `mvn -pl trade-batch,trade-mcp-server -am package -DskipTests` | Passed. |
| `mvn -pl trade-rest -am package -DskipTests` | Passed, but produced jar has an invalid start class; see risks. |
| `npm --prefix trade-ui run build` | Passed. |
| `mvn -pl trade-rest -am test` | Failed before test execution. Mockito/Byte Buddy could not self-attach under JDK 25. |

Coverage gaps include no database integration tests, batch tests, MCP tests, UI tests, end-to-end tests, or security tests.

## Architectural risks and technical debt

### Critical

1. **REST executable jar is misconfigured.** `trade-rest/pom.xml` configures the Spring Boot plugin with an MCP application class that is not present in the REST artifact. The generated jar manifest confirms this invalid `Start-Class`; `java -jar` deployment is expected to fail.
2. **Batch truncate and concurrency can lose imported data.** One asynchronous job is launched for each CSV file. When truncation is enabled, each job can delete the full target table and race with other imports.

### High

1. **Broker account create/update returns the wrong resource.** JDBC save/update return affected-row counts, but the controller treats that value as the generated record ID, commonly rereading record `1`.
2. **No versioned schema migrations or DDL exist.** Table structure, foreign keys, indexes, defaults, and cascade behavior are not reproducible from this repository.
3. **Unauthenticated mutable REST API.**
4. **Trade fast-lane SQL is fragile.** Its `COUNT(grouped_trades.*)` expression should be validated against PostgreSQL because `COUNT(*)` is the expected aggregate form.
5. **MCP average-price descriptions do not match query output.** The JPA average query returns total value and quantity, not an explicitly calculated average price.

### Medium

1. Mixed JDBC and JPA persistence approaches produce inconsistent transaction and schema behavior.
2. The WebFlux MCP server runs blocking database work on its request path.
3. REST controllers directly orchestrate persistence and policy; no service layer separates those responsibilities.
4. Batch broadly skips exceptions, potentially masking persistent/database/programming failures.
5. Several batch properties are unused, and ledger-balance naming conflicts with persisted entity behavior.
6. Developer-local input paths and default password make startup brittle and unsafe outside development.
7. REST tests cannot run under the configured Java 25 runtime without Mockito agent/build configuration.

### Low

1. Field injection remains in batch/MCP code.
2. `LedgerBalances` appears to be dead code.
3. There are no React tests.
4. Package naming mixes JPA repository interfaces with concrete implementations under `impl`.
5. Minor stale comments, TODO text, and property typos remain.

## Open questions

- What component owns PostgreSQL schema creation and evolution?
- Are the inferred mutual-fund relationships enforced by physical foreign keys, and what are their cascade policies?
- Is batch import intended to append or replace data?
- Which system produces the CSV files, including headers, escaping, duplicate and timezone rules?
- Is `LedgerBalances` planned or obsolete?
- Is MCP only intended for local use, or will it be exposed behind a gateway?
- What is the production routing/deployment plan for the Vite SPA and REST API?
- Is absent REST authentication intentional for a trusted network, or incomplete functionality?
- Are the trade aggregates meant to be weighted averages?

## Recommended learning path

1. Read the root POM, module boundaries, and Spring Boot entry points.
2. Study the mutual-fund model and JDBC repositories.
3. Trace one successful REST write and one REST failure through validation and exception handling.
4. Obtain the database DDL before changing any persistence code.
5. Follow one trade CSV import end-to-end through reader, mapper, processor, writer, and repository.
6. Repeat the analysis for ledger import and resolve the role of `LedgerBalances`.
7. Study MCP configuration, tools, resources, JPA aggregates, and native analytical SQL.
8. Review the UI API layer and client-side transaction/chart calculations.
9. Repair jar packaging and test-runner reliability before significant feature work.
10. Address schema governance, batch idempotency/truncation semantics, and API authentication as operational priorities.

### User registration

`POST /api/auth/register` creates an enabled account, a profile, and a `ROLE_USER`
assignment in one transaction. V5 creates the supplied authentication tables
when absent and seeds `ROLE_USER`; existing tables must match that schema.

```json
{
  "username": "alice",
  "email": "alice@example.com",
  "password": "a-long-password",
  "firstName": "Alice",
  "lastName": "Smith"
}
```

Success returns HTTP 201 with `id`, `username`, `email`, and `roles`.
Invalid input returns 400; duplicate username/email returns 409. Usernames are
1–50 ASCII letters, digits, underscores, dots, or hyphens. Passwords must contain
12–72 characters and at most 72 UTF-8 bytes; only a BCrypt hash is stored.
Profile fields (`firstName`, `lastName`, `phoneNumber`, `avatarUrl`) are optional.
Username/email uniqueness follows the supplied PostgreSQL constraints (case
sensitive). Clients cannot choose roles or account flags. This endpoint creates
accounts; it does not issue login tokens.

### Login and private portfolios

The public home page displays the header and Register/Login links. Trading views
are mounted only after `GET /api/auth/me` confirms an authenticated session.
Login restores the dashboard, and logout or an expired session removes its data.
The frontend refreshes session state on window focus, periodically, and when
another tab logs in or out. Passwords and authentication tokens are never stored
in local storage.

Browser API flow:

1. `GET /api/auth/csrf` returns `token` and `headerName` for subsequent writes.
2. `POST /api/auth/login` accepts form-encoded `username` and `password` plus the
   CSRF header. Success returns 204 and uses an HttpOnly session cookie; invalid,
   disabled, locked, or expired accounts return a generic 401 response.
3. `GET /api/auth/me` returns the user ID, username, and roles, or 401.
4. `POST /api/auth/logout` with a fresh CSRF header invalidates the session.

All `/api/**` endpoints other than CSRF, login, and registration require login.
CSRF protection applies to registration and all other write requests. The frontend
obtains a fresh CSRF token before writes, including after login/logout. This uses
[Spring Security's session and CSRF support](https://docs.spring.io/spring-security/reference/7.0/servlet/exploits/csrf.html).
Use `SESSION_COOKIE_SECURE=true` when serving the application over HTTPS.

#### Database setup while Flyway is disabled

Flyway remains disabled. Before running the updated REST application:

1. Apply `trade-repository/src/main/resources/db/migration/V5__create_app_user.sql`
   if the matching authentication tables and `ROLE_USER` seed are not present.
2. Apply `trade-repository/src/main/resources/db/migration/V6__portfolio_ownership.sql`
   once to add the owner column and supporting indexes. The existing application
   tables from V3 must already exist.
3. Assign existing broker accounts to the correct users explicitly. For example,
   adapt this parameterized SQL for each verified account/user pair:

   ```sql
   UPDATE mutual_fund_broker_account
   SET owner_user_id = :verified_user_id
   WHERE broker_account_id = :verified_broker_account_id
     AND owner_user_id IS NULL;
   ```

No existing rows are assigned automatically. Unassigned accounts and their funds,
transactions, and valuations are invisible to REST users. Newly created broker
accounts receive the authenticated user's ID; client-supplied owner IDs are ignored.

REST uses request-scoped owner-filtered repository implementations. Ownership is
checked within SQL for reads, counts, summaries, inserts, updates, and deletes;
changing a parent reference also requires ownership of the new parent. Unknown or
foreign record IDs return 404 on direct reads/updates/deletes; filtered listings and
summaries include only owned records. Invalid parent references return 400.
Existing batch and MCP repository contracts are unchanged.

Record which SQL scripts you applied manually. Before enabling Flyway later,
reconcile the schema and migration history with those scripts; simply enabling it
will not record manually executed migrations or resolve the earlier missing-history
error.

Verification commands:

```bash
mvn -pl trade-rest -am clean test
npm --prefix trade-ui test
npm --prefix trade-ui run build
```

Security integration tests use a disposable H2 database in PostgreSQL mode and
exercise the real security filters and JDBC repositories with two owners and
unassigned data. PostgreSQL's role-seed `ON CONFLICT` clause is omitted only in the
H2 fixture. UI tests cover guest/authenticated rendering and authentication API
requests; they are not a browser end-to-end test.

### Analytics data

Analytics uses `GET /api/analytics/funds` for the complete fund selector and
`GET /api/analytics/funds/{fundId}/values` for the complete valuation history,
ordered by date and valuation ID. Both return JSON arrays without pagination or
record limits and restrict results to the authenticated owner. Foreign, unknown,
or unassigned funds return an empty history. The management pages retain their
existing paginated endpoints. No database migration is required for this change.
