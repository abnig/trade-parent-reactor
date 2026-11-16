# Workflows and calculation rules

Source baseline: `719606a`, 16 November 2026. HTTP shapes are defined in
[Contracts](CONTRACTS_AND_UI.md); dated test/runtime evidence is in [Verification](VERIFICATION.md).

## Registration, session and authorization

Registration validates credentials, hashes with BCrypt cost 12, then transactionally
creates user/details/ROLE_USER and optional three-answer recovery enrollment.
Duplicate username/email or invalid answers roll back the operation. It does not
log in, send verification mail or accept elevated roles. Passwords must be 12–72
characters and at most 72 UTF-8 bytes.

Login uses username/password and CSRF, returning 204 with a session on success.
`CredentialVersionFilter` checks account eligibility and credential version on
subsequent requests. Logout invalidates the session/cookie. UI focus, 60-second and
BroadcastChannel checks refresh identity; user-keyed views reset private state.
Roles are loaded at login, not refreshed by the version filter. No login rate limit
or admin access path is implemented.

Sources: [RegistrationService](../../trade-rest/src/main/java/com/trading/service/RegistrationService.java),
[SecurityConfiguration](../../trade-rest/src/main/java/com/trading/security/SecurityConfiguration.java),
[CredentialVersionFilter](../../trade-rest/src/main/java/com/trading/security/CredentialVersionFilter.java),
[App](../../trade-ui/src/App.jsx).

## Profile update and separate hint

`UserProfileService` locks the user, rechecks eligibility/version and validates
email, optional phone/avatar and the hint pair. Email or hint changes require the
current password. Optional basic fields are normalized; omitted basics clear and
omitted hints preserve. Email changes increment recovery version and invalidate
pending challenges/tokens. Profile updates are transactional; username/roles are
not writable. The single hint hashes the raw answer with SHA-256 then BCrypt and
is separate from the three normalized recovery answers.

Sources: [UserProfileService](../../trade-rest/src/main/java/com/trading/profile/UserProfileService.java),
[JdbcUserProfileRepository](../../trade-repository/src/main/java/com/trading/repository/impl/JdbcUserProfileRepository.java).

## Recovery and username reminder

1. Enrollment requires the current password and three distinct answers. Normalize
   with NFKC, strip and lowercase, then SHA-256 and BCrypt. Locked/versioned updates
   replace answers and invalidate pending recovery. No enrollment screen exists.
2. Challenge creation uses username, source/account rate limits and usable mail
   configuration. Eligible accounts with three answers get two randomly selected
   questions; other inputs get a dummy challenge with the same response shape.
   A random 32-byte URL-safe ID is returned; only its digest is stored. Expiry is
   10 minutes, with at most five attempts.
3. Correct answers atomically consume the challenge and issue one active reset
   token per account, expiring after 15 minutes. Only its digest is stored. The raw
   token goes to the stored email in a URL fragment. Failed delivery revokes it.
   Wrong/unknown accounts receive generic acceptance; this is not a constant-time claim.
4. Completion atomically consumes a valid token, changes the password and increments
   credential/recovery versions. Existing sessions fail their next version check.
   Concurrent/replayed token use cannot both succeed under the repository contract.

Persistent 15-minute rate limits are source 30, challenge username 5, verification
account 10, enrollment user 5 and reminder email 5. A proxy can make clients share
the same `getRemoteAddr` bucket. Cleanup runs on challenge creation, not a scheduler.
Reminder lookup is case-insensitive, sends to the stored eligible account and returns
a generic response; send failures are hidden. Missing mail configuration can yield 503.

`SmtpResetMailSender` permits HTTPS or HTTP localhost reset URLs, without userinfo,
query or fragment. Production templates use 127.0.0.1 for browser access while reset
profile defaults use localhost: choose a consistent supported origin for deployment.
No email retry queue is implemented.

Sources: [PasswordRecoveryService](../../trade-rest/src/main/java/com/trading/recovery/PasswordRecoveryService.java),
[JdbcPasswordRecoveryRepository](../../trade-repository/src/main/java/com/trading/repository/impl/JdbcPasswordRecoveryRepository.java),
[SmtpResetMailSender](../../trade-rest/src/main/java/com/trading/recovery/SmtpResetMailSender.java).

