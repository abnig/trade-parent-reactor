# Multi-user implementation and gaps

Source review: `719606a`, 16 November 2026. This replaces the obsolete September
plan comparison. [Architecture](ARCHITECTURE.md) describes current boundaries;
[Workflows](docs/project-understanding/WORKFLOWS.md) describes account flows.

## Implemented

- Username/password login, BCrypt, session logout, CSRF, HttpOnly/SameSite cookies
  and a 30-minute session timeout; public registration assigns ROLE_USER only.
- Four primary request-scoped REST repositories fix the owner from the principal.
  Broker/fund/transaction/value reads, writes, counts and summaries constrain SQL
  by ownership; foreign parent assignment fails. Analytics passes owner explicitly.
- Account eligibility and credential-version checks invalidate stale sessions.
  Password reset, separate recovery-answer enrollment, profile/hint updates and
  email username reminders have transactional/versioned repository support.
- H2 **and PostgreSQL** isolation tests exist. The 16 November build ran
  `SessionAndOwnershipTest` and `PostgresSessionAndOwnershipTest`; see [Verification](docs/project-understanding/VERIFICATION.md).

The broker model contains `ownerUserId`, but REST's owned repository ignores client
ownership for writes; its row mapper currently leaves that response field null.
Foreign filtered parents return empty/zero results rather than revealing existence.

## Remaining gaps

| Area | Current limit / future decision |
|---|---|
| Existing data | V6 leaves ownership nullable; backfill and any NOT NULL transition need a verified mapping and separate migration. October catalog evidence is not current deployment proof. |
| Global data | Legacy trade/ledger imports and MCP reads have no user boundary; do not infer REST protection applies there. |
| Coin | Upload/job persistence is unfinished; ownership, missing-master handling and cross-user regression tests must be part of completion. |
| Admin | No ROLE_ADMIN bootstrap, admin authorization/API/UI or account-management workflow. Role storage alone grants no admin feature. |
| Audit | No actor/action/entity audit trail or general user-modification timestamp; account/profile audit fields do not capture every meaningful change. |
| CRUD consistency | Validation, writes and rereads are separate operations; no common optimistic locking or idempotency. A service layer is a possible future design, not an implemented requirement. |
| Deployment | Credential fallbacks remain; TLS, secure cookies and reset-link origin require deployment configuration. Flyway history/schema reconciliation remains open. |
| Abuse controls | Recovery has persistent rate limits; login throttling and broader API rate limiting are absent. Proxy source-address behavior affects recovery buckets. |
| UI | No recovery-enrollment or admin screen; session propagation is implemented, but live browser/security flows lack browser automation. |

Any admin proposal must define provisioning, allowed actions, immutable ownership,
financial-data edit/delete/export policy, session revocation, auditing and migration
checks before implementation. Public registration must not accept role elevation.
No such feature or database operation is authorized by this documentation cleanup.

Evidence: [OwnedPortfolioConfiguration](trade-rest/src/main/java/com/trading/security/OwnedPortfolioConfiguration.java),
[OwnedBrokerRepository](trade-repository/src/main/java/com/trading/repository/impl/OwnedBrokerRepository.java),
[CredentialVersionFilter](trade-rest/src/main/java/com/trading/security/CredentialVersionFilter.java),
[V6](trade-repository/src/main/resources/db/migration/V6__portfolio_ownership.sql),
[PostgresSessionAndOwnershipTest](trade-rest/src/test/java/com/trading/security/PostgresSessionAndOwnershipTest.java).
