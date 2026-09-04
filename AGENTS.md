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