## Portfolio writes and uploads

Controllers validate inputs/owned parents, call owner-bound SQL, then reread where
needed. Parent validation, write and reread are not one common CRUD transaction.
Missing/foreign targets are 404; invalid references/integrity failures are 400.
Foreign filtered parents return empty/zero results. Database FKs prevent deleting
parents with children or transactions still referenced by retained order rows.
There is no general optimistic locking or duplicate-submission key.

Broker → fund → transaction/value records drive the portfolio. Status is descriptive
and never excludes a transaction from calculations. There is no current order API,
order repository or automatic lifecycle flow. Coin upload is a TODO; use the
[Coin status guide](../COIN_BATCH_RUNBOOK.md), not the historical staging/import flow.

## Financial computations and provenance


Only `mutual_fund_txn` completed rows and `mutual_fund_value` snapshots contribute. Orders never contribute, transaction status never filters inclusion, metadata cannot substitute for units or amount. Currency display assumes INR; schema has no currency column/conversion. No distributions, tax/fees, corporate actions, NAV fetching or benchmark histories are inferred.

| Calculation / layer | Exact rule and missing-data handling |
|---|---|
| Transaction Summary SQL | `COALESCE(SUM(amount),0)`, `COALESCE(SUM(units),0)` over owned selected/all transactions. BUY/SELL both added; totalUnits is not units held. [OwnedTxnRepository.java](../../trade-repository/src/main/java/com/trading/repository/impl/OwnedTxnRepository.java) |
| All-fund investment SQL | BUY amount, SELL negative amount, other type0; LEFT JOIN includes zero-transaction funds. Negative historical SELL values differ from analytics ABS convention. [OwnedTxnRepository.java](../../trade-repository/src/main/java/com/trading/repository/impl/OwnedTxnRepository.java) |
| Portfolio SQL + Java | CTE owner funds, cumulative txn aggregate through end date, latest snapshot in chosen range, then joins; BUY amount, SELL `-ABS(amount)`, invalid type triggers error. fromDate limits snapshots only, retaining opening transactions; toDate inclusive via next-day exclusive bound. Latest calendar DATE then highest valId, not intraday latest. [JdbcAnalyticsRepository.java](../../trade-repository/src/main/java/com/trading/repository/impl/JdbcAnalyticsRepository.java) |
| Portfolio totals | Net investment sums; knownValueTotal sums available snapshots; totalValue/gain/return null unless all funds valued. Empty portfolio counts0 and zero sums, unavailable percentages/date where no comparison date. Broker grouping by ID excludes accounts with no funds. Gain=value−net. Percentage100×numerator/positive denominator, HALF_UP scale6. Allocation requires every value present/nonnegative and positive total. Contribution=BUY/totalBUY only if all fund BUY totals nonnegative and totalpositive. asOfDate explicit toDate else latest included txn/snapshot. |
| `investmentHistory` JS | Validate selected-fund transaction date/amount/direction; cumulative BUY amount−ABS(SELL amount). Includes before-from history, excludes after-to. Value dates/boundaries carry cumulative balance. Invalid selected-fund history throws; unrelated fund invalid records ignored. [investmentHistory.js](../../trade-ui/src/components/investmentHistory.js) |
| `analyticsSummary` / `valueHistory` JS | Discard invalid/missing snapshot values/dates, sort calendar day/id; latest valid inside range. Cumulative investment and reliable units through end; BUY/SELL/count only in selected range; opening net before start. Gain/latest-minus-net and return only positive net. Later transaction warning signals dates differ. [analyticsMetrics.js](../../trade-ui/src/components/analyticsMetrics.js) |
| Holdings / moving cost JS | For each calendar day pool buys before sells: U+=buy units,C+=buy amount; average=C/U; sale reduces U by units and C by soldUnits×average, not by proceeds. Positive finite units required for every record; historical oversell makes subsequent balances unknown. Tolerance1e−9 treats near-zero as exit; zero units with no cost gives average null. Later valid purchase after a valid full exit starts new pool; invalid-history balance does not recover automatically. Negative purchase cost makes average unavailable. [holdingsBalances.js](../../trade-ui/src/components/holdingsBalances.js), [analyticsActivity.js](../../trade-ui/src/components/analyticsActivity.js) |
| Cash flow buckets JS | Calendar month or quarter with zero-flow gaps inside known/requested extent; partial boundary buckets clipped. BUY positive, SELL shown negative ABS, net bought−sold. No known dates/bounds → empty, not invented timeline. [analyticsActivity.js](../../trade-ui/src/components/analyticsActivity.js) |
| Activity table JS | Selected fund/date range, calendar day DESC/id DESC, pages loaded array20. Recorded averagePrice shown as given; not recomputed average cost. [analyticsActivity.js](../../trade-ui/src/components/analyticsActivity.js), [AnalyticsDetails.jsx](../../trade-ui/src/components/AnalyticsDetails.jsx) |
| XIRR JS | First/last valid in-range snapshot delimit covered period; opening snapshot negative, subsequent net contributions negative, final snapshot positive. Exclude opening-day and after-final flows. Net same-day using compensated sum and floating tolerance. Solve `Σ c_i/(1+r)^((day_i−day_0)/365)=0` by160-step bisection in log(1+r), supported rate−0.9999999999..1,000,000. Requires one negative→positive sign change, separate dates, positive opening/nonnegative closing; ambiguous/no roots unsupported. Display annualized percent. [advancedReturnMetrics.js](../../trade-ui/src/components/advancedReturnMetrics.js) |
| TWR JS | Require snapshot on every nonzero net-flow day after opening through closing. Factor=(closing−net contribution on closing day)/opening; product factors−1, not annualized. Positive interval openings/nonnegative values/adjusted close; missing flow-date value disables complete chain. Full sale can end an interval at0 but0 cannot open next one. [advancedReturnMetrics.js](../../trade-ui/src/components/advancedReturnMetrics.js) |
| Drawdown JS | Performance index starts100 and multiplies TWR factors; drawdown=100×(index/runningPeak−1). Maximum most negative, current final. Missing complete chain→null. Observations only at snapshots; no inferred intervening highs/lows. Peak recorded value separately includes cash contributions and earliest date wins tie. |
| Monthly returns/volatility JS | Exact previous calendar month-end/current month-end plus intervening flow-date snapshots; no business-day substitution. Partial months visible but excluded. Sample SD of monthly percentage returns using n−1, >=2 complete consecutive months with no gaps; annualized SD×sqrt12. Missing any complete month invalidates volatility; later supported individual month can still display. |

