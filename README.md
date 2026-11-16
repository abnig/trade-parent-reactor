# Trade Parent Reactor

A Java/Spring application for mutual-fund portfolio management, trade/ledger CSV
imports and MCP read tools, with an independent React UI.

Documentation reconciled with commit `719606a` on 16 November 2026. The Coin
importer is being rewritten: its upload endpoint, mapper and writers are incomplete.
See [Coin status](docs/COIN_BATCH_RUNBOOK.md) before attempting an import.

## Modules

| Module | Purpose |
|---|---|
| `trade-model` | Portfolio/account POJOs, result types and legacy JPA trade/ledger entities |
| `trade-repository` | JDBC portfolio/security repositories, JPA trade/ledger repositories, V1–V15 migrations |
| `trade-rest` | Session authentication, registration/recovery/profile, owned portfolio CRUD and analytics |
| `trade-batch` | Legacy trade/ledger jobs under `com.trading.kite`; unfinished Coin pipeline |
| `trade-mcp-server` | Four read tools and one symbol resource over raw trade/ledger data |
| `trade-coverage` | Aggregate JaCoCo reporting; no runtime service |
| `trade-ui` | React + Vite browser application; outside the Maven reactor |

See [architecture](ARCHITECTURE.md) for dependencies and persistence boundaries.

## Build and test

Use Java 25 (the current POM setting), Maven, and a Node version supported by
[the frontend lockfile](trade-ui/package-lock.json). Docker must be running for
PostgreSQL integration tests; they use disposable Testcontainers databases.

```bash
mvn verify -Pdev
npm --prefix trade-ui ci
npm --prefix trade-ui test
npm --prefix trade-ui run build
```

Use `mvn -o verify -Pdev` only when dependencies are already cached. For a focused
backend check, use `mvn -pl trade-rest -am test`. For clean tests, coverage and HTML
test reports together, run `scripts/verify-with-reports.sh -Pdev`.

Reports are generated under module `target/surefire-reports`, `target/site/jacoco`
and `trade-coverage/target/site/jacoco-aggregate`. Never commit `target`,
`node_modules`, `dist` or credential files. [Verification](docs/project-understanding/VERIFICATION.md)
records dated results and their limits.

## Local REST and UI

The `dev` profile defaults to REST port **6666** and database `postgresd/public`;
Vite uses **7777** and proxies `/api` to REST. Supply `DB_USERNAME` and `DB_PASSWORD`
externally, or override `SPRING_DATASOURCE_*`. Do not rely on committed credential
fallbacks. Flyway and Hibernate schema creation are disabled: reconcile the schema
and migration history using the [migration guide](trade-repository/src/main/resources/db/migration/README.md)
before starting against a database.

After building, run in separate terminals:

```bash
java -jar trade-rest/target/trade-rest-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
npm --prefix trade-ui run dev
```

Open `http://localhost:7777`. Register/login to manage brokers, funds, transactions,
valuations and analytics. Recovery email additionally needs SMTP configuration,
`PASSWORD_RESET_FROM` and `PASSWORD_RESET_URL`; the profile hint is separate from
the three recovery answers. See [workflows](docs/project-understanding/WORKFLOWS.md).

`/actuator/health` is public. Portfolio routes require a session, and unsafe
requests require CSRF. [API contracts](docs/project-understanding/CONTRACTS_AND_UI.md)
describe routes, dates, pagination and UI behavior.

## Other runtimes

- [Legacy batch](trade-batch/ARCHITECTURE.md): custom startup runners execute CSV
  imports even with `spring.batch.job.enabled=false`; review inputs and deletion
  flags before launching.
- [MCP](docs/project-understanding/WORKFLOWS.md#mcp-contracts-and-independent-runtime):
  configured on loopback port 8081. Its POM names a nonexistent main class, so
  packaging success does not establish a runnable MCP JAR.
- [macOS deployment](DEPLOYMENT_ARCHITECTURE.md): separate REST/UI LaunchAgents,
  production ports 8888/9999, external credentials and a startup health gate.

## Documentation map

- [Project handoff and known gaps](docs/PROJECT_UNDERSTANDING.md)
- [Backend components](docs/project-understanding/COMPONENTS.md)
- [Schema and dated catalog evidence](docs/project-understanding/DATA_MODEL.md)
- [Analytics status](ANALYTICS_PLAN.md) and [multi-user gaps](multi-user-support-gap-analysis.md)
- [Learning history](docs/project-understanding/LEARNING_LOG.md)
- [Contributor instructions](AGENTS.md)
