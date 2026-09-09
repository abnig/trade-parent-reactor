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
