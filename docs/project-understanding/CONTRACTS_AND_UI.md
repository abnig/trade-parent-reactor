# REST contracts and UI integration

[Main report](../PROJECT_UNDERSTANDING.md). **C**, with representative contracts also **T** through current tests; no live application/browser responses were captured. Commit `70b9f2c2e8621ed396019c6378c1a61b019cf701`. All examples use synthetic data. The matrix accounts for41 authored controller method/route pairs plus login, logout and health (44 total, excluding automatic framework handlers). No OpenAPI document is generated or checked in; Java/controller/DTO and API tests are the contract evidence.

## Shared HTTP rules and JSON shapes

All portfolio/analytics/profile operations require a session. Every unsafe request needs CSRF, including public authentication/recovery actions. Browser helper first GETs `/api/auth/csrf`, then sends its returned headerName/token with same-origin credentials. No bearer token/localStorage credentials. JSON requests except form-encoded login and multipart upload. All fetches use cache:no-store;204 becomes null; non-2xx throws Error(message) with status, logs method/URL/status/message, never request body. A failed CSRF fetch prevents the write.401 data/profile errors dispatch session-expired; other auth flows handle their own failures. **FieldErrors are not retained by api.js**. No timeout, retry or AbortController is installed. [api.js:12](../../trade-ui/src/api/api.js#L12), [SecurityConfiguration.java:28](../../trade-rest/src/main/java/com/trading/security/SecurityConfiguration.java#L28)

Paged endpoints accept page>=0 and size1..100, defaults0/20, reject multiplication overflow. They do not accept a configurable sort. Page beyond end returns200 empty content with last=true; zero records has totalPages0, first/last=true. List/count are separate queries. Other arbitrary query parameters are generally ignored; portfolio and base profile explicitly reject unsupported ones. Positive IDs are annotated for fund/txn/value routes, but broker and analytics history lack equivalent path validation. [PageRequest.java:11](../../trade-repository/src/main/java/com/trading/repository/PageRequest.java#L11), [PagedResponse.java:28](../../trade-rest/src/main/java/com/trading/dto/PagedResponse.java#L28), [AnalyticsController.java:37](../../trade-rest/src/main/java/com/trading/controller/AnalyticsController.java#L37), [UserProfileController.java:25](../../trade-rest/src/main/java/com/trading/profile/UserProfileController.java#L25)

| Shape | Exact properties/types |
|---|---|
| `Page<T>` | content:T[], page:number, size:number, totalElements:number, totalPages:number, first:boolean, last:boolean |
| `Broker` | id:number, brokerName:string, accountId:string, createDate/updateDate:ISO local timestamp; no owner_user_id field |
| `Fund` | mutualFundId:number, brokerAccountId:number, mutualFundName:string, isin/plan/folioNumber:string or null, createDate/updateDate:ISO local timestamp |
| `TxnDto` | mutualFundTxnId,mutualFundId:number; amount/units/avgPrice:number; transactionType:"BUY"/"SELL"; txnDate:"DD-Mon-YYYY"; createDate/updateDate:ISO timestamp; status/exchangeOrderId/remarks/tag/settlementId:string or null |
| `ValueDto` | valId,mutualFundId,totalValue:number; valueAsOfDate:"DD-Mon-YYYY"; no audit timestamps |
| `ValueModel` | Same four fields, but valueAsOfDate:ISO local timestamp in analytics history |
| `Summary` | totalValue:number,totalUnits:number; raw sum, not net holdings |
| `FundInvestmentSummary` | mutualFundId:number,mutualFundName:string,totalInvested:number; raw array over all owned funds |
| `Profile` | userId:number,username,email:string,firstName/lastName/phoneNumber/avatarUrl:string or null,hintQuestion:number or null,hintAnswerSet:boolean |
| `ApiError` | timestamp:UTC instant,status:number,error/message/path:string,fieldErrors:object. Security returns only status/message; portfolio parameter errors only message. Delete404 can have empty body. |

JSON numeric tokens are read as JavaScript Number. API does not guarantee decimal lexical formatting/trailing zeros; persistent scale is separate. Dates supplied as compatible ISO values are converted to calendar midnight on txn/value writes. `createDate`, `updateDate`, request IDs and unknown owner/role properties are not writable account authority. Most legacy request types ignore unknown JSON fields; profile alone explicitly rejects them. [MutualFundTxnDto.java:1](../../trade-rest/src/main/java/com/trading/dto/MutualFundTxnDto.java#L1), [MutualFundValueDto.java:1](../../trade-rest/src/main/java/com/trading/dto/MutualFundValueDto.java#L1), [ProfileUpdateRequest.java:1](../../trade-rest/src/main/java/com/trading/profile/ProfileUpdateRequest.java#L1), [GlobalExceptionHandler.java:1](../../trade-rest/src/main/java/com/trading/exception/GlobalExceptionHandler.java#L1)

## Portfolio integration matrix

In the matrix, `B/F/T/V` mean broker/fund/transaction/value tables; fund/txn/value queries additionally join or predicate the ancestor tables to enforce owner. `CRUD errors` =401 no/invalid session,403 CSRF for authenticated user,400 body/reference/integrity constraints,404 unknown/foreign direct target,500 unexpected failure. A foreign filtered parent returns an empty page/zero summary rather than disclosing existence. Each listed method is an actual route; GET single helpers currently have no screen consumer.

| UI/action → component/handler → api function | HTTP method/route | Controller → repository → tables | Response → UI update; inputs/sort |
|---|---|---|---|
| Brokers load/paging → BrokerAccounts effect → brokerAccounts.all | GET `/api/broker-accounts` | BrokerController.findAll → OwnedBrokerRepository.findAll/count → B |200 Page<Broker> → rows/paging; id ASC |
| No screen → brokerAccounts.get | GET `/api/broker-accounts/{id}` | findById → owned findById → B |200 Broker; CRUD errors |
| Add broker → submit → brokerAccounts.create | POST `/api/broker-accounts` | create → save/generated id/reread → B |201 Broker → hide/reset form, reload page; brokerName/accountId body, no Bean Validation |
| Edit row → submit → brokerAccounts.update | PUT `/api/broker-accounts/{id}` | update → owner UPDATE/reread → B |200 Broker → reset/reload; path id overrides body id |
| Delete → remove → brokerAccounts.remove | DELETE `/api/broker-accounts/{id}` | delete → owned delete → B |204 → reload or preceding page if last row; FK children400;404 empty |
| Funds unfiltered/paging → MutualFunds effect → mutualFunds.all | GET `/api/mutual-funds` | FundController.findAll → OwnedFundRepository.findAll/count → F/B |200 Page<Fund> → rows; id ASC |
| Broker filter → selectBroker/effect → mutualFunds.byBrokerAccount | GET `/api/mutual-funds/broker-account/{brokerAccountId}` | findByBrokerAccountId → scoped list/count → F/B |200 Page<Fund>; reset page0; foreign/unknown empty; positive parent |
| No screen → mutualFunds.get | GET `/api/mutual-funds/{id}` | findById → owned lookup → F/B |200 Fund;404 inaccessible |
| Add fund → submit/fundPayload → mutualFunds.create | POST `/api/mutual-funds` | create → requireBrokerAccount → save → F/B |201 Fund → reset/reload; positive brokerAccountId, NotBlank mutualFundName; optional three metadata strings |
| Edit fund → submit → mutualFunds.update | PUT `/api/mutual-funds/{id}` | update → validator → scoped UPDATE/reread → F/B |200 Fund → reset/reload; null metadata preserves, empty clears |
| Delete fund → remove → mutualFunds.remove | DELETE `/api/mutual-funds/{id}` | delete → owned delete → F/B |204; child rows reject400;404 inaccessible; reset page if necessary |
| Transaction overview + dropdown → MutualFundTransactions effect → transactions.fundInvestments | GET `/api/mutual-fund-txns/summary/by-fund` | getFundInvestments → OwnedTxnRepository.getFundInvestments → F/B/T |200 FundInvestmentSummary[] → overview/grand net total and selector; name/id ASC; all funds including no transactions |
| Selected fund → effect → transactions.byFund | GET `/api/mutual-fund-txns/mutual-fund/{mutualFundId}` | findByMutualFundId → scoped list/count → T/F/B |200 Page<TxnDto> → rows; txn timestamp DESC,id ASC; page/size |
| Selected summary → effect → transactions.summary | GET `/api/mutual-fund-txns/summary` | getSummary → raw SUM → T/F/B |200 Summary → amount/units summary; optional mutualFundId>=0, null/0 all; foreign zero |
| No management screen → transactions.all | GET `/api/mutual-fund-txns` | findAll → scoped list/count → T/F/B |200 Page<TxnDto>, id ASC; default screen instead loads fund overview |
| No screen → transactions.get | GET `/api/mutual-fund-txns/{id}` | findById → owned lookup → T/F/B |200 TxnDto |
| Add single → submit → transactions.create | POST `/api/mutual-fund-txns` | create → DTO→validator→save → T/F/B |201 TxnDto → reset/reload data and summaries; required fund/amount/units/avgPrice/date/BUY-or-SELL, optional metadata |
| Edit row → submit → transactions.update | PUT `/api/mutual-fund-txns/{id}` | update → DTO/path id/validator→UPDATE → T/F/B |200 TxnDto → reload and summaries; ordinary full required body, not PATCH; null metadata preserves |
| Delete → remove → transactions.remove | DELETE `/api/mutual-fund-txns/{id}` | delete → scoped delete → T/F/B |204 → page/reload/summary; linked order FK400 |
| CSV select → Coin form → transactions.upload | POST `/api/mutual-fund-txns/zerodha-upload` | Session owner + file/options → isolated Coin job | 9 Oct:200 COMPLETED/counts and row/total refresh; file-only requests retain202 staging. Auth/CSRF,10MB, explicit dates/account/posting; safe field errors |
| Values overview → MutualFundValues effect → values.latestByFund | GET `/api/analytics/funds`, then GET `/api/mutual-fund-values/mutual-fund/{id}?page=0&size=1` per fund | AnalyticsController + ValueController → two repositories → F/B/V |All or rejection via Promise.all → latest rows and known-value grand total; N+1 unbounded parallel requests |
| Selected values/paging → effect → values.byFund | GET `/api/mutual-fund-values/mutual-fund/{mutualFundId}` | findByMutualFundId → OwnedValueRepository list/count → V/F/B |200 Page<ValueDto>; full timestamp DESC,valId ASC; foreign empty |
| No screen → values.all | GET `/api/mutual-fund-values` | findAll → owned list/count → V/F/B |200 Page<ValueDto>, id ASC |
| No screen → values.get | GET `/api/mutual-fund-values/{id}` | findById → scoped lookup → V/F/B |200 ValueDto |
| Add snapshot → handleSubmit → values.create | POST `/api/mutual-fund-values` | create → DTO→requireFund→save → V/F/B |201 ValueDto → reset/reload; required positive fund,nonnegative totalValue,valid date |
| Edit snapshot → handleSubmit → values.update | PUT `/api/mutual-fund-values/{id}` | update → DTO/path/parent→UPDATE → V/F/B |200 ValueDto → reset/reload |
| Delete snapshot → handleDelete → values.remove | DELETE `/api/mutual-fund-values/{id}` | delete → owned delete → V/F/B |204/404 → reload or previous page |
| Analytics dropdown (also values overview) → effect → analytics.funds | GET `/api/analytics/funds` | funds → JdbcAnalyticsRepository.findFunds → F/B |200 Fund[] complete/no paging, fund name then id ASC; selected metadata fields not populated; no owner query override |
| Fund analytics → selection effect → analytics.valueHistory | GET `/api/analytics/funds/{fundId}/values` | values → findValueHistory(userId,fundId) → V/F/B |200 ValueModel[] complete timestamp ASC,id ASC → chart/helper inputs; foreign/unknown empty |
| Same selection → analytics.transactionHistory | Repeated GET `/api/mutual-fund-txns/mutual-fund/{fundId}?page=n&size=100` | TxnController → OwnedTxnRepository → T/F/B |Sequential pages until totalPages → complete array; any page failure rejects entire history |
| All funds/date/retry → PortfolioAnalytics effect → analytics.portfolio | GET `/api/analytics/portfolio?fromDate=YYYY-MM-DD&toDate=YYYY-MM-DD` | portfolio → JdbcAnalyticsRepository.findPortfolio → B/F/T/V |200 PortfolioAnalytics → totals/comparison/meters; optional blank dates omitted; invalid/reversed/unknown query400; single owner aggregate |

Controller evidence: [MutualFundBrokerAccountController.java:1](../../trade-rest/src/main/java/com/trading/controller/MutualFundBrokerAccountController.java#L1), [MutualFundController.java:1](../../trade-rest/src/main/java/com/trading/controller/MutualFundController.java#L1), [MutualFundTxnController.java:1](../../trade-rest/src/main/java/com/trading/controller/MutualFundTxnController.java#L1), [MutualFundValueController.java:1](../../trade-rest/src/main/java/com/trading/controller/MutualFundValueController.java#L1), [AnalyticsController.java:1](../../trade-rest/src/main/java/com/trading/controller/AnalyticsController.java#L1). Caller evidence: [BrokerAccounts.jsx:42](../../trade-ui/src/components/BrokerAccounts.jsx#L42), [MutualFunds.jsx:53](../../trade-ui/src/components/MutualFunds.jsx#L53), [MutualFundTransactions.jsx:92](../../trade-ui/src/components/MutualFundTransactions.jsx#L92), [MutualFundValues.jsx:50](../../trade-ui/src/components/MutualFundValues.jsx#L50), [Analytics.jsx:1](../../trade-ui/src/components/Analytics.jsx#L1), [PortfolioAnalytics.jsx:1](../../trade-ui/src/components/PortfolioAnalytics.jsx#L1), [api.js:1](../../trade-ui/src/api/api.js#L1).

## Authentication/profile/recovery integration matrix

| UI/action → handler → api function | Route | Backend/data side effects | Response/failures and UI result |
|---|---|---|---|
| Every unsafe API action → request helper | GET `/api/auth/csrf` | SessionController.csrf → deferred CSRF token/session |200 `{token,headerName}`, public; token fetched fresh before mutation |
| Startup/focus/60s/cross-tab/login callback → App effect/loggedIn → auth.me | GET `/api/auth/me` | SessionController.me + credential filter → users/version |200 `{id,username,roles}` → protected views;401 → guest; transient error banner |
| Login `/login` → Login.submit → auth.login | POST `/api/auth/login` | Security filter/UserDetails/JdbcLoginAccountRepository → users+roles read/BCrypt/session |Form username/password;204 → loggedIn refetches me and dashboard;401 generic; CSRF required |
| Header logout → App.logout → auth.logout | POST `/api/auth/logout` | Security logout invalidates in-memory session/deletes cookie |204 → clear user/tab data/broadcast; failure shown; guarded double-submit |
| Register `/register` → Register.submit → auth.register | POST `/api/auth/register` | RegistrationController/service/repository → users/details/user_roles; optional recovery answers |201 `{id,username,email,roles}` → success/login link, not auto-login;400 invalid,409 duplicate |
| Profile mount → Profile effect → profile.get | GET `/api/auth/profile` | Controller/service/JdbcUserProfileRepository → users/details |200 Profile → form; query params400;401 session clear |
| Profile mount → same effect → profile.questions | GET `/api/auth/profile/hint-questions` | Static ProfileHintQuestion enum |200 `[{id,text},...]`, requires login, no DB |
| Profile save → submit/profileUpdatePayload → profile.update | PUT `/api/auth/profile` | Service locked transaction → users/details/recovery tables on email change |200 safe Profile → local saved form/status;400 validation,403 wrong currentPassword,409 duplicate,401 stale credentials |
| No UI/helper | GET `/api/auth/recovery/questions` | Static recovery service catalog |200 3×`{id,text}`, public, no DB |
| No UI/helper | PUT `/api/auth/recovery/answers` | Service currentPassword → row-locked repo replaceanswers/bump recovery_version/clear pending recovery |204; 3 unique answers/currentPassword;400 invalid,429 rate,401 no session |
| `/reset-password` initial step → ResetPassword.submit → auth.startPasswordReset | POST `/api/auth/password-reset/challenges` | Service rate/lookup/answers/dummy work → challenge row |200 `{challengeId,questions:[two],expiresInSeconds:600}` → answers form;400 invalid,429,503 mail config |
| Answer step → submit → auth.verifyPasswordReset | POST `/api/auth/password-reset/verify` | Service/repo attempts/issue token → SMTP stored address |202 `{message}` generic whether wrong/unknown; UI mail message;400 malformed,429,503 unavailable/send error |
| Mail fragment → reset form → submit → auth.completePasswordReset | POST `/api/auth/password-reset/complete` | Repo token consume/password/version invalidation |204 → login link;400 invalid/expired/replayed token/password;429 rate |
| `/forgot-username` → ForgotUsername.submit → auth.forgotUsername | POST `/api/auth/forgot-username` | Rate + case-insensitive email lookup → SMTP stored eligible account |202 genericmessage → success; invalid400,429,missing config503; actual send errors concealed |
| Production startup readiness, no screen | GET `/actuator/health` | Boot Actuator health including configured contributors |Public, health status; Node awaits2xx before listening; exact deployed response unverified |

Evidence: [SessionController.java:1](../../trade-rest/src/main/java/com/trading/controller/SessionController.java#L1), [SecurityConfiguration.java:1](../../trade-rest/src/main/java/com/trading/security/SecurityConfiguration.java#L1), [RegistrationController.java:1](../../trade-rest/src/main/java/com/trading/controller/RegistrationController.java#L1), [RegisterRequest.java:1](../../trade-rest/src/main/java/com/trading/dto/RegisterRequest.java#L1), [UserProfileController.java:1](../../trade-rest/src/main/java/com/trading/profile/UserProfileController.java#L1), [ProfileUpdateRequest.java:1](../../trade-rest/src/main/java/com/trading/profile/ProfileUpdateRequest.java#L1), [PasswordRecoveryController.java:1](../../trade-rest/src/main/java/com/trading/recovery/PasswordRecoveryController.java#L1), [RecoveryRequests.java:1](../../trade-rest/src/main/java/com/trading/recovery/RecoveryRequests.java#L1), [App.jsx:1](../../trade-ui/src/App.jsx#L1), [Login.jsx:1](../../trade-ui/src/components/Login.jsx#L1), [Register.jsx:1](../../trade-ui/src/components/Register.jsx#L1), [Profile.jsx:1](../../trade-ui/src/components/Profile.jsx#L1), [ResetPassword.jsx:1](../../trade-ui/src/components/ResetPassword.jsx#L1), [ForgotUsername.jsx:1](../../trade-ui/src/components/ForgotUsername.jsx#L1). Framework `/actuator` discovery and `/error` handling are not custom business endpoints; no additional exposed business controllers exist. Automatic HTTP HEAD/OPTIONS handling is framework behavior, not separate authored workflows.

Registration fields: username ASCII letters/digits/underscore/dot/hyphen length1..50; email valid max100; password12..72 characters **and <=72 UTF-8 bytes**; names50,phone20,avatar255 optional; optional recoveryAnswers exactly3 unique question IDs1..3 with nonblank answer<=256. Register UI sends username/email/password/names/phone, excludes recoveryAnswers/avatar and removes confirmPassword. Roles/account flags in extra body fields cannot elevate registration.

Profile fields: email required/max100; optional names50,phone20,avatar255; phone optional leading+ and accepted formatting but7..15 digits; avatar absolute HTTP(S) host, no userinfo. Sensitive changes require currentPassword. hintQuestion1..3/hintAnswer<=256 supplied together, answer nonblank; absent preserves existing. Email/hint update authentication is after row lock and credential-version check. Ordinary omitted optional fields clear. [UserProfileService.java:30](../../trade-rest/src/main/java/com/trading/profile/UserProfileService.java#L30)

Recovery fields: username max50 same registration pattern; challengeId/token `[A-Za-z0-9_-]{43}`; verification exactly2 distinct requested answer IDs; enrollment exactly3; complete newPassword12..72 chars/72 UTF-8 bytes. Forgot email max254, which is wider than stored registration/profile max100. Response examples are generic and do not expose account existence. No username change endpoint exists.

## Representative wire examples

After an authenticated session and fresh CSRF header, the following create chain shows external string identifiers and exact metadata preservation:

```http
POST /api/broker-accounts
Content-Type: application/json

{"brokerName":"Example Broker","accountId":"000071"}
```

```json
{"id":41,"brokerName":"Example Broker","accountId":"000071","createDate":"2026-10-01T10:00:00","updateDate":"2026-10-01T10:00:00"}
```

```http
POST /api/mutual-funds

{"brokerAccountId":41,"mutualFundName":"Example Index Growth","isin":"N/A","plan":" Direct Growth ","folioNumber":"00001234/05"}
```

```http
POST /api/mutual-fund-txns

{"mutualFundId":51,"amount":123.45,"units":1.234567,"avgPrice":100.000001,"transactionType":"BUY","txnDate":"01-Oct-2026","status":"SOURCE_STATUS","exchangeOrderId":"000099","settlementId":"000007","remarks":" processing ","tag":"  {\"tag\":[\"example\"]}  "}
```

The txn response is201 with generated mutualFundTxnId plus the DTO properties above. The server does not require amount to equal units×avgPrice, and all transaction statuses count. An ordinary update must repeat required numeric/direction/date/parent fields; omitting metadata preserves it. Fund `{"isin":""}` in an otherwise valid PUT clears to NULL, whereas txn `{"tag":""}` stores empty text.

```http
POST /api/mutual-fund-values

{"mutualFundId":51,"totalValue":135.00,"valueAsOfDate":"2026-10-02T18:30:00+05:30"}
```

Response201 has `valueAsOfDate:"02-Oct-2026"`; persistence is `2026-10-02 00:00:00`. Analytics history returns that timestamp as an ISO string. No timezone conversion to UTC is performed.

```json
{"content":[],"page":0,"size":20,"totalElements":0,"totalPages":0,"first":true,"last":true}
```

```json
{"timestamp":"2026-10-07T00:00:00Z","status":400,"error":"Validation failed","message":"Request validation failed.","path":"/api/mutual-fund-txns","fieldErrors":{"amount":"Amount must not be negative"}}
```

The error above illustrates ApiError shape; browser currently shows its message, not the fieldErrors. Error title depends on the specific handler.

Synthetic profile PUT: `{"email":"alex@example.invalid","firstName":"Alex","lastName":null,"phoneNumber":"+91 90000 00000","avatarUrl":null}`. Adding changed email/hint requires a synthetic currentPassword of allowed length; a hint example is `{"hintQuestion":1,"hintAnswer":"synthetic response"}` merged into that body. No userId/username/role is accepted by strict profile JSON.

Synthetic registration: `{"username":"example_user","email":"alex@example.invalid","password":"synthetic-only-password","firstName":"Alex"}`. Optional API recoveryAnswers may be `[{"questionId":1,"answer":"synthetic one"},{"questionId":2,"answer":"synthetic two"},{"questionId":3,"answer":"synthetic three"}]`; these do not represent a real account.

Synthetic reset request: `{"username":"example_user"}` → challenge contains43-character opaque ID and2 questions; verification `{"challengeId":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA","answers":[{"questionId":1,"answer":"synthetic one"},{"questionId":3,"answer":"synthetic three"}]}` → generic202; completion `{"token":"BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB","newPassword":"synthetic-new-password"}` requires a real issued token to succeed. Raw IDs shown here are placeholders with valid shape, not usable credentials.

File-only upload returns `{"uploadId":"synthetic-uuid","originalFilename":"orders.csv","size":321,"status":"UPLOADED"}` with202 and no import counts. The 9 October options-based upload returns200/COMPLETED with `importResult` counts and reports validation errors; see the [Coin contract](#coin-upload-contract--9-october-2026). Neither path exposes a polling URL.

Portfolio example for one valued fund:

```json
{
  "asOfDate":"2026-10-02","totalValue":135.00,"knownValueTotal":135.00,
  "totalInvested":123.45,"totalBought":123.45,"gainLoss":11.55,
  "returnPercentage":9.356015,"fundCount":1,"valuedFundCount":1,
  "funds":[{"mutualFundId":51,"mutualFundName":"Example Index Growth","brokerAccountId":41,
    "brokerName":"Example Broker","accountId":"000071","totalInvested":123.45,"totalBought":123.45,
    "totalValue":135.00,"valueAsOfDate":"2026-10-02","lastTransactionDate":"2026-10-01",
    "gainLoss":11.55,"returnPercentage":9.356015,"allocationPercentage":100.000000,"contributionPercentage":100.000000}],
  "brokerAccounts":[{"brokerAccountId":41,"brokerName":"Example Broker","accountId":"000071",
    "totalValue":135.00,"knownValueTotal":135.00,"totalInvested":123.45,"fundCount":1,"valuedFundCount":1,"allocationPercentage":100.000000}]
}
```

## Screens, state, refresh and calculation component map

| Screen/component | State and behavior, including incomplete/error cases |
|---|---|
| `main.jsx`, `App` / `AppView` | React root, manual history/popstate paths, local tabs. Initial checking suppresses both data/guest flashes. Session refresh uses generation ref to ignore stale identity responses, focus+60s timer+BroadcastChannel. User-id keyed views reset private state. Tab navigation unmounts/refetches. No Redux/router library/query cache. [main.jsx:1](../../trade-ui/src/main.jsx#L1), [App.jsx:1](../../trade-ui/src/App.jsx#L1) |
| `AuthLinks`, public `/` | Only Register/Login links when guest; home intentionally has no login form/data. Authenticated view shows username profile control/logout/five tabs; path does not override dashboard while logged in. **I:** opening reset link while still authenticated can show dashboard instead of reset form. [AuthLinks.jsx:1](../../trade-ui/src/components/AuthLinks.jsx#L1), [App.jsx:104](../../trade-ui/src/App.jsx#L104) |
| Login/Register/ForgotUsername/ResetPassword | Loading/saving guards, validation and inline error/success text. Password confirmation stays client-only. Reset component reads fragment token once, removes it with history replacement and holds in memory. No enrollment screen. [Login.jsx:5](../../trade-ui/src/components/Login.jsx#L5), [Register.jsx:5](../../trade-ui/src/components/Register.jsx#L5), [ResetPassword.jsx:1](../../trade-ui/src/components/ResetPassword.jsx#L1), [ForgotUsername.jsx:1](../../trade-ui/src/components/ForgotUsername.jsx#L1) |
| BrokerAccounts | Loading/empty table, error banner, local create/edit form, delete confirm, paging. Editing copies row; no fresh GET by ID. CRUD save not disabled while in flight. Effect active flag ignores late load. [BrokerAccounts.jsx:1](../../trade-ui/src/components/BrokerAccounts.jsx#L1) |
| MutualFunds / MutualFundForm | Same CRUD/paging pattern with broker filter. Broker dropdown only first100; unknown label falls back Broker#id. Name/ISIN trimmed, plan/folio raw. Loading/empty/errors; successful mutation resets/reloads. [MutualFunds.jsx:1](../../trade-ui/src/components/MutualFunds.jsx#L1) |
| MutualFundTransactions / FundInvestmentOverview | Default shows all-fund net summary, not transaction rows. Select fund → independent page and gross summary requests. Success refreshes both list and summaries. Positive numeric HTML with units/NAV step.001; metadata controls five text fields. Only Processed/Completed options offered, arbitrary existing status retained as disabled selected option. Details escape raw text. Separate guarded upload produces staged message and no transaction reload. [MutualFundTransactions.jsx:1](../../trade-ui/src/components/MutualFundTransactions.jsx#L1) |
| MutualFundValues / FundValueOverview | Default performs all-fund N+1 latest fetch; any request failure suppresses totals. Missing snapshot displayed separately but omitted from grand sum. Selected fund paged CRUD uses DTO dates. Current-page refresh only; leaving/reentering overview reloads. [MutualFundValues.jsx:1](../../trade-ui/src/components/MutualFundValues.jsx#L1) |
| Analytics | Loads complete fund selector; selection effect loads values and all transaction pages, clears old histories and rejects incomplete load. Range filters client-side for one fund, server-side portfolio; date bounds preserved across drilldown. No live market refresh. Value chart custom SVG, horizontal scroll, spaced dates/tooltips. [Analytics.jsx:1](../../trade-ui/src/components/Analytics.jsx#L1) |
| AnalyticsResults / AnalyticsViewSelector | Five views: value, cash, holdings, transactions, returns. Missing selection/loading/error/invalid range gates hide metric/chart content; no fabricated zero data while loading. [AnalyticsDetails.jsx:11](../../trade-ui/src/components/AnalyticsDetails.jsx#L11) |
| AnalyticsSummary / analyticsMetrics | Summary cards separate cumulative/opening investment from in-range activity, latest valid snapshot/date, holdings reliability and later-transaction notice; no new request. [AnalyticsSummary.jsx:1](../../trade-ui/src/components/AnalyticsSummary.jsx#L1), [analyticsMetrics.js:1](../../trade-ui/src/components/analyticsMetrics.js#L1) |
| CashFlowView, HoldingsView, TransactionActivityView / analyticsActivity | Monthly/quarterly flows, holdings table/charts, newest/highest-ID transaction table client-paged20. No independent endpoint; all consume selected-fund loaded history. [AnalyticsDetails.jsx:1](../../trade-ui/src/components/AnalyticsDetails.jsx#L1), [analyticsActivity.js:1](../../trade-ui/src/components/analyticsActivity.js#L1) |
| ActivityCharts / holdingsBalances / investmentHistory | Custom SVG signed bars and line gaps, moving weighted cost, carried opening balances; SELL omitted from legend when no sales. Exact computation rules in [Workflows](WORKFLOWS.md). [ActivityCharts.jsx:1](../../trade-ui/src/components/ActivityCharts.jsx#L1), [holdingsBalances.js:1](../../trade-ui/src/components/holdingsBalances.js#L1), [investmentHistory.js:1](../../trade-ui/src/components/investmentHistory.js#L1) |
| AdvancedReturns / advancedReturnMetrics | Covered dates, XIRR/TWR/drawdown/volatility and unavailable reasons, accessible tables; no server call beyond histories. [AdvancedReturns.jsx:1](../../trade-ui/src/components/AdvancedReturns.jsx#L1), [advancedReturnMetrics.js:1](../../trade-ui/src/components/advancedReturnMetrics.js#L1) |
| PortfolioAnalytics / PortfolioAnalyticsView / PortfolioShare | Aggregate effect by range/retry, stale load ignored; loading/error/empty gating, known vs complete totals, per-fund/broker meters only where valid, drilldown returns to fund selection; percentages2decimals, backend6. [PortfolioAnalytics.jsx:1](../../trade-ui/src/components/PortfolioAnalytics.jsx#L1) |
| Profile / ProfileForm | Parallel current profile + hint catalog; immutable username, optional profile fields, separate change-hint control/current password. Save disables fields, secrets cleared in finally, local saved profile updated; cancel restores source. Stored avatar is not rendered as an image. [Profile.jsx:1](../../trade-ui/src/components/Profile.jsx#L1) |
| Pagination | Page sizes10/20/50/100, previous/next bounds, total count metadata; deletes shift back if last row. [Pagination.jsx:1](../../trade-ui/src/components/Pagination.jsx#L1) |
| DateInput / date utils / chartTicks | Text DD-Mon-YYYY plus native date picker, validity messages, calendar conversions, shared display/range handling; no persisted timezone. Chart tick spacing avoids overlap; styling/media rules separate from data. [DateInput.jsx:1](../../trade-ui/src/components/DateInput.jsx#L1), [date.js:1](../../trade-ui/src/utils/date.js#L1), [chartTicks.js:1](../../trade-ui/src/components/chartTicks.js#L1), [app.css:1](../../trade-ui/src/styles/app.css#L1) |
| Production server | Static MIME/SPA fallback, HTML no-cache, other assets immutable1year, lexical traversal guard, health startup gate, /api proxy;502 upstream failure; no downstream DB access. Loopback proxy tested, not a browser/E2E test. [serve-production.mjs:1](../../trade-ui/scripts/serve-production.mjs#L1) |

No client-side financial data cache persists between sessions, but loaded component histories remain until selection/tab/refresh changes. Active flags prevent late **read** effect updates; requests still consume network/server resources. CRUD submit continuations have no equivalent global generation check and can race if user navigates while saving (**I**). Separate HTTP pages can see concurrent edits; the complete-history helper does not provide a snapshot transaction. There is no browser automation suite in this repository, so actual DOM events, cookie behavior in a browser and visual layouts remain runtime-unverified.

## Unconsumed and partial features

Single GET helpers for all four CRUD resources, transaction/value unfiltered-all helpers, public recovery question catalog and authenticated recovery enrollment are not used by current screens. All four resources do have functioning create/update/delete clients. Orders have no route/client/screen; LedgerBalances has no consumer; AI tables have no consumer; SMTP is not mocked in production but is mocked in tests. No first-party frontend production mock dataset or placeholder API implementation was found. The staged upload message's promised later processing has no corresponding worker. Benchmark/portfolio-wide advanced returns, admin UI/API and user-management workflows are absent, rather than hidden routes.


## Coin upload contract — 9 October 2026

The full route and primary upload button remain unchanged. Multipart `file` alone
retains202 staging. With application/json `options`, the endpoint runs the Coin
job and returns200 only on completion, preserving receipt fields and adding
`importResult` (importId, status, records, inserted, updated, unchanged, posted).
The owner is never read from request data. Period/date/posting/matching choices
are explicit. Errors use the existing ApiError shape, with safe record/field codes
in fieldErrors; see the [runbook](../COIN_BATCH_RUNBOOK.md#rest-and-ui-integration--9-october-2026)
for options, statuses, replay counts and synchronous timeout/retry semantics.

[CoinUploadForm](../../trade-ui/src/components/CoinUploadForm.jsx) collects account,
export period, CSV date convention, posting policy and optional per-record fund
matches, using all pages of owned account/fund choices. Orders-only is initially
visible and selected; no source date format is guessed. Inputs/submission are
disabled during imports, validation diagnostics remain visible for corrections,
and success displays persisted counts. The parent refreshes the selected fund's
transactions, summary and investment overview after an attempt. The API helper
uses a JSON Blob in FormData (browser chooses multipart boundary) and retains
error.fieldErrors without logging file contents or request bodies.

Tests cover multipart/CSRF/payload contracts, form rendering/options validation,
all-page selectors and PostgreSQL-backed HTTP execution. Browser event/layout
validation has not been performed in this task.
