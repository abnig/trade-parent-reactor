# Multi-User Support Plan

## Objective

Enhance the application to support multiple users with strict data isolation, plus an `ADMIN` role for user management and privileged access.

The core security principle is:

```text
Frontend never decides which user owns a normal request.

JWT/session
     ↓
Spring Security
     ↓
authenticated userId
     ↓
Service
     ↓
Repository query containing ownership constraint
     ↓
PostgreSQL
```

Normal users must only be able to access their own data. Administrative capabilities should be explicit and separated from normal user APIs.

---

## 1. Define the Tenancy and Role Model

- Add `User` as the ownership root.
- Add roles:
  - `USER`
  - `ADMIN`
- Define user lifecycle states such as:
  - active
  - disabled
- Define timestamps such as:
  - created date
  - last modified date
- Decide whether login identity is:
  - username
  - email
  - external identity
- Define ownership as:

```text
User
 └── BrokerAccount
      └── MutualFund
           ├── MutualFundTxn
           └── MutualFundValue
```

- Decide whether trade and ledger data should also become user-owned now or in a later phase.

---

## 2. Introduce Schema Migrations Before Changing Behavior

Add versioned database migration support using Flyway or Liquibase.

Tasks:

- Create an `app_user` table.
- Add role and status fields.
- Add `user_id` to `mutual_fund_broker_account`.
- Add a foreign key from broker account to user.
- Add required indexes.
- Backfill existing data to a bootstrap/default user.
- Make `user_id` non-null after migration and verification.

This should be done before introducing application behavior that depends on user ownership.

---

## 3. Add User and Role Domain Models

Suggested user model:

```text
AppUser
├── id
├── username/email
├── passwordHash
├── role
├── status
├── createdAt
└── updatedAt
```

Suggested role model:

```text
USER
ADMIN
```

A Java enum can be used for the role.

The ownership hierarchy should remain:

```text
User
 └── BrokerAccount
      └── MutualFund
           ├── MutualFundTxn
           └── MutualFundValue
```

---

## 4. Introduce Spring Security

Add authentication and authorization using Spring Security.

Tasks:

- Add Spring Security dependencies.
- Configure authentication.
- Add secure password hashing using BCrypt or Argon2.
- Create an authenticated principal that contains:
  - user ID
  - username/email
  - role
- Decide between:
  - session-based authentication
  - JWT-based authentication

JWT is appropriate if the React UI and REST API remain independently deployed. Server-side sessions may be simpler if both are deployed together.

---

## 5. Create Authentication APIs

Minimum initial authentication endpoints:

```text
POST /api/auth/login
GET  /api/auth/me
POST /api/auth/logout
```

Registration does not necessarily need to be public.

Because the system includes an `ADMIN` role, an admin-created-user model may be preferable initially.

The authentication API should never expose password hashes or other sensitive credentials.

---

## 6. Add a Service Layer

Introduce a service layer between REST controllers and repositories.

Target architecture:

```text
Controller
     ↓
Service
     ↓
Repository
```

Example:

```text
MutualFundTxnController
     ↓
MutualFundTxnService
     ↓
MutualFundTxnRepository
```

The service layer should become the primary location for:

- ownership validation
- authorization rules
- business rules
- orchestration
- transaction boundaries where required

This avoids duplicating authorization logic across controllers.

---

## 7. Make Repositories Tenant-Aware

Every repository query that returns user-owned data must be scoped by the authenticated user ID.

Avoid repository APIs such as:

```java
findAll(PageRequest pageRequest)
```

Prefer APIs such as:

```java
findAll(long userId, PageRequest pageRequest)
count(long userId)
```

Similarly:

```java
findById(long userId, long id)
update(long userId, ...)
delete(long userId, long id)
```

Both paginated data queries and their associated `COUNT(*)` queries must apply the same ownership constraints.

---

## 8. Enforce Ownership Through Joins for Child Entities

Child entities such as transactions and fund values should derive user ownership through their parent relationships.

Example transaction query:

```sql
SELECT t.*
FROM mutual_fund_txn t
JOIN mutual_fund mf
  ON mf.mutual_fund_id = t.mutual_fund_id
JOIN mutual_fund_broker_account ba
  ON ba.broker_account_id = mf.broker_account_id
WHERE ba.user_id = ?
```

Apply the same approach to mutual fund values.

The frontend must not send a trusted `userId` to determine ownership.

---

## 9. Secure Single-Item Reads and Writes

Protect all record-specific operations against cross-user access.

