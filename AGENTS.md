# Repository Guide

## Project and module structure

This is a Maven multi-module trading and mutual-fund application. The root
`pom.xml` is the Maven reactor.

- `trade-model`: Domain models, result types, and enums.
- `trade-repository`: Repository contracts and database implementations;
  depends on `trade-model`.
- `trade-batch`: Trade-record and ledger-record CSV batch pipelines; depends
  on `trade-repository`.
- `trade-mcp-server`: Spring AI MCP server, tools, resources, and
  configuration; depends on `trade-repository`.
- `trade-rest`: Spring Boot REST API and mutual-fund controllers; depends on
  `trade-repository`.
- `trade-ui`: Independent React + Vite frontend. Components are in `src/components`,
  API calls in `src/api/api.js`, and styles in `src/styles`.

Preserve module boundaries and dependency direction:
`trade-model` -> `trade-repository` -> `trade-batch`, `trade-mcp-server`, and
`trade-rest`. Do not reorganize modules or add cross-module dependencies unless
the task explicitly requires it.

## General development rules

- Do not perform unrelated refactoring. Prefer small, focused changes.
- Inspect relevant code, adjacent implementations, module POMs, and runtime
  configuration before modifying code.
- Explain significant architectural changes before implementing them.
- Use Spring Boot and prefer constructor dependency injection.
- Target Java 21 unless explicitly instructed otherwise. The current Maven POMs
  configure Java 25; do not change that configuration incidentally.
- Do not introduce unnecessary frameworks or libraries.

## Persistence

The target architecture is PostgreSQL, `JdbcTemplate`, explicit SQL, and plain
Java POJOs.

- Do not introduce JPA or Hibernate for new development.
- For modules that already use JPA, do not remove or migrate JPA unless an
  explicit, separate migration task requests it.
- Where a module has been migrated to JDBC, use `JdbcTemplate` or
  `NamedParameterJdbcTemplate`, parameter binding, explicit SQL, and
  `RowMapper`/equivalent mapping.
- Keep repository contracts and their implementations in `trade-repository`.

## REST

- Maintain backward compatibility for existing APIs unless a breaking change is
  explicitly requested.
- Validate request parameters and return appropriate HTTP status codes.
- Keep controllers focused on HTTP concerns; place persistence logic in
  repositories and avoid duplicating it in controllers.

## React

- Use React + Vite and preserve the existing frontend architecture.
- Do not introduce TypeScript unless explicitly requested.
- Do not commit `node_modules` or frontend build output.

## Batch

- The batch module contains separate trade-record and ledger-record CSV
  pipelines.
- Preserve existing batch behavior while making incremental improvements.
- Do not change batch persistence technology without an explicit migration
  task.

## MCP

- Preserve existing MCP module boundaries.
- Do not change MCP APIs or tool contracts without explicit approval.

## Security and repository hygiene

- Never commit or expose passwords, API keys, tokens, private keys,
  certificates, or other secrets.
- Never commit `.env` files containing secrets.
- Database credentials must be externalized.
- Do not add `node_modules`, Maven `target` directories, IDE metadata, OS files,
  or generated build output to Git.
- Preserve `.gitignore` protections.

## Testing

- Add tests for new functionality.
- Run relevant tests and builds after changes. For example:

  ```bash
  mvn -pl <module> -am test
  npm --prefix trade-ui run build
  ```

- Do not claim tests pass unless they were actually executed. Report any check
  that could not run and why.

## Git

- Do not commit or push automatically.
- Do not rewrite Git history unless explicitly requested.
- Show or summarize changes before committing.

## Change process

Before implementing a significant feature:

1. Inspect the relevant modules and existing implementation.
2. Identify the files that need to change.
3. Explain the proposed approach.
4. Wait for approval when the change is architectural or potentially breaking.
5. Implement the smallest appropriate change.
6. Run relevant tests and builds.
7. Report changed files and verification results.

## Retaining project knowledge

The user has requested that learning be retained every time we work on this app.

- At the start of each task, read `docs/PROJECT_UNDERSTANDING.md`, the relevant
  linked appendices, and recent entries in
  `docs/project-understanding/LEARNING_LOG.md`. Consult this knowledge before
  asking questions whose answers may already be documented or in the repository.
- Check the current checkout, branch, commit and existing changes. Treat dated
  findings as a starting point; verify relevant source and runtime assumptions
  before relying on them for a change.
- Before finishing each task, retain material new findings, user decisions,
  business rules, contract changes, troubleshooting causes and fixes, verification
  results, and unresolved gaps. Update the relevant report sections and append a
  concise dated entry to `LEARNING_LOG.md` with links to the affected evidence.
  Do not add repetitive entries when nothing new was learned.
- Cite concrete source files and symbols, and distinguish code-, test- and
  runtime-verified findings from inferences and unresolved questions. Record the
  investigated commit and any relevant uncommitted changes.
- Correct superseded descriptions while preserving the dates and scope of
  historical test results and schema snapshots. Never present an old successful
  check as verification of the current checkout or running service.
- Keep repository documentation as the detailed handoff and persistent assistant
  memory as a concise pointer to it. The user's standing request authorizes
  retaining app-related learning; follow the memory system's update mechanism.
- Never retain credentials, tokens, private records or personal financial data
  in these notes. This maintenance instruction does not authorize commits,
  pushes, deployments, database changes or service restarts.
