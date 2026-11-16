# Analytics implementation status and backlog

Source baseline: `719606a`, 16 November 2026. The original phased plan is condensed
here; [Workflows](docs/project-understanding/WORKFLOWS.md#financial-computations-and-provenance)
owns the calculation formulas and missing-data rules.

## Implemented views

| Original phase | Current behavior / source |
|---|---|
| 1: Fund summary | Snapshot value/date, cumulative net investment, gain/return, activity counts and reliable units; [analyticsMetrics](trade-ui/src/components/analyticsMetrics.js) |
| 2: Cash, holdings, transactions | Monthly/quarterly flows, carried opening holdings, moving weighted cost, activity table and accessible SVG charts; [AnalyticsDetails](trade-ui/src/components/AnalyticsDetails.jsx), [analyticsActivity](trade-ui/src/components/analyticsActivity.js), [holdingsBalances](trade-ui/src/components/holdingsBalances.js) |
| 3: Portfolio | All-fund/broker totals, valuation completeness, allocation/contribution and fund drilldown; [PortfolioAnalytics](trade-ui/src/components/PortfolioAnalytics.jsx), [JdbcAnalyticsRepository](trade-repository/src/main/java/com/trading/repository/impl/JdbcAnalyticsRepository.java) |
| 4: Returns and risk | Fund XIRR, TWR, drawdown, exact month-end returns and volatility with coverage/unavailable explanations; [AdvancedReturns](trade-ui/src/components/AdvancedReturns.jsx), [advancedReturnMetrics](trade-ui/src/components/advancedReturnMetrics.js) |

The UI loads all pages of selected-fund transactions and complete valuation history.
Portfolio uses one owner-scoped aggregate request. API routes and fields are in
[Contracts](docs/project-understanding/CONTRACTS_AND_UI.md).

## Interpretation rules

- Opening transactions before a selected start date establish balances; the end
  date limits cumulative investment. Snapshots must lie in the selected range.
- Missing/invalid data produces unavailable values, not invented zeros. Portfolio
  total value is incomplete when any fund lacks a snapshot; known totals are separate.
- Status text does not exclude recorded transactions. No NAV, distribution, tax,
  fee or currency conversion is inferred.
- Average holdings cost pools same-day buys before sells and removes sold units
  at moving average cost. It is an estimate, not a tax-lot calculation.
- Advanced returns use the first/last covered snapshots and end-of-day flow timing.
  XIRR refuses ambiguous multiple-sign-change cash flows. TWR requires values on
  every net-flow date; volatility requires consecutive complete calendar months.
- Chart data has accessible table equivalents. Loading/errors hide stale metrics;
  date and later-transaction notices expose comparison limitations.

## Remaining work

Phase 5 benchmark comparison is not implemented. It requires an approved benchmark
source, date alignment, missing-data and cash-flow conventions. Portfolio-wide
advanced returns also need synchronized portfolio valuation history; the current
aggregate endpoint does not provide it. Live prices and automatic valuations are
outside the implemented scope.

Tests cover calculation helpers, SSR/view states, API helpers and H2/PostgreSQL
portfolio queries. There is no browser automation. [Verification](docs/project-understanding/VERIFICATION.md)
records actual run dates; this status page does not claim a new test execution.
