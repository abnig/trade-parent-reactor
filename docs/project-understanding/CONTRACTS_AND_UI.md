# REST contracts and UI integration

Source baseline: `719606a`, 16 November 2026. These are code-derived contracts;
[Verification](VERIFICATION.md) records executed tests. No live browser/API session
was exercised for this reconciliation. There is no checked-in OpenAPI document.

## Shared HTTP rules

Portfolio, analytics and profile routes require a session. All unsafe requests,
including public login/registration/recovery, require CSRF. The browser obtains
`{token, headerName}` from `/api/auth/csrf` before each mutation and uses same-origin
cookies with `cache: no-store`. Login is form encoded; upload is multipart; other
writes use JSON. The helper returns `null` for 204 and otherwise parses JSON.

[api.js](../../trade-ui/src/api/api.js) retains error `status` and `fieldErrors`, logs
method/URL/status/message without request bodies, and dispatches `session-expired`
on protected data/profile 401 responses. It has no request timeout, retry or abort.
Screens do not all render field-level errors.

Paged collections use `page >= 0`, `size` 1–100, defaults 0/20, and reject offset
overflow. Responses contain `content`, `page`, `size`, `totalElements`, `totalPages`,
`first`, `last`. Beyond-end pages return 200 with empty content. Sort is fixed;
count and list queries are separate. A missing/foreign direct record is 404;
foreign filtered parents yield empty pages or zero summaries. Invalid references
and integrity errors are 400. Security failures are 401 or authenticated-CSRF 403.

## Portfolio routes

Each base below implements GET collection → paged 200, GET `/{id}` → 200,
POST → 201, PUT `/{id}` → 200, DELETE `/{id}` → 204 (404 when absent). PUT requires
ordinary required fields; it is not a PATCH. Ownership comes from the principal.

| Base route / source | Shape and validation |
|---|---|
| [`/api/broker-accounts`](../../trade-rest/src/main/java/com/trading/controller/MutualFundBrokerAccountController.java) | `id`, `brokerName`, `accountId`, `ownerUserId`, `createDate`, `updateDate`. No body Bean Validation; SQL constraints apply. The owned row mapper leaves `ownerUserId` null; supplied owner values never control authorization or persistence. |
| [`/api/mutual-funds`](../../trade-rest/src/main/java/com/trading/controller/MutualFundController.java) | `mutualFundId`, positive `brokerAccountId`, nonblank `mutualFundName`, optional `isin`, `plan`, `folioNumber`, audit timestamps |
| [`/api/mutual-fund-txns`](../../trade-rest/src/main/java/com/trading/controller/MutualFundTxnController.java) | `mutualFundTxnId`, positive `mutualFundId`, nonnegative required `amount`, `units`, `avgPrice`, `transactionType` BUY/SELL, required `txnDate`, audit timestamps; optional `status`, `exchangeOrderId`, `remarks`, `tag`, `settlementId` |
| [`/api/mutual-fund-values`](../../trade-rest/src/main/java/com/trading/controller/MutualFundValueController.java) | `valId`, positive `mutualFundId`, nonnegative required `totalValue`, required `valueAsOfDate`; no audit fields |

IDs/audit timestamps are server controlled. Broker account IDs and fund/transaction
metadata are strings. Fund metadata create null/empty → NULL; update omitted/null
preserves, empty clears. Transaction metadata omitted/null preserves on update;
empty text is stored verbatim. There is no amount = units × price or oversell check.
Every stored transaction counts regardless of status text.

Transaction/value DTOs accept strict English `dd-MMM-uuuu`, ISO date or ISO timestamp,
retain the calendar date and store midnight without timezone conversion. Their
responses use `DD-Mon-YYYY`; analytics value history returns ISO local timestamps.
JSON numbers become JavaScript Number; database scale is a separate constraint.

| Additional method/route | Result / ordering |
|---|---|
| GET `/api/mutual-funds/broker-account/{brokerAccountId}` | Paged funds; ID ascending |
| GET `/api/mutual-fund-txns/mutual-fund/{mutualFundId}` | Paged transactions; timestamp descending, ID ascending |
| GET `/api/mutual-fund-txns/summary?mutualFundId=0` | `{totalValue,totalUnits}` raw sums of both BUY/SELL; null/0 selects all owned funds |
| GET `/api/mutual-fund-txns/summary/by-fund` | Array `{mutualFundId,mutualFundName,totalInvested}`; net BUY minus SELL, includes funds without transactions |
| GET `/api/mutual-fund-values/mutual-fund/{mutualFundId}` | Paged values; full timestamp descending, ID ascending |
| POST `/api/mutual-fund-txns/zerodha-upload` | Authenticated multipart `file`; TODO returning `null`, no import/staging result |
| GET `/api/analytics/funds` | Complete owned fund array, name then ID; metadata subset |
| GET `/api/analytics/funds/{fundId}/values` | Complete value-model array, timestamp then ID ascending; foreign/unknown empty |
| GET `/api/analytics/portfolio?fromDate=YYYY-MM-DD&toDate=YYYY-MM-DD` | Portfolio aggregate; dates optional, invalid/reversed/unsupported query parameters return 400 |

