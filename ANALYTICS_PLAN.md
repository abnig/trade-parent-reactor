# Analytics Enhancement Plan

## Objective

Expand the Analytics page from a value-history chart into a useful portfolio
analysis view while preserving the existing fund selector, date filters, and
historical value/investment chart.

The first release should use data already available in the application:

- Mutual fund value snapshots (`totalValue`, `valueAsOfDate`)
- Transactions (`BUY`/`SELL`, amount, units, average price, transaction date)
- Fund and broker-account ownership relationships

## Proposed features

### Phase 1: summary metrics

Add a summary row for the selected fund and date range:

- Cumulative net investment
- Latest total value
- Absolute gain/loss (`value - net investment`)
- Return percentage (`gain/loss / net investment * 100`)
- Total units held, when the transaction data supports a reliable balance
- Total bought, total sold, and transaction count

The opening investment balance must include transactions before the selected
date range. If the range contains no transactions, the last cumulative value
should be carried forward, as the chart now does.

### Phase 2: cash-flow and holdings views

Add switchable views below the summary cards:

- Monthly or quarterly BUY, SELL, and net-flow bars
- Units held over time
- Average cost per unit over time
- A transaction activity table for the selected range

These views should reuse the existing date filters and clearly distinguish a
zero cash flow from missing data.

### Phase 3: portfolio comparison

Add an optional portfolio-level view across all funds owned by the user:

- Total portfolio value
- Total invested amount
- Gain/loss and return by fund
- Allocation by fund and broker account
- Contribution share by fund

The existing fund-level chart remains available when a specific fund is
selected.

### Phase 4: advanced returns and risk

Consider these after the core views are stable:

- Money-weighted return / XIRR
- Time-weighted return
- Peak value and drawdown
- Monthly return and volatility
- Benchmark comparison using an explicitly selected benchmark data source

These metrics need documented assumptions around valuation dates, cash-flow
timing, missing snapshots, and external market data. They should not be
presented as precise until those assumptions are supported by the data model.

## Implementation approach

1. Add pure calculation helpers in `trade-ui/src/components`, with tests for
   empty ranges, opening balances, BUY/SELL flows, zero investment, and dates
   with no value snapshot.
2. Build Phase 1 metrics from the already loaded value and transaction history
   before adding new endpoints.
3. Refactor repeated date and cumulative-balance logic into shared helpers.
4. Add the Phase 2 chart/table components without changing existing API
   contracts.
5. Add portfolio aggregation endpoints in `trade-repository` and
   `trade-rest` only when the frontend needs data that the current fund-level
   endpoints cannot provide efficiently.
6. Keep persistence in repositories using explicit SQL and parameter binding.
   Controllers should expose HTTP concerns only.

## API considerations

The current fund-level endpoints can support Phase 1:

- `GET /api/analytics/funds`
- `GET /api/analytics/funds/{fundId}/values`
- Existing paged mutual-fund transaction endpoint

For Phase 3, prefer one authenticated portfolio analytics endpoint that returns
aggregated, owner-scoped data rather than making the browser issue one request
per fund. A possible response shape is:

```json
{
  "asOfDate": "2026-09-08",
  "totalValue": 125000.00,
  "totalInvested": 100000.00,
  "funds": [
    {
      "mutualFundId": 7,
      "totalValue": 75000.00,
      "totalInvested": 60000.00,
      "units": 120.5
    }
  ]
}
```

The exact response should be agreed with the repository model before
implementation. All results must be scoped to the authenticated owner.

## UX requirements

- Keep the existing mutual-fund and date-range controls.
- Show an explicit empty state when there are no value snapshots.
- Show carried-forward investment separately from activity in the selected
  range when that distinction matters.
- Format currency, percentages, units, and dates consistently with the rest of
  the application.
- Explain that gain/loss is based on the available value snapshots and recorded
  transactions.
- Ensure charts remain readable on narrow screens and provide accessible text
  labels or table alternatives.

## Testing and verification

Frontend tests should cover:

- No transactions in a selected range
- Transactions before, inside, and after the range
- Same-day BUY and SELL ordering
- Zero or negative net investment
- Missing value snapshots
- Multiple funds and fund filtering
- Correct metric rounding and formatting

Run the relevant checks after each phase:

```bash
npm --prefix trade-ui test
npm --prefix trade-ui run build
mvn -pl trade-rest -am test
```

## Suggested delivery order

1. Phase 1 summary metrics and calculation tests
2. Phase 2 cash-flow, holdings, and transaction views
3. Phase 3 portfolio comparison and aggregation API
4. Phase 4 advanced return and risk metrics after data assumptions are agreed

Each phase should be released only after its calculations are verified against
hand-worked examples and its empty/loading/error states are covered.

## Phase 1 calculation conventions

- Summary investment and units include recorded history through the selected
  end date, or all loaded transactions when no end date is selected. BUY/SELL
  activity totals and counts include only the selected range, inclusively.
- Latest value uses the most recent valid snapshot inside the selected range.
  Without a snapshot, investment and activity remain visible while value,
  gain/loss, and return are unavailable.
