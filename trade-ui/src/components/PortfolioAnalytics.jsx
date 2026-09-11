import { useEffect, useState } from 'react'
import api from '../api/api'
import { toPickerDate } from './DateInput'
import { formatMetric } from './analyticsMetrics'
import { formatDisplayDate, parseCalendarDate } from '../utils/date'

export const PORTFOLIO_SELECTION = 'portfolio'

const displayDate = value => value ? formatDisplayDate(parseCalendarDate(value)) : 'Unavailable'
const percentage = value => value == null ? 'Unavailable' : `${formatMetric(value)}%`

export function PortfolioShare({ value, label }) {
  return <span className="portfolio-share">
    {value != null && <meter min="0" max="100" value={value} aria-label={label} />}
    <span>{percentage(value)}</span>
  </span>
}

export function PortfolioAnalyticsView({ data, loading, error, onRetry, onSelectFund, fromDate, toDate }) {
  if (loading) return <div className="analytics-empty" role="status">Loading portfolio analytics…</div>
  if (error) return <div className="analytics-empty" role="alert"><h3>Portfolio analytics could not be loaded</h3>
    <p>{error}</p><button type="button" className="secondary" onClick={onRetry}>Retry portfolio</button></div>
  if (!data || data.fundCount === 0) return <div className="analytics-empty"><h3>No funds in your portfolio</h3>
    <p>Add a mutual fund to one of your broker accounts to see portfolio comparisons.</p></div>
  const metrics = [
    ['Total portfolio value', formatMetric(data.totalValue)],
    ['Cumulative net investment', formatMetric(data.totalInvested)],
    ['Portfolio gain/loss', formatMetric(data.gainLoss)],
    ['Portfolio return', percentage(data.returnPercentage)]
  ]
  const laterTransactions = data.funds.some(fund => fund.valueAsOfDate && fund.lastTransactionDate &&
    parseCalendarDate(fund.lastTransactionDate) > parseCalendarDate(fund.valueAsOfDate))
  return <div className="portfolio-analytics">
    <div className="analytics-metrics">
      <h3>Portfolio comparison</h3>
      <dl className="analytics-metrics-grid">{metrics.map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}</dl>
      <p>{data.fundCount} funds · {data.valuedFundCount} with value snapshots. Comparison through {displayDate(data.asOfDate)}.</p>
      {data.valuedFundCount < data.fundCount && <p className="portfolio-data-note" role="status">
        {data.fundCount - data.valuedFundCount} funds have no value snapshot in the selected range.
        {' '}Known snapshot value: {formatMetric(data.knownValueTotal)}. Total portfolio value, portfolio gain/loss, and allocation shares are unavailable until every fund has a value.</p>}
      <p>Net investment = cumulative BUY amounts − SELL amounts {toDate ? `through ${toDate}` : 'through the latest recorded transaction'}.
        {fromDate && ` Transactions before ${fromDate} are included.`}
        {' '}Each fund uses its latest snapshot inside the selected range. Snapshot dates may differ; this is not a synchronized portfolio valuation.</p>
      <p>Gain/loss = snapshot value − net investment. Return is unavailable for zero or negative net investment.
        {' '}Allocation is the share of total snapshot value and requires complete, nonnegative values with a positive total.
        {' '}Contribution share is each fund’s cumulative BUY amount divided by all funds’ cumulative BUY amounts; it is unavailable when total purchases are zero or any fund has negative purchases.</p>
      {laterTransactions && <p className="portfolio-data-note">Some funds include transactions after their snapshot date. Their comparisons do not represent same-date returns.</p>}
    </div>

    <div className="table-wrap analytics-detail-table" role="region" aria-label="Fund comparisons" tabIndex={0}>
      <table><caption>Fund comparison and contribution shares</caption>
        <thead><tr>{['Fund', 'Broker account', 'Snapshot date', 'Latest value', 'Net investment', 'Gain/loss', 'Return', 'Value allocation', 'Cumulative BUY', 'Contribution share', 'History'].map(label => <th key={label} scope="col">{label}</th>)}</tr></thead>
        <tbody>{data.funds.map(fund => <tr key={fund.mutualFundId}>
          <th scope="row">{fund.mutualFundName}</th><td>{fund.brokerName} · {fund.accountId}</td>
          <td>{displayDate(fund.valueAsOfDate)}{fund.valueAsOfDate && fund.lastTransactionDate &&
            parseCalendarDate(fund.lastTransactionDate) > parseCalendarDate(fund.valueAsOfDate) && <small className="portfolio-date-note">Transactions through {displayDate(fund.lastTransactionDate)}</small>}</td>
          <td>{formatMetric(fund.totalValue)}</td><td>{formatMetric(fund.totalInvested)}</td><td>{formatMetric(fund.gainLoss)}</td>
          <td>{percentage(fund.returnPercentage)}</td>
          <td><PortfolioShare value={fund.allocationPercentage} label={`Value allocation for ${fund.mutualFundName}`} /></td>
          <td>{formatMetric(fund.totalBought)}</td>
          <td><PortfolioShare value={fund.contributionPercentage} label={`Contribution share for ${fund.mutualFundName}`} /></td>
          <td><button type="button" className="secondary" onClick={() => onSelectFund(fund.mutualFundId)} aria-label={`View history for ${fund.mutualFundName}`}>View fund</button></td>
        </tr>)}</tbody>
      </table>
    </div>

    <div className="table-wrap analytics-detail-table" role="region" aria-label="Broker account allocations" tabIndex={0}>
      <table><caption>Allocation by broker account</caption>
        <thead><tr>{['Broker', 'Account', 'Funds with values', 'Total value', 'Known snapshot value', 'Net investment', 'Portfolio allocation'].map(label => <th key={label} scope="col">{label}</th>)}</tr></thead>
        <tbody>{data.brokerAccounts.map(broker => <tr key={broker.brokerAccountId}>
          <th scope="row">{broker.brokerName}</th><td>{broker.accountId}</td><td>{broker.valuedFundCount} / {broker.fundCount}</td>
          <td>{formatMetric(broker.totalValue)}</td><td>{formatMetric(broker.knownValueTotal)}</td><td>{formatMetric(broker.totalInvested)}</td>
          <td><PortfolioShare value={broker.allocationPercentage} label={`Portfolio allocation for ${broker.brokerName}, ${broker.accountId}`} /></td>
        </tr>)}</tbody>
      </table>
    </div>
  </div>
}

export default function PortfolioAnalytics({ fromDate, toDate, onSelectFund }) {
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [retry, setRetry] = useState(0)
  useEffect(() => {
    let active = true
    setLoading(true)
    setData(null)
    setError('')
    api.analytics.portfolio({ fromDate: toPickerDate(fromDate), toDate: toPickerDate(toDate) })
      .then(response => { if (active) setData(response) })
      .catch(reason => { if (active) setError(reason.message || 'Failed to load portfolio analytics.') })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [fromDate, toDate, retry])
  return <PortfolioAnalyticsView data={data} loading={loading} error={error} fromDate={fromDate} toDate={toDate}
    onSelectFund={onSelectFund} onRetry={() => setRetry(value => value + 1)} />
}