Synthetic examples covered by test sources (execution dates in [Verification](VERIFICATION.md)): buy10 units for100 then10 for200 →20units/average15; sell5 →15units/average15. Opening100 → snapshot160 afterBUY50 → snapshot136 afterSELL40 produces factors1.1 and1.1, TWR21%, no drawdown. No-flow values100,120,90,108 →max drawdown−25%, current−10%, TWR8%. −100 then+110365days later →XIRR10%; two complete monthly returns+10%/−10% →SD√200=14.14%, annualized√2400=48.99%. [AnalyticsDetails.test.jsx](../../trade-ui/src/components/AnalyticsDetails.test.jsx), [AdvancedReturns.test.jsx](../../trade-ui/src/components/AdvancedReturns.test.jsx), [PortfolioAnalyticsTest.java](../../trade-rest/src/test/java/com/trading/analytics/PortfolioAnalyticsTest.java), [PostgresPortfolioAnalyticsTest.java](../../trade-rest/src/test/java/com/trading/analytics/PostgresPortfolioAnalyticsTest.java)

These implemented estimates are not tax cost basis or a claim of financial standards compliance. Different snapshot dates across funds can make portfolio comparison temporally uneven; UI exposes dates/later-transaction warnings. Complete history paging lacks database snapshot consistency under concurrent changes; no stored derived holdings/returns table exists. See dictionary for decimal/date limits and value-management overview differences.

## Batch processing

The canonical [batch reference](../../trade-batch/ARCHITECTURE.md) owns legacy CSV
schemas, discovery, chunk/skip behavior, deletion flags and runner limitations.
Coin scaffolding is documented in [Coin status](../COIN_BATCH_RUNBOOK.md).

## MCP contracts and independent runtime

