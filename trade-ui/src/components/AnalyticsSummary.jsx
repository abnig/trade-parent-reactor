import { formatMetric } from './analyticsMetrics'
import { formatDisplayDate } from '../utils/date'

export default function AnalyticsSummary({ summary, fromDate, toDate }) {
  const metrics = [
    ['Cumulative net investment', formatMetric(summary.netInvestment)],
    ['Latest total value', formatMetric(summary.latestValue)],
    ['Absolute gain/loss', formatMetric(summary.gainLoss)],
    ['Return percentage', summary.returnPercentage == null ? 'Unavailable' : `${formatMetric(summary.returnPercentage)}%`],
    ['Total units held', formatMetric(summary.units, 3)],
    ['Total bought in range', formatMetric(summary.totalBought)],
    ['Total sold in range', formatMetric(summary.totalSold)],
    ['Transactions in range', formatMetric(summary.transactionCount, 0)]
  ]
  return <div className="analytics-metrics" aria-label="Fund summary">
    <dl className="analytics-metrics-grid">
      {metrics.map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}
    </dl>
    <p>Investment and units include recorded transactions {toDate ? `through ${toDate}` : 'through the latest transaction'}.
      {fromDate && ` Opening net investment before ${fromDate}: ${formatMetric(summary.openingInvestment)}.`}
      {' '}Bought, sold, and transaction count cover only the selected range.</p>
    <p>{summary.valueDate ? `Latest snapshot: ${formatDisplayDate(summary.valueDate)}.` : 'No value snapshot in the selected range.'}
      {' '}Gain/loss = available snapshot value − cumulative net investment. Return is unavailable when net investment is zero or negative.
      {summary.hasLaterTransactions && ' Transactions after the snapshot are included in investment; this comparison does not represent a same-date return.'}
      {' '}Units are based on recorded BUY/SELL units and are unavailable when the history cannot support a reliable balance.</p>
  </div>
}