- Gain/loss subtracts cumulative net investment from that snapshot value. The
  snapshot date is displayed, and a notice identifies comparisons that include
  transactions after the snapshot. This is a simple recorded-data comparison.
- Percentage return is unavailable for zero or negative net investment.
- Units require positive, finite units on every included transaction and no
  negative end-of-day balance. Same-day BUY/SELL order does not affect this
  check. Missing history or unreliable unit records produce an unavailable
  balance; no units are inferred from amounts or average prices.

## Phase 2 calculation conventions

- The view selector preserves value history and adds cash flows, holdings, and
  transaction activity using the same complete fund history and date filters.
- Cash flows can be grouped by calendar month or quarter. BUY adds investment,
  SELL is an outflow, and net flow is BUY minus SELL. Empty periods contain
  zero recorded flow after history has loaded successfully; failed or pending
  requests never display fabricated zero totals. Boundary periods include only
  the selected dates and show their actual included dates in the table.
- Unspecified range endpoints use the earliest/latest recorded transaction or
  valid value snapshot. If only an explicit endpoint exists outside the history,
  the other endpoint is clamped to it. With no dated history or date filters,
  the cash-flow view asks for a date range rather than inventing an extent.
- Holdings show end-of-day balances at transaction dates, snapshot dates, and
  range boundaries. Earlier transactions establish the opening balance; dates
  without activity carry it forward. No value snapshots are required.
- Average cost uses a moving weighted average of recorded BUY amounts and
  units. Sales remove units and their cost at that average; sale proceeds do
  not alter average cost. Same-day purchases are pooled before same-day sales
  because the available calendar dates do not establish intraday order. This
  is a documented estimate, not a tax cost basis.
- A full exit shows zero units and unavailable average cost; a later purchase
  starts a new cost pool. Missing/nonpositive units or a negative historical
  balance make subsequent holdings unavailable. Negative BUY amounts also
  make average cost unavailable. Missing balances are chart gaps, not zeros.
- The transaction table is newest first, with transaction ID breaking same-day
  ties, and paginates the already loaded history. Charts include accessible
  tables with exact formatted values and scroll within their containers.

## Phase 3 implementation and API

`GET /api/analytics/portfolio` accepts optional `fromDate` and `toDate` in
`YYYY-MM-DD` format. Invalid dates, reversed ranges, and other query parameters
(including ownership overrides) return HTTP 400. Authentication is required;
the owner always comes from the authenticated session.

The endpoint returns one aggregate response with `asOfDate`, `totalValue`,
`knownValueTotal`, `totalInvested`, `totalBought`, `gainLoss`,
`returnPercentage`, `fundCount`, `valuedFundCount`, `funds`, and
`brokerAccounts`. Fund entries include their identifiers and names, broker
account, snapshot date, last transaction date, net investment, cumulative BUY
amount, value, gain/loss, return, allocation percentage, and contribution
percentage. Broker entries include account identity, total/known value, net
investment, coverage counts, and allocation percentage. Amounts and percentages
are calculated with BigDecimal; percentages retain six decimal places in the
API and display two in the UI.

- The All funds option is optional; selecting a fund or its View fund button
  returns to the existing fund analytics with the same date filters.
- One owner-scoped SQL statement aggregates transactions per fund and selects
  one latest snapshot per fund before joining them. This avoids duplicate
  totals, requests per fund, and inconsistent reads between portfolio totals.
- Like phase 1, net investment includes all transactions through the end date,
  including opening transactions before the start date. With no end date, it
  includes all recorded transactions. Snapshots must be inside the selected
  range; the latest calendar date and then highest value ID win, matching fund
  analytics. The comparison date is the selected end date or the latest
  included transaction/snapshot date, not a claim that every fund was valued
  on that date. Each fund shows its own snapshot date and later cash-flow date.
- Total value and portfolio gain/loss are unavailable if any owned fund has no
  snapshot in the range. `knownValueTotal` separately sums available snapshots.
  Broker totals follow the same completeness rule within each account. Funds
  without transactions have zero investment; missing snapshots remain null.
- Allocation is fund or broker value divided by the complete portfolio value.
  It is unavailable for missing or negative values and for a nonpositive total.
  Broker accounts and funds are grouped by their IDs, never by display names.
- Contribution share is a fund's cumulative BUY amount divided by cumulative
  BUY amounts across all owned funds. It measures recorded purchase
  contributions, not current value or net investment. It is unavailable when
  total BUY amounts are zero or any fund has negative total BUY amounts.
- Returns use gain/loss divided by positive net investment. Zero or negative
  investment yields an unavailable return. Unknown transaction types fail the
  request instead of being silently treated as zero cash flow.
- A portfolio with no owned funds returns empty lists and zero monetary totals,
  and the UI shows an empty state. Loading/failure states never expose stale
  totals; failures provide a retry control.

The PostgreSQL version of the portfolio tests runs only when
`ANALYTICS_TEST_POSTGRES_URL` points to a disposable local database named
`analytics_test`; it resets that database's public schema before each test.
