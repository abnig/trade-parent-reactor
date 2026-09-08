# Multi-User Support Gap Analysis

Review date: 8 September 2026

Reference: [Multi-User Support Plan](multi-user-support-plan.md)

## Summary

Core multi-user authentication and mutual-fund data isolation are implemented. The application does not yet meet the full plan's definition of done. The largest remaining areas are administrative functionality, migration of existing data, PostgreSQL integration tests, auditing, and production hardening.

This assessment is based on source-code, configuration, migration, and test inspection. Tests were not executed for this review, and no deployed application or database was inspected. “Implemented” describes the code present, not verified deployment status.

## Comparison with the plan

| Plan section | Status | Findings and remaining work |
|---|---|---|
| 1–3: Tenancy, user model, roles, and schema | Partial | Username login, user tables, account status flags, role mappings, ownership foreign key, and indexes exist. Only `ROLE_USER` is seeded. Controlled admin provisioning and a consistently maintained `users.updated_at`, mapped into the relevant user/profile model, remain. See the completion criteria below. |
| 4–5: Security and authentication APIs | Implemented | Spring Security, BCrypt, session authentication, login, `/api/auth/me`, logout, and credential-safe responses exist. Public registration is also implemented. |
| 6: Service layer | Mostly missing | Registration has a service. Portfolio and analytics controllers call repositories directly. The planned portfolio services for business rules, authorization orchestration, and transaction boundaries remain. |
| 7–9: Tenant-aware repositories and protected CRUD | Implemented for REST mutual-fund data | Broker accounts, funds, transactions, and values use ownership-constrained SQL for reads, writes, and counts. Foreign parent assignments are rejected. Batch/MCP retain existing repositories. |
| 10: Transaction summary | Implemented with a behavior difference | Aggregation is user-scoped. A filter referencing another user's fund returns zero totals rather than explicitly rejecting the inaccessible fund. Decide whether to retain this behavior or require `404`. |
| 11–13: Admin APIs, user management, and data policy | Missing | Admin authorization, user management, explicit cross-user inspection endpoints, and policies for financial modifications, deletion, exports, and impersonation remain. |
| 14: React authentication | Implemented | Login/logout, session checks, protected portfolio rendering, cookie/CSRF handling, `401` session clearing, and error propagation exist. |
| 15: Admin UI | Missing | Role-aware navigation, user list/detail screens, account management, and permitted portfolio inspection remain. |
| 16: Existing-data migration | Incomplete | Ownership backfill, count/relationship verification, and non-null ownership enforcement remain. Manually applied migrations must be reconciled before enabling Flyway. |
| 17: Cross-user isolation tests | Substantially implemented | Tests cover User A/B isolation, CRUD, parent reassignment, summaries, counts, pagination, and analytics. Admin scenarios and disabling an already-authenticated account remain. |
| 18: PostgreSQL integration tests | Missing | Current security/database tests use H2 in PostgreSQL compatibility mode. Real PostgreSQL tests are needed for migrations, constraints, ownership queries, and admin access. |
| 19: Auditing | Missing | Capture actor, action, entity type/ID, timestamp, and success/failure, especially for administrative actions. |
| 20: Production hardening | Partial | CSRF, HttpOnly/SameSite cookies, logout invalidation, and a 30-minute session timeout exist. Credential defaults, secure deployment settings, rate limiting, logging, and active-session revocation remain to be addressed or verified. |

## Detailed outstanding work

### Completion criteria for plan sections 1–3: Admin provisioning and user timestamps

The existing schema and login implementation can support an admin role without renaming tables or replacing the separate login and profile models. Two focused additions remain for this item.

**Controlled admin provisioning**

- Add a new migration that seeds `ROLE_ADMIN` without changing previously applied migrations. Keep public registration restricted to `ROLE_USER`.
- Provide an operator-only command or explicitly enabled bootstrap process to create the first admin or grant the role to an existing, verified user.
- Make provisioning transactional and safe to rerun without duplicate users or role mappings. Externalize any supplied password and hash it with the existing password encoder; do not seed a default password in SQL or source code.
- Document how to provision the first admin and how reruns behave.

[JdbcLoginAccountRepository](trade-repository/src/main/java/com/trading/repository/impl/JdbcLoginAccountRepository.java) already loads assigned roles from the database. Provisioning therefore makes `ROLE_ADMIN` available at login, but does not itself implement admin permissions. Protecting admin operations requires the authorization work in detailed section 2 below; an admin dashboard is not required to complete provisioning itself.

**Consistent modification timestamp and model**

