# User Profile Management

Authenticated users can read and edit their own profile through the React **My profile** tab or the profile API. The backend derives the user ID exclusively from the Spring Security session principal. Username cannot be changed through this feature.

The profile has one selected hint question and a securely hashed answer. This hint is separate from the existing three-question password-recovery records. No hint-verification, login, or account-recovery mechanism is added by this feature.

## Manual database migration

Flyway remains disabled. Apply [V8__user_profile_hint.sql](trade-repository/src/main/resources/db/migration/V8__user_profile_hint.sql) after V7 and before deploying the profile endpoints.

V8 adds nullable `hint_question_id` and `hint_answer_hash` columns to `user_details`, a constraint requiring both fields to be set together, and a unique index on `LOWER(users.email)`. Existing users initially have no selected profile hint; existing recovery answers are not copied or changed.

Before executing the migration, check for emails that differ only in case:

```sql
SELECT LOWER(email) AS normalized_email, COUNT(*)
FROM users
GROUP BY LOWER(email)
HAVING COUNT(*) > 1;
```

If this returns rows, resolve them with the affected account owners before applying the unique index. The migration does not automatically rename addresses or merge users.

With database connection details supplied through your existing PostgreSQL environment or password file, execute the script atomically:

```bash
psql -v ON_ERROR_STOP=1 --single-transaction \
  -f trade-repository/src/main/resources/db/migration/V8__user_profile_hint.sql
```

Apply once and record its execution with the other manual migrations. Reconcile migration history before enabling Flyway later. No live database migration is performed automatically by this feature's implementation.

## Authentication and CSRF

All profile endpoints, including the hint catalog, require an authenticated session. Reads return `401` for guests. Writes also require the existing CSRF header:

1. Fetch `GET /api/auth/csrf` with session cookies.
2. Read its `headerName` and `token` properties.
3. Include that header and the same cookie in the JSON update request.

The frontend API client performs these steps. If profile requests return `401`, it clears the authenticated UI through the existing session-expired event.

## Read profile

`GET /api/auth/profile`

Example response (`200`):

```json
{
  "userId": 1,
  "username": "alice",
  "email": "alice@example.com",
  "firstName": "Alice",
  "lastName": "Example",
  "phoneNumber": "+91 98765-43210",
  "avatarUrl": "https://example.com/avatar.png",
  "hintQuestion": 2,
  "hintAnswerSet": true
}
```

`hintQuestion` is the approved question's numeric ID, or `null` when unset. `hintAnswerSet` reports only whether an answer is stored. Neither the answer nor its hash appears in GET or PUT responses. Missing optional details are returned as `null`; an account without a `user_details` row can still be read and edited.

Profile GET and PUT do not accept query parameters, including `userId`. There is no profile-by-user-ID endpoint.

## Approved hint questions

`GET /api/auth/profile/hint-questions`

Returns the authenticated UI's question catalog:

```json
[
  {"id": 1, "text": "What was the name of your first school?"},
  {"id": 2, "text": "What was the name of your first pet?"},
  {"id": 3, "text": "In which city did your parents first meet?"}
]
```

## Update profile

`PUT /api/auth/profile`

Example changing email and hint details:

```json
{
  "email": "alice.new@example.com",
  "firstName": "Alice",
  "lastName": "Example",
  "phoneNumber": "+91 98765-43210",
  "avatarUrl": "https://example.com/avatar.png",
  "hintQuestion": 2,
  "hintAnswer": "<new-private-answer>",
  "currentPassword": "<current-password>"
}
```

A successful update returns `200` with the same safe profile representation as GET.

| Field | Rules |
|---|---|
| `email` | Required, nonblank valid email, at most 100 characters. Unique across users regardless of case. |
| `firstName`, `lastName` | Optional, at most 50 characters each. |
| `phoneNumber` | Optional, at most 20 characters; 7–15 digits; optional leading `+`; spaces, parentheses, and hyphens between digits allowed. |
| `avatarUrl` | Optional, at most 255 characters; absolute HTTP or HTTPS URL with a hostname and no embedded credentials. The backend does not fetch the URL. |
| `hintQuestion` | Approved numeric ID: 1, 2, or 3. Supply together with a new `hintAnswer`. |
| `hintAnswer` | Nonblank, at most 256 characters when changing the hint. Supply together with `hintQuestion`. |
| `currentPassword` | Required when changing email or setting/replacing hint details; at most 72 characters and at most 72 UTF-8 bytes for password verification. Not required for ordinary contact-detail edits. |

