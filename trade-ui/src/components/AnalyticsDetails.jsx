import { useMemo, useState } from 'react'
import { cashFlowHistory, holdingsHistory, transactionActivity } from './analyticsActivity'
import { formatMetric } from './analyticsMetrics'
import { formatDisplayDate, parseCalendarDate } from '../utils/date'
import { CashFlowChart, HoldingsChart } from './ActivityCharts'
import Pagination from './Pagination'

export const ANALYTICS_VIEWS = [['value', 'Value history'], ['cash', 'Cash flows'], ['holdings', 'Holdings'], ['transactions', 'Transactions']]

export function AnalyticsResults({ fundId, loading, error, rangeError, children }) {
  if (!fundId) return <div className="analytics-empty"><h3>Select a mutual fund</h3>
    <p>Choose a mutual fund above to view its analytics.</p></div>
  if (loading) return <div className="analytics-empty" role="status">Loading analytics history…</div>
  if (error) return <div className="analytics-empty">Analytics history could not be loaded. Please try again.</div>
  if (rangeError) return <div className="analytics-empty"><h3>Invalid date range</h3><p>{rangeError}</p></div>
  return children
}

export function AnalyticsViewSelector({ view, onChange }) {
  return <div className="analytics-view-selector" role="group" aria-label="Analytics view">
    {ANALYTICS_VIEWS.map(([id, label]) => <button key={id} type="button" aria-pressed={view === id}
      onClick={() => onChange(id)}>{label}</button>)}
  </div>
}

function TableWrap({ label, children }) {
  return <div className="table-wrap analytics-detail-table" role="region" aria-label={label} tabIndex={0}>{children}</div>
}

export function CashFlowView({ transactions, values, fundId, fromDate, toDate }) {
  const [period, setPeriod] = useState('monthly')
  const rows = useMemo(() => cashFlowHistory(transactions, values, fundId, fromDate, toDate, period),
    [transactions, values, fundId, fromDate, toDate, period])
  return <div className="analytics-card">
    <div className="analytics-card-header"><h3>Cash flows</h3>
      <label>Group by<select value={period} onChange={event => setPeriod(event.target.value)}>
        <option value="monthly">Monthly</option><option value="quarterly">Quarterly</option>
      </select></label>
    </div>
    <p className="chart-explanation">Zero means no recorded cash flow in a period of the loaded history. Boundary periods include only dates inside the selected range.</p>
    {rows.length ? <>
      <CashFlowChart rows={rows} />
      <TableWrap label="Cash-flow amounts"><table>
        <caption>Cash-flow amounts for the selected range</caption>
        <thead><tr>{['Period', 'Included dates', 'BUY', 'SELL', 'Net flow', 'Transactions'].map(label => <th key={label} scope="col">{label}</th>)}</tr></thead>
        <tbody>{rows.map(row => <tr key={row.date.getTime()}>
          <th scope="row">{row.label}</th><td>{formatDisplayDate(row.start)} – {formatDisplayDate(row.end)}</td>
          <td>{formatMetric(row.bought)}</td><td>{formatMetric(row.sold)}</td><td>{formatMetric(row.net)}</td><td>{row.count}</td>
        </tr>)}</tbody>
      </table></TableWrap>
    </> : <p className="analytics-detail-empty">No dated history available. Select a date range to see periods with zero recorded cash flow.</p>}
  </div>
}

export function HoldingsView({ transactions, values, fundId, fromDate, toDate }) {
  const rows = useMemo(() => holdingsHistory(transactions, values, fundId, fromDate, toDate),
    [transactions, values, fundId, fromDate, toDate])
  return <div className="analytics-card">
    <h3>Holdings over time</h3>
    <p className="chart-explanation">End-of-day balances include transactions before the range and carry forward on dates without activity.
      {' '}Average cost uses recorded purchase amounts and units. Sales remove cost at the running average; sale proceeds do not change the average.
      {' '}Same-day purchases are pooled before sales because intraday ordering is not available. This is an estimate, not a tax cost basis.</p>
    <p className="chart-explanation">Missing or invalid units and negative historical balances make holdings unavailable from that date onward.
      {' '}Before the first recorded transaction, holdings are unknown. A fully sold holding has zero units and no average cost.</p>
    {rows.length ? <>
      <h4>Units held</h4><HoldingsChart rows={rows} metric="units" label="Units held" digits={3} />
      <h4>Average cost per unit</h4><HoldingsChart rows={rows} metric="averageCost" label="Average cost per unit" digits={2} />
      <TableWrap label="Holdings balances"><table>
        <caption>Holdings at range boundaries, transaction dates, and snapshot dates</caption>
        <thead><tr><th scope="col">Date</th><th scope="col">Units held</th><th scope="col">Average cost per unit</th></tr></thead>
        <tbody>{rows.map(row => <tr key={row.date.getTime()}><th scope="row">{formatDisplayDate(row.date)}</th>
          <td>{formatMetric(row.units, 3)}</td><td>{formatMetric(row.averageCost)}</td></tr>)}</tbody>
      </table></TableWrap>
    </> : <p className="analytics-detail-empty">No holdings history available for the selected range.</p>}
  </div>
}

const recordedNumber = (value, digits = 2) => value == null || String(value).trim() === '' ? 'Unavailable' : formatMetric(Number(value), digits)

export function TransactionActivityView({ transactions, fundId, fromDate, toDate }) {
  const rows = useMemo(() => transactionActivity(transactions, fundId, fromDate, toDate), [transactions, fundId, fromDate, toDate])
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(20)
  const totalPages = Math.ceil(rows.length / size)
  const currentPage = Math.min(page, Math.max(0, totalPages - 1))
  return <div className="analytics-card">
    <h3>Transaction activity</h3>
    <p className="chart-explanation">Recorded transactions in the selected range, newest first. Earlier transactions contribute to holdings but are not listed here.</p>
    {rows.length ? <>
      <TableWrap label="Transaction activity"><table>
        <caption>Transactions in the selected range</caption>
        <thead><tr>{['Date', 'Transaction ID', 'Type', 'Amount', 'Units', 'Recorded average price'].map(label => <th key={label} scope="col">{label}</th>)}</tr></thead>
        <tbody>{rows.slice(currentPage * size, (currentPage + 1) * size).map((row, index) => <tr key={row.mutualFundTxnId ?? index}>
          <td>{formatDisplayDate(parseCalendarDate(row.txnDate))}</td><td>{row.mutualFundTxnId ?? 'Unavailable'}</td><td>{row.transactionType}</td>
          <td>{formatMetric(row.amount)}</td><td>{recordedNumber(row.units, 3)}</td><td>{recordedNumber(row.avgPrice)}</td>
        </tr>)}</tbody>
      </table></TableWrap>
      <Pagination page={currentPage} size={size} totalPages={totalPages} totalElements={rows.length}
        first={currentPage === 0} last={currentPage >= totalPages - 1}
        onPrevious={() => setPage(currentPage - 1)} onNext={() => setPage(currentPage + 1)}
        onSizeChange={value => { setSize(value); setPage(0) }} />
    </> : <p className="analytics-detail-empty">No transactions in the selected range.</p>}
  </div>
}

export default function AnalyticsDetails({ view, ...props }) {
  if (view === 'cash') return <CashFlowView {...props} />
  if (view === 'holdings') return <HoldingsView {...props} />
  return <TransactionActivityView key={`${props.fundId}:${props.fromDate}:${props.toDate}`} {...props} />
}