A normal user must not be able to access another user's record through guessed IDs.

Examples that must be protected:

```text
GET    /api/mutual-fund-txns/{id}
PUT    /api/mutual-fund-txns/{id}
DELETE /api/mutual-fund-txns/{id}
```

Prefer repository queries that include ownership in the lookup rather than:

1. loading the object by ID
2. checking ownership afterward

For inaccessible records, normal user APIs should generally return `404` rather than revealing that another user's record exists.

---

## 10. Secure the Transaction Summary Endpoint

The transaction summary API must also be user-scoped.

Example:

```text
GET /api/mutual-fund-txns/summary
```

should aggregate only the authenticated user's transactions.

For filtered requests:

```text
GET /api/mutual-fund-txns/summary?mutualFundId=123
```

the backend must ensure that mutual fund `123` belongs to the authenticated user.

Do not accept a normal-user API such as:

```text
?userId=123
```

The authenticated principal must determine ownership.

---

## 11. Implement Explicit ADMIN Capabilities

Keep administrative behavior explicit rather than allowing normal endpoints to silently bypass ownership rules.

Suggested admin namespace:

```text
/api/admin/users
/api/admin/users/{userId}
/api/admin/users/{userId}/broker-accounts
/api/admin/users/{userId}/mutual-funds
/api/admin/users/{userId}/transactions
/api/admin/users/{userId}/values
```

Authorization model:

```text
/api/admin/** → ROLE_ADMIN
/api/**       → authenticated USER or ADMIN
```

Normal APIs should still behave as current-user APIs.

---

## 12. Add Admin User-Management APIs

Initial admin capabilities should include:

- list users
- create user
- view user details
- enable user
- disable user
- change role
- reset password if using local authentication

Prefer disabling users instead of hard-deleting them initially, especially because financial records may need to remain intact.

---

## 13. Define ADMIN Data Rules Explicitly

Decide what administrators are allowed to do.

Questions to define:

- Can an admin read all user data?
- Can an admin modify financial data?
- Can an admin delete financial data?
- Can an admin impersonate users?
- Can an admin export user data?

Recommended initial policy:

- `ADMIN` can manage user accounts.
- `ADMIN` can inspect user data through explicit admin endpoints.
- `ADMIN` does not automatically edit or delete financial data unless that capability is explicitly required.

---

## 14. Update React Authentication

Add authentication support to the React application.

Tasks:

- Add a login page.
- Add authentication/session state.
- Call `/api/auth/me`.
- Add logout.
- Add protected routes.
- Add authorization headers or session handling.
- Handle `401 Unauthorized`.
- Handle `403 Forbidden`.

Existing domain APIs should not require the frontend to send `userId`.

The backend should derive the current user from authentication.

---

## 15. Add ADMIN UI

Keep admin functionality separate from normal user functionality.

Suggested routes:

```text
/admin/users
/admin/users/:id
```

Normal users should not see admin navigation.

An admin user detail screen may show:

```text
User Profile
Broker Accounts
Mutual Funds
Transactions
Values
```

The UI should only expose administrative actions allowed by the backend authorization model.

---

## 16. Handle Migration of Existing Data

Existing application data must be assigned to a valid user before enforcing non-null ownership.

Recommended migration approach:

1. Create a bootstrap user or admin.
2. Assign all existing broker accounts to that user.
3. Verify row counts before and after migration.
4. Verify all ownership relationships.
5. Add the `NOT NULL` constraint only after successful backfill.

Do not enforce `NOT NULL user_id` before existing data has been migrated.

---

## 17. Add Comprehensive Cross-User Isolation Tests

Security tests must explicitly verify isolation between users.

Create test identities:

```text
User A
User B
Admin
```

Test scenarios:

```text
User A lists User A data                PASS
User A lists User B data                BLOCKED
User A reads User B record by ID        BLOCKED
User A updates User B record             BLOCKED
User A deletes User B record             BLOCKED
User A summary excludes User B data      PASS
User A pagination count excludes User B  PASS

Admin accesses /api/admin/**             PASS
User A accesses /api/admin/**            BLOCKED
```

Also test filtered and paginated APIs because counts, summaries, and metadata can leak information even when record contents do not.

---

## 18. Add Security Integration Tests with PostgreSQL

Controller/unit tests alone are not sufficient for tenancy isolation.

Add PostgreSQL-backed integration tests, preferably using Testcontainers.

Verify:

- ownership joins
- repository predicates
- pagination counts
- filtered pagination
- transaction summary aggregation
- foreign-key behavior
- schema migrations
- cross-user record isolation
- admin queries