Unfiltered CRUD collections sort by ID ascending. Analytics history has no separate
transaction endpoint: the client fetches all pages of the transaction-by-fund route
with size 100. See [AnalyticsController](../../trade-rest/src/main/java/com/trading/controller/AnalyticsController.java)
and [calculation rules](WORKFLOWS.md#financial-computations-and-provenance).

The portfolio response contains `asOfDate`, `totalValue`, `knownValueTotal`,
`totalInvested`, `totalBought`, `gainLoss`, `returnPercentage`, `fundCount`,
`valuedFundCount`, `funds` and `brokerAccounts`. Per-fund entries include snapshot
and last-transaction dates plus allocation/contribution percentages. Missing
valuations produce incomplete/null totals, with known totals kept separately.
[PortfolioAnalytics](../../trade-model/src/main/java/com/trading/model/result/PortfolioAnalytics.java)
defines exact nested fields; server percentages use six decimal places.

## Authentication, profile and recovery routes

All paths below are under `/api/auth`; public means no authenticated session is
required, not that mutation CSRF is waived.

| Method/path | Access / response |
|---|---|
| GET `/csrf` | Public; `{token,headerName}` |
| POST `/register` | Public; 201 `{id,username,email,roles}`; 400 validation, 409 duplicate; no automatic login |
| POST `/login` | Public; form username/password; 204 success, generic 401 failure |
| POST `/logout` | Session logout; 204, invalidates cookie/session |
| GET `/me` | Session; `{id,username,roles}` |
| GET `/profile` | Session; safe profile; unsupported query parameters rejected |
| PUT `/profile` | Session; updated profile; 400 invalid, 403 wrong current password, 409 duplicate |
| GET `/profile/hint-questions` | Session; static `[{id,text}]` |
| GET `/recovery/questions` | Public; three static questions |
| PUT `/recovery/answers` | Session + current password + three distinct answers; 204; no UI enrollment screen |
| POST `/password-reset/challenges` | Public username; `{challengeId,questions,expiresInSeconds:600}` |
| POST `/password-reset/verify` | Public challenge ID + two answers; generic 202 `{message}` |
| POST `/password-reset/complete` | Public token + new password; 204; invalid/expired/replayed token 400 |
| POST `/forgot-username` | Public email; generic 202 `{message}` |

Recovery may return 429 with `Retry-After: 900`; unavailable mail configuration can
return 503. `/actuator/health` is public for deployment readiness; other actuator
routes require authentication. Sources: [SecurityConfiguration](../../trade-rest/src/main/java/com/trading/security/SecurityConfiguration.java),
[SessionController](../../trade-rest/src/main/java/com/trading/controller/SessionController.java),
[RegistrationController](../../trade-rest/src/main/java/com/trading/controller/RegistrationController.java),
[UserProfileController](../../trade-rest/src/main/java/com/trading/profile/UserProfileController.java),
[PasswordRecoveryController](../../trade-rest/src/main/java/com/trading/recovery/PasswordRecoveryController.java).

Registration username is 1–50 ASCII letters/digits/underscore/dot/hyphen, email
max 100, password 12–72 characters and at most 72 UTF-8 bytes. Optional names are
max 50, phone 20, avatar 255; optional recovery enrollment requires three unique
question IDs 1–3 and nonblank answers up to 256 characters. UI registration does
not collect recovery answers. The reset/reminder flow does not disclose existence.

Safe profile fields are `userId`, `username`, `email`, `firstName`, `lastName`,
`phoneNumber`, `avatarUrl`, `hintQuestion`, `hintAnswerSet`. Email is required;
changed email or hint requires current password. Unknown JSON/query fields are
rejected. Omitted optional basics clear; omitted hints preserve. The single profile
hint is not recovery enrollment. [Workflows](WORKFLOWS.md) explains locks and hashing.

## UI behavior and limitations

| Screen/source | Behavior |
|---|---|
| [App](../../trade-ui/src/App.jsx) | Manual paths/tabs; startup, focus, 60-second and cross-tab session refresh; user-keyed views clear private state |
| [BrokerAccounts](../../trade-ui/src/components/BrokerAccounts.jsx), [MutualFunds](../../trade-ui/src/components/MutualFunds.jsx) | Paged CRUD; fund broker selector fetches first 100 only; UI trims fund name/ISIN, preserves plan/folio text |
| [MutualFundTransactions](../../trade-ui/src/components/MutualFundTransactions.jsx) | All-fund investment overview, then selected-fund transactions and gross summary; CRUD refreshes rows/totals; status choices do not restrict all stored values |
| [CoinUploadForm](../../trade-ui/src/components/CoinUploadForm.jsx) | Retains old options/counts/diagnostics UI and refresh callbacks; backend is incomplete, so no working import is claimed |
| [MutualFundValues](../../trade-ui/src/components/MutualFundValues.jsx) | All-fund latest-value overview uses one request per fund; sums known values; any failed request rejects the overview |
| [Analytics](../../trade-ui/src/components/Analytics.jsx) | Complete selected-fund histories, five views, ranges and portfolio drilldown; unsupported metrics display unavailable |
| [Profile](../../trade-ui/src/components/Profile.jsx) | Profile/hint editing with current-password checks; avatar URL stored but not rendered as an image |
| [ResetPassword](../../trade-ui/src/components/ResetPassword.jsx) | Reads reset token from URL fragment, removes it from address bar and retains it in memory |

Read effects ignore late results but do not cancel requests; ordinary CRUD saves
lack a universal pending/generation guard. Pagination under concurrent changes is
not a snapshot. There are no order/admin screens, recovery enrollment UI, or browser
automation suite. Login/profile, loading, empty and error states are component-tested;
this does not verify live cookies or DOM interactions. The [Coin guide](../COIN_BATCH_RUNBOOK.md)
is authoritative for the current upload mismatch.