Currently, [V5](trade-repository/src/main/resources/db/migration/V5__create_app_user.sql) defines `users.created_at` and `user_details.updated_at`, but no `users.updated_at`. [JdbcUserProfileRepository](trade-repository/src/main/java/com/trading/repository/impl/JdbcUserProfileRepository.java) refreshes the detail timestamp on profile updates, while password resets in [JdbcPasswordRecoveryRepository](trade-repository/src/main/java/com/trading/repository/impl/JdbcPasswordRecoveryRepository.java) do not. Neither [LoginAccount](trade-model/src/main/java/com/trading/model/LoginAccount.java) nor [UserProfile](trade-model/src/main/java/com/trading/model/UserProfile.java) exposes these timestamps.

- Add a non-null `users.updated_at` with an initial database default through a new migration. Backfill existing rows using a documented policy based on available timestamps, with a fallback for missing values. Exact historical modification times cannot be reconstructed.
- Define this field as the last meaningful account change: profile/email edits, password changes, account-status changes, and role changes. Document whether no-op updates or internal recovery bookkeeping should advance it.
- Maintain the timestamp in the same transaction as each relevant write, including changes to `user_details` and `user_roles`. Initialize it during registration and admin creation.
- Decide whether `user_details.updated_at` remains a separate profile-only timestamp, and document that distinction so callers use the account timestamp consistently.
- Map creation and modification timestamps with a consistent Java type, such as `Instant`, in the appropriate user/profile model and response. Preserve credential-safe responses and existing API compatibility; a single combined user model is unnecessary.

**Verification and definition of done**

- Test initial admin provisioning, reruns, role loading at login, and rejection or ignoring of public registration attempts to assign `ROLE_ADMIN`.
- Test timestamp initialization and advancement for each relevant account change, including password resets and role/status updates when those write paths are implemented.
- Verify fresh-install and populated-database migration paths on PostgreSQL, including timestamp backfill, and run the relevant backend tests.

This item is complete when an operator can reliably provision an admin and account modification time has a documented, consistently implemented meaning in persistence and the relevant model. Broader admin APIs, UI, session revocation, and audit history remain separate work below; a modification timestamp is not an audit trail.

### 1. Complete and reconcile database migration

[V5](trade-repository/src/main/resources/db/migration/V5__create_app_user.sql) creates `users`, `roles`, `user_roles`, and `user_details`. These names differ from the suggested `app_user` table but provide the authentication schema; renaming is not inherently necessary.

[V6](trade-repository/src/main/resources/db/migration/V6__portfolio_ownership.sql) adds nullable `owner_user_id` to broker accounts. It deliberately leaves existing accounts unassigned and invisible to REST users. This preserves isolation but does not fulfill the plan's existing-data migration requirement.

Remaining tasks:

- Define and execute an approved mapping of existing broker accounts to verified users or a bootstrap account.
- Verify row counts and ownership relationships before and after backfill.
- Enforce non-null ownership after verification, coordinating any writers that still create unassigned accounts.
- Reconcile manually applied SQL with Flyway history before enabling managed migrations.
- Resolve the baseline discrepancy: migration documentation describes version `4`, while REST configuration specifies version `1`.
- Verify fresh-install and existing-database upgrade paths using PostgreSQL.

Evidence: [migration README](trade-repository/src/main/resources/db/migration/README.md), [REST configuration](trade-rest/src/main/resources/application.properties), and [baseline profile](trade-rest/src/main/resources/application-flyway-baseline.properties).

### 2. Implement admin authorization and functionality

[SecurityConfiguration](trade-rest/src/main/java/com/trading/security/SecurityConfiguration.java) currently requires authentication for `/api/**`; it does not define an admin-specific role restriction. There are no admin controllers or admin UI screens.

Remaining tasks:

- Provision `ROLE_ADMIN` and define a controlled initial-admin setup process.
- Require `ROLE_ADMIN` for `/api/admin/**`.
- Implement user listing, creation, detail retrieval, enable/disable, role changes, and password reset.
- Add explicit cross-user portfolio inspection APIs.
- Define whether admins may modify/delete financial records, export data, or impersonate users. These are policy decisions, not automatically required permissions.
- Keep normal APIs scoped to the current user even when that user is an admin.
- Build role-aware admin navigation and user-management/detail screens.
- Test both permitted admin access and denial for ordinary users.

Public registration currently assigns `ROLE_USER`. The plan allows registration policy flexibility; choosing admin-created accounts would require a separate policy decision.

### 3. Revoke access when account state changes