The separate WebFlux/ASYNC service is configured for loopback 8081, SSE `/sse` and
messages `/mcp/message`. Tool callbacks come from annotated methods and the symbols
resource from `@McpResource`. The Java contract below has not been validated through
a live MCP handshake. This module has no REST session/owner context or security
filter chain. CORS permits credentialed wildcard-origin patterns; network binding
is its configured boundary. JPA/JDBC calls block; no per-query timeout/retry adapter
is implemented. Hikari is configured max 10/min 5 with 30-second connection/idle limits.

| Entry point | Inputs/schema at Java boundary | Calls/SQL and returned data | Important semantics |
|---|---|---|---|
| `Get-Ledger-Details-By-Date-Range` / getLedgerDetailsByDateRange | date1,date2:LocalDate; expected JSON ISO date strings | LedgerRecordRepository.findBetweenPostingDates, inclusive BETWEEN posting_date; List<LedgerRecord> with UUID, particulars,date,costCenter,voucherType,debit,credit,netBalance,fileName,createDateTime | No pagination/sort/owner/reversed-range validation. Description asks client to interpret particulars; server does no grouping/description parsing. |
| `Average-Buy-Price-and-Count` / getAvgBuy | symbol:String @ToolParam/@NotNull | TradeRecordRepository.findAverageByTradeType(symbol,"buy") → JPQL SUM(price×quantity),SUM(quantity),symbol,tradeType → TradeDetailsResult | avgPrice unset; totalPrice is not an average; total bought quantity is not current position after sales. Unknown symbol may yield no grouped result; envelope behavior unverified. |
| `Average-Sell-Price-and-Count` / getAvgSell | symbol:String | Same JPQL with "sell" | Total sell value/quantity; no explicitly calculated avgPrice. |
| `Average-Buy-Sell-Price-and-Count` / getOverallBuyAvgSellAvg | symbol:String | FastLane NamedParameter JDBC query → List<TradeDetailsResult> | LAG previous type by execution timestamp/symbol, running transition group, MIN/MAX time, SUM quantity, ROUND(AVG(price),2); **unweighted** price. Mapper discards trade count, totalPrice unset; tie timestamps lack deterministic ID order. |
| `All-Valid-Symbols`, URI `symbols` / getAllValidSymbols | Resource read URI symbols, no custom params | JPA distinct symbols → Mono<List<String>> | Global unsorted symbols from trades, not a validated security master or fund list. |

Evidence: [TradeMcpServerTools.java](../../trade-mcp-server/src/main/java/com/trading/mcp/tools/TradeMcpServerTools.java), [TradeMcpServerResources.java](../../trade-mcp-server/src/main/java/com/trading/mcp/resources/TradeMcpServerResources.java), [TradeRecordRepository.java](../../trade-repository/src/main/java/com/trading/repository/impl/TradeRecordRepository.java), [LedgerRecordRepository.java](../../trade-repository/src/main/java/com/trading/repository/impl/LedgerRecordRepository.java), [TradeRecordFastLaneRepository.java](../../trade-repository/src/main/java/com/trading/repository/TradeRecordFastLaneRepository.java), [TradeRecordFastLaneRepositoryImpl.java](../../trade-repository/src/main/java/com/trading/repository/impl/TradeRecordFastLaneRepositoryImpl.java), [TradeDetailsResult.java](../../trade-model/src/main/java/com/trading/model/result/TradeDetailsResult.java).

For synthetic buys 2 @ 10 and 8 @ 20, the JPA tool returns totalPrice 180 and
quantity 10, without populating an average. One fast-lane contiguous group returns
unweighted avgPrice 15, not weighted 18. This difference is SQL semantics. The
7 October synthetic PostgreSQL 18.4 probe accepted its `COUNT(grouped_trades.*)`
syntax; that historical probe does not verify the Java mapper or transport.
Nullable execution timestamps may fail the mapper's unguarded conversion (inference).

The MCP POM names nonexistent `com.trading.mcp.server.TradeMcpServerApplication`;
source declares `com.trading.TradeMcpServerApplication`. The `veersion` property is
misspelled. Resource discovery and packaged startup remain unresolved. No Ollama
call, prompt/RAG workflow or first-party MCP client uses the declared AI dependencies.