PUT replaces the ordinary profile fields. Omitted, null, or blank first name, last name, phone number, and avatar URL clear those fields; nonblank values have surrounding whitespace removed. Email is required on every update.

Omit both hint fields, or leave both null, to preserve the saved hint. Setting either hint field requires a valid question and a new nonblank answer, even when selecting the same question. This prevents accidental removal or replacement of a saved answer during unrelated edits. This endpoint does not delete a hint.

Unknown JSON fields are rejected with `400`, including `username`, `userId`, `id`, `roles`, `enabled`, `password`, and `hintAnswerHash`. This strict behavior is local to the profile endpoint and does not change older API request contracts.

## Security and consistency

- Profile SQL always includes the authenticated user ID as a bound parameter.
- Sensitive updates verify the actual login password, never a hint answer.
- Password checks and writes occur in one transaction while holding the user's row lock. A password reset or account-state change cannot race past stale credential confirmation.
- Duplicate-email conflicts return `409`; failed writes roll back email, profile fields, and hint changes together.
- Hint answers are SHA-256 digested before BCrypt hashing with the configured password encoder (currently cost 12). Prehashing supports long UTF-8 answers without BCrypt truncation. Plaintext answers never reach repository SQL.
- The safe response model contains no password or answer hash. Request/credential model string representations are redacted, and error responses do not echo passwords, hint answers, or unsupported input values.
- Changing email invalidates outstanding records from the already-existing recovery feature so links sent to the old mailbox cannot remain usable. It does not initiate email delivery or create recovery credentials.
- The profile hint is stored separately in `user_details`; it does not enroll or update the existing `recovery_answer` table.
- The form never pre-fills a saved hint answer and clears entered password/answer values after save attempts. It does not persist them in browser storage.

## Response codes

| Status | Meaning |
|---|---|
| `200` | Profile read/update or question catalog. |
| `400` | Invalid fields, unsupported input, incomplete hint pair, or missing current password for a sensitive change. |
| `401` | Missing, disabled, expired, or revoked session. |
| `403` | Incorrect current password, or authenticated write missing valid CSRF. |
| `404` | Missing profile or unsupported profile-by-ID route. |
| `409` | Email already belongs to another account, including case-only variants. |

## Testing

```bash
mvn -pl trade-rest -am test
npm --prefix trade-ui test
npm --prefix trade-ui run build
```

The profile suite covers authentication/CSRF, ownership, immutable username, unsupported fields, validation, password confirmation, duplicate email conflicts, transactional rollback, secure hint storage, preserving/replacing hints, absent detail rows, session state, and revocation of old-email reset links. Frontend tests cover visibility, sensitive-field controls, request construction, duplicate errors, and session expiry.

`UserProfileTest` runs against H2. `PostgresUserProfileTest` repeats the same suite against an explicitly supplied disposable PostgreSQL database, executing the real migration SQL and case-insensitive index:

```bash
docker run --detach --rm --name trade-profile-tests \
  -e POSTGRES_DB=profile_test -e POSTGRES_HOST_AUTH_METHOD=trust \
  -p 127.0.0.1::5432 postgres:16-alpine
docker port trade-profile-tests 5432/tcp
# After PostgreSQL is ready, replace <port> with the printed localhost port.
PROFILE_TEST_POSTGRES_URL=jdbc:postgresql://127.0.0.1:<port>/profile_test \
  mvn -pl trade-rest -am test
docker stop trade-profile-tests
```

**The PostgreSQL test class drops and recreates the supplied database's public schema before every test. Use only the disposable `profile_test` database.** It is skipped unless the environment variable matches the localhost URL above. H2 uses an equivalent generated-column index because it does not support PostgreSQL's expression-index syntax. These tests execute migrations directly; they do not validate the separate Flyway-baseline adoption process.