These tests should prove isolation at the database-query level, not just controller level.

---

## 19. Add Auditing

Once multiple users and administrators exist, important actions should be auditable.

At minimum capture:

```text
authenticated user
action
entity type
entity ID
timestamp
success/failure
```

Admin actions should receive particular attention.

Possible future additions include:

- source IP
- request/correlation ID
- before/after values
- authentication events
- account disable/enable events
- role changes

---

## 20. Harden Production Security

Before considering the feature production-ready:

- remove default database passwords
- restrict CORS
- use HTTPS
- secure token/cookie storage
- configure token/session expiration
- add authentication rate limiting
- ensure passwords and tokens are never logged
- enable CSRF protection where required
- restrict admin endpoints
- reduce excessive debug logging
- validate environment-specific secret management
- review authorization failures and error responses
- ensure disabled users cannot continue accessing APIs
- review session/token invalidation behavior

---

# Recommended Delivery Sequence

Implement this enhancement as a series of smaller, reviewable changes.

## Phase 1 — Database Migrations + User/Role Model

- Introduce Flyway or Liquibase.
- Add `app_user`.
- Add role/status.
- Add user ownership to broker accounts.
- Backfill existing data.
- Add indexes and constraints.

## Phase 2 — Spring Security + Authentication

- Configure Spring Security.
- Implement login.
- Implement authenticated principal.
- Implement `/api/auth/me`.
- Implement logout.
- Add password hashing.
- Establish `USER` and `ADMIN` authorities.

## Phase 3 — Current-User Identity + Service Layer

- Introduce current-user abstraction.
- Add services between controllers and repositories.
- Move ownership and authorization rules into services.
- Establish consistent error behavior.

## Phase 4 — Ownership Enforcement for Broker Accounts + Mutual Funds

- Scope broker-account queries by authenticated user.
- Scope mutual-fund queries by ownership.
- Protect CRUD operations.
- Update pagination/count queries.
- Add isolation tests.

## Phase 5 — Ownership Enforcement for Transactions + Values

- Scope transaction queries.
- Scope mutual-fund values.
- Secure transaction summary.
- Secure filtered endpoints.
- Protect reads/writes by ID.
- Verify pagination metadata cannot leak other users' data.

## Phase 6 — ADMIN APIs and Authorization

- Add `/api/admin/**`.
- Add admin user management.
- Add explicit cross-user inspection APIs.
- Define allowed admin modifications.
- Add role authorization tests.

## Phase 7 — React Authentication + Admin UI

- Add login/logout.
- Protect application routes.
- Handle authentication expiry.
- Add admin navigation.
- Add user-management screens.
- Keep normal user APIs independent of explicit user IDs.

## Phase 8 — Cross-User Isolation + Integration/Security Tests

- Add Testcontainers/PostgreSQL integration tests.
- Verify User A/User B isolation.
- Verify admin capabilities.
- Verify migrations.
- Verify summary/count isolation.
- Review security configuration.

---

# Target Architecture

```text
                     React Application
                            │
                   authenticated request
                            │
                            ▼
                     Spring Security
                            │
                ┌───────────┴───────────┐
                │                       │
           ROLE_USER                ROLE_ADMIN
                │                       │
                ▼                       ▼
          Normal REST APIs          /api/admin/**
                │                       │
                └───────────┬───────────┘
                            ▼
                         Service
                            │
              authenticated user / role
                            │
                            ▼
                        Repository
                            │
                  ownership-aware SQL
                            │
                            ▼
                       PostgreSQL
```

---

# Data Ownership Model

```text
AppUser
   │
   └── 1..* MutualFundBrokerAccount
               │
               └── 1..* MutualFund
                           │
                           ├── 1..* MutualFundTxn
                           └── 1..* MutualFundValue
```

Ownership originates at `AppUser` and is enforced through the parent hierarchy.

Normal frontend requests never supply a trusted user ID.

---

# Definition of Done

The enhancement should not be considered complete until all of the following are true:

- Users authenticate before accessing protected APIs.
- Normal users can only access their own data.
- Record IDs cannot be used to bypass ownership.
- Pagination counts exclude other users' records.
- Summary APIs exclude other users' records.
- ADMIN APIs require `ROLE_ADMIN`.
- Admin cross-user access is explicit.
- Existing data has been migrated safely.
- React handles authentication and authorization.
- Cross-user isolation is covered by automated tests.
- Database-level integration tests validate ownership queries.
- Security configuration is suitable for deployment.