[JdbcLoginAccountRepository](trade-repository/src/main/java/com/trading/repository/impl/JdbcLoginAccountRepository.java) loads account flags and roles at authentication time. Subsequent requests use the stored [AccountPrincipal](trade-rest/src/main/java/com/trading/security/AccountPrincipal.java).

The inspected implementation does not recheck database account state on each request or revoke existing sessions when an account is disabled. Consequently, disabling an account after login is not sufficient to immediately stop its existing session from accessing APIs.

Remaining tasks:

- Define and implement session revocation or account-state revalidation.
- Define how password resets and role changes affect existing sessions.
- Add tests for disablement, role removal, and password reset while a session is active.

### 4. Add the planned service layer

[RegistrationService](trade-rest/src/main/java/com/trading/service/RegistrationService.java) exists, but portfolio and analytics controllers invoke repositories directly.

The current ownership mechanism uses [OwnedPortfolioConfiguration](trade-rest/src/main/java/com/trading/security/OwnedPortfolioConfiguration.java) to create request-scoped repositories bound to the authenticated user ID. [OwnedPortfolioJdbc](trade-repository/src/main/java/com/trading/repository/impl/OwnedPortfolioJdbc.java) applies ownership predicates in SQL. This is a valid implementation difference from passing `userId` into every repository method, not evidence of missing REST isolation.

To meet the planned architecture, introduce focused portfolio services and move orchestration, business rules, and relevant transaction boundaries into them while retaining SQL ownership enforcement.

### 5. Add PostgreSQL-backed security integration tests

[SessionAndOwnershipTest](trade-rest/src/test/java/com/trading/security/SessionAndOwnershipTest.java) already covers significant isolation behavior, including lists, individual records, updates/deletes, foreign parent assignments, summaries, counts, and analytics. It uses H2 and adapts PostgreSQL migration syntax. [RegistrationPersistenceTest](trade-rest/src/test/java/com/trading/repository/RegistrationPersistenceTest.java) also uses H2.

Remaining PostgreSQL test coverage should include:

- Fresh migrations and upgrades of populated databases.
- Ownership foreign keys and eventual non-null constraints.
- SQL ownership predicates and child-entity relationships.
- Paginated and filtered counts, summaries, and cross-user CRUD.
- Admin queries and role authorization.
- Account-state changes affecting active sessions.

Testcontainers is the plan's preferred option. Existing H2 coverage should not be treated as proof that PostgreSQL migrations and queries work unchanged.

### 6. Add auditing and complete production hardening

No application audit trail was found. Add records for authenticated actor, action, entity type/ID, timestamp, and outcome, with particular attention to user-management and privileged access.

Existing protections include BCrypt hashing, CSRF, HttpOnly/SameSite cookies, logout session invalidation, and a configured 30-minute session timeout.

Remaining implementation or deployment-verification tasks:

- Remove database password fallbacks from runtime configuration.
- Verify HTTPS deployment and enable secure session cookies; the current secure-cookie setting defaults to false.
- Add authentication rate limiting.
- Review debug logging and verify credentials/tokens are not logged.
- Verify environment-specific secret management.
- Review CORS, particularly MCP's permissive configuration if exposed beyond loopback.
- Verify authorization error behavior and session invalidation policies.

HTTPS and secret-management deployment state were not inspected, so these are verification requirements rather than claims about the running environment.

### 7. Decide the scope of trade/ledger ownership

Implemented isolation covers REST mutual-fund portfolios and their analytics. Trade/ledger data and MCP access have not been made user-scoped. The plan explicitly leaves timing of trade/ledger ownership open.

Document whether these areas are deferred or included in the current delivery. If included, plan their identity propagation and repository ownership changes separately, preserving module boundaries and obtaining approval for MCP contract changes.

## Recommended implementation order

1. Reconcile migrations, map existing data, verify ownership, and complete database constraints. Add controlled admin provisioning and consistent user timestamps as described in the plan sections 1–3 completion criteria.
2. Implement active-session revocation for account disablement and define role/password-change behavior.
3. Define admin policy and implement protected admin APIs with focused services.
4. Add admin UI and authorization tests.
5. Add PostgreSQL integration coverage, starting alongside migration work and extending with admin functionality.
6. Add auditing and complete production configuration/security verification before deployment sign-off.

Resolve trade/ledger scope early so the definition of done is explicit.

## Review limitations

- This document records a read-only source review as of the review date.
- No tests or builds were executed as part of that review.
- No live database backfill, Flyway history, HTTPS setup, or deployed secret configuration was verified.
- Test coverage described above was inspected, not reported as passing.
