import { useMemo } from 'react'
import { advancedReturns } from './advancedReturnMetrics'
import { formatMetric } from './analyticsMetrics'
import { formatDisplayDate } from '../utils/date'

const percent = value => value === null ? 'Unavailable' : `${formatMetric(value)}%`

export default function AdvancedReturns({ transactions, values, fundId, fromDate, toDate }) {
  const data = useMemo(() => advancedReturns(transactions, values, fundId, fromDate, toDate),
    [transactions, values, fundId, fromDate, toDate])
  if (!data.snapshotCount) return <div className="analytics-empty"><h3>No value history available for returns and risk</h3>
    <p>Add dated value snapshots for this fund or adjust the date range.</p></div>
  const cards = [
    ['Money-weighted return (XIRR)', data.xirr, true, 'Annualized over the covered period.'],
    ['Time-weighted return', data.twr, true, 'Cumulative return over the covered period.'],
    ['Peak recorded value', data.peakValue, false, data.peakValue.date ? `Recorded on ${formatDisplayDate(data.peakValue.date)}; includes contributions.` : ''],
    ['Maximum drawdown', data.maxDrawdown, true, 'Largest decline from a prior peak of the cash-flow-adjusted index.'],
    ['Current drawdown', data.currentDrawdown, true, 'Decline from the index peak at the last snapshot.'],
    ['Monthly volatility', data.volatility, true, `Sample standard deviation of ${data.volatilityMonths} available complete monthly returns.`],
    ['Annualized volatility', data.annualizedVolatility, true, 'Monthly volatility × √12.']
  ]
  return <div className="analytics-card">
    <h3>Returns &amp; risk</h3>
    <p className="chart-explanation">Covered period: <strong>{formatDisplayDate(data.startDate)} – {formatDisplayDate(data.endDate)}</strong>
      {' '}({data.snapshotCount} dated snapshots inside the selected range).
      {' '}Returns start at the first available snapshot, not the original purchase. Earlier holdings are represented by that opening value.</p>
    {data.hasLaterTransactions && <p className="portfolio-data-note">Transactions after the last snapshot are excluded from these returns. Add a later valuation to include them.</p>}
    <div className="analytics-metrics"><dl className="analytics-metrics-grid">
      {cards.map(([label, result, percentage, explanation]) => <div key={label}>
        <dt>{label}</dt><dd>{percentage ? percent(result.value) : formatMetric(result.value)}</dd>
        <p>{result.reason || explanation}</p>
      </div>)}
    </dl></div>
    <details className="analytics-return-assumptions">
      <summary>Calculation assumptions and data requirements</summary>
      <p>These are estimates from recorded fund values and BUY/SELL transactions. Each snapshot is assumed to be an end-of-day value after all transactions on that date.
        {' '}Same-day cash flows are netted. Opening-day transactions are already included in the opening snapshot.
        {' '}Unrecorded distributions, fees, taxes, or transactions are not inferred.</p>
      <p>XIRR treats the opening value and subsequent purchases as payments, sales and closing value as receipts, using actual calendar days and a 365-day year.
        {' '}It is unavailable when cash flows change sign more than once, because a unique rate is not guaranteed.
        {' '}Annualizing a short period can produce large rates.</p>
      <p>Time-weighted return links intervals using (closing value − end-date net contribution) / opening value.
        {' '}Every date with a nonzero net cash flow needs a snapshot. Missing snapshots are not interpolated or carried forward.
        {' '}Zero opening values, negative valuations, and invalid purchase amounts make affected intervals unavailable.</p>
      <p>Drawdown uses the cash-flow-adjusted return index, starting at 100, so deposits and withdrawals alone do not count as gains or losses.
        {' '}It only measures declines observed at snapshots and may miss intervening peaks or losses.</p>
      <p>Monthly returns require exact snapshots at the previous and current calendar month end and on cash-flow dates.
        {' '}Partial months are excluded from volatility. Volatility requires at least two consecutive complete monthly returns, with no missing months;
        {' '}it uses the sample standard deviation and is annualized by multiplying by √12. Small samples may be unrepresentative.</p>
    </details>

    {data.monthly.length ? <div className="table-wrap analytics-detail-table" role="region" aria-label="Monthly returns" tabIndex={0}>
      <table><caption>Monthly time-weighted returns</caption>
        <thead><tr><th scope="col">Month</th><th scope="col">Opening valuation date</th><th scope="col">Closing valuation date</th><th scope="col">Return</th><th scope="col">Data coverage</th></tr></thead>
        <tbody>{data.monthly.map(row => <tr key={row.date.getTime()}>
          <th scope="row">{formatDisplayDate(row.date).slice(3)}</th><td>{formatDisplayDate(row.opening)}</td><td>{formatDisplayDate(row.closing)}</td>
          <td>{percent(row.value)}</td><td>{row.reason || 'Complete month'}</td>
        </tr>)}</tbody>
      </table>
    </div> : <p className="analytics-detail-empty">No monthly return periods available.</p>}

    <div className="table-wrap analytics-detail-table" role="region" aria-label="Return index and drawdown" tabIndex={0}>
      <table><caption>Recorded values and cash-flow-adjusted drawdown</caption>
        <thead><tr><th scope="col">Snapshot date</th><th scope="col">Recorded value</th><th scope="col">Return index (starts at 100)</th><th scope="col">Drawdown</th></tr></thead>
        <tbody>{data.observations.map(row => <tr key={row.date.getTime()}>
          <th scope="row">{formatDisplayDate(row.date)}</th><td>{formatMetric(row.value)}</td><td>{formatMetric(row.index)}</td><td>{percent(row.drawdown)}</td>
        </tr>)}</tbody>
      </table>
    </div>
  </div>
}
