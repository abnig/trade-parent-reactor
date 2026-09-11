import test from 'node:test'
import assert from 'node:assert/strict'
import { renderToStaticMarkup } from 'react-dom/server'
import { cashFlowHistory, holdingsHistory, transactionActivity } from './analyticsActivity'
import { analyticsSummary } from './analyticsMetrics'
import { historyDate } from './investmentHistory'
import AnalyticsDetails, { AnalyticsResults, AnalyticsViewSelector, CashFlowView, HoldingsView, TransactionActivityView } from './AnalyticsDetails'
import { CashFlowChart, HoldingsChart } from './ActivityCharts'

const txn = (txnDate, amount, units, transactionType = 'BUY', mutualFundId = 1, mutualFundTxnId = 1) =>
  ({ txnDate, amount, units, transactionType, mutualFundId, mutualFundTxnId, avgPrice: 10 })
const snapshot = (valueAsOfDate, mutualFundId = 1) => ({ valueAsOfDate, mutualFundId, totalValue: 100 })
const balances = rows => rows.map(({ units, averageCost }) => [units, averageCost])
const flows = rows => rows.map(({ bought, sold, net, count }) => [bought, sold, net, count])

test('monthly cash flows include zero periods and only selected-fund activity within inclusive boundaries', () => {
  const transactions = [txn('2025-12-31', 1000, 100), txn('2026-01-14', 1000, 100),
    txn('15-Jan-2026', 100, 10), txn('2026-01-31T22:00:00', -40, 4, 'SELL'),
    txn('2026-03-10', 60, 6, 'SELL'), txn('2026-03-11', 1000, 100), txn('2026-02-01', 999, 99, 'BUY', 2)]
  const rows = cashFlowHistory(transactions, [], '1', '15-Jan-2026', '10-Mar-2026')
  assert.deepEqual(rows.map(row => row.label), ['Jan-2026', 'Feb-2026', 'Mar-2026'])
  assert.deepEqual(flows(rows), [[100, 40, 60, 2], [0, 0, 0, 0], [0, 60, -60, 1]])
  assert.equal(rows[0].start.getTime(), historyDate('2026-01-15').getTime())
  assert.equal(rows.at(-1).end.getTime(), historyDate('2026-03-10').getTime())
  const summary = analyticsSummary(transactions, [], 1, '15-Jan-2026', '10-Mar-2026')
  assert.equal(rows.reduce((sum, row) => sum + row.bought, 0), summary.totalBought)
  assert.equal(rows.reduce((sum, row) => sum + row.sold, 0), summary.totalSold)
  assert.equal(rows.reduce((sum, row) => sum + row.count, 0), summary.transactionCount)
})

test('quarterly buckets cross year boundaries and match monthly totals', () => {
  const transactions = [txn('2025-12-31', 100, 10), txn('2026-01-01', 50, 5), txn('2026-04-01', 30, 3, 'SELL')]
  const quarterly = cashFlowHistory(transactions, [], 1, '', '', 'quarterly')
  assert.deepEqual(quarterly.map(row => row.label), ['Q4 2025', 'Q1 2026', 'Q2 2026'])
  assert.deepEqual(flows(quarterly), [[100, 0, 100, 1], [50, 0, 50, 1], [0, 30, -30, 1]])
  assert.equal(quarterly.reduce((sum, row) => sum + row.net, 0),
    cashFlowHistory(transactions, [], 1).reduce((sum, row) => sum + row.net, 0))
})

test('cash flow distinguishes a dated zero-activity range from an unknown extent', () => {
  assert.deepEqual(flows(cashFlowHistory([], [], 1, '01-Jan-2026', '31-Mar-2026')),
    [[0, 0, 0, 0], [0, 0, 0, 0], [0, 0, 0, 0]])
  assert.deepEqual(cashFlowHistory([], [], 1), [])
  assert.deepEqual(flows(cashFlowHistory([], [snapshot('2026-02-01')], 1)), [[0, 0, 0, 0]])
  assert.deepEqual(cashFlowHistory([], [snapshot('2026-02-01', 2)], 1), [])
  assert.equal(cashFlowHistory([], [], 1, '01-Jan-2026').length, 1)
  assert.equal(cashFlowHistory([], [], 1, '', '31-Jan-2026').length, 1)
  assert.deepEqual(cashFlowHistory([], [], 1, '31-Mar-2026', '01-Jan-2026'), [])
  assert.deepEqual(cashFlowHistory([], [], 1, 'bad', '01-Jan-2026'), [])
  const leap = cashFlowHistory([], [], 1, '01-Feb-2024', '01-Mar-2024')
  assert.equal(leap[0].end.getDate(), 29)
})

test('cash flows and holdings reject invalid history instead of inventing zero data', () => {
  for (const helper of [cashFlowHistory, holdingsHistory]) {
    assert.throws(() => helper([txn('bad', 10, 1)], [], 1), /invalid transaction/)
    assert.throws(() => helper([txn('2026-01-01', null, 1)], [], 1), /invalid transaction/)
    assert.throws(() => helper([txn('2026-01-01', 10, 1, 'OTHER')], [], 1), /invalid transaction/)
    assert.deepEqual(helper([txn('bad', null, null, 'OTHER', 2)], [], 1), [])
  }
})

test('holdings use weighted purchase cost and preserve average cost through sales', () => {
  const rows = holdingsHistory([txn('2026-01-01', 100, 10), txn('2026-01-15', 200, 10),
    txn('2026-02-01', 200, 5, 'SELL'), txn('2026-02-15', 150, 5),
    txn('2026-03-01', 500, 20, 'SELL'), txn('2026-03-02', 160, 4)], [], 1)
  assert.deepEqual(balances(rows), [[10, 10], [20, 15], [15, 15], [20, 18.75], [0, null], [4, 40]])
})

test('holdings carry opening balances across empty ranges and snapshot dates', () => {
  const transactions = [txn('2026-01-01', 100, 10), txn('2026-01-15', 25, 2, 'SELL'),
    txn('2026-03-01', 1000, 100), txn('2026-02-15', 999, 99, 'BUY', 2)]
  const rows = holdingsHistory(transactions, [snapshot('2026-02-14'), snapshot('2026-02-10', 2)], '1', '01-Feb-2026', '28-Feb-2026')
  assert.deepEqual(rows.map(row => row.date.getDate()), [1, 14, 28])
  assert.deepEqual(balances(rows), [[8, 10], [8, 10], [8, 10]])
  assert.equal(rows.at(-1).units, analyticsSummary(transactions, [], 1, '01-Feb-2026', '28-Feb-2026').units)
  assert.deepEqual(balances(holdingsHistory(transactions, [], 1, '01-Apr-2026')), [[108, 10]])
})

test('same-day trades have deterministic end-of-day balances, pooling buys before sells', () => {
  const transactions = [txn('2026-01-01', 100, 10), txn('2026-02-01', 300, 15, 'SELL'), txn('2026-02-01', 200, 10)]
  const expected = [[10, 10], [5, 15]]
  assert.deepEqual(balances(holdingsHistory(transactions, [], 1)), expected)
  assert.deepEqual(balances(holdingsHistory([...transactions].reverse(), [], 1)), expected)
})

test('holdings preserve unknown data and do not recover an unreliable balance from later purchases', () => {
  const transactions = [txn('2026-01-10', 100, 10), txn('2026-01-20', 10, null), txn('2026-01-30', 50, 5)]
  assert.deepEqual(balances(holdingsHistory(transactions, [], 1, '01-Jan-2026', '01-Feb-2026')),
    [[null, null], [10, 10], [null, null], [null, null], [null, null]])
  for (const units of ['', 0, -2, 'invalid', Infinity]) {
    assert.deepEqual(balances(holdingsHistory([txn('2026-01-10', 100, units)], [], 1)), [[null, null]])
  }
  assert.deepEqual(balances(holdingsHistory([txn('2026-01-01', 100, 10, 'SELL'), txn('2026-01-02', 200, 20)], [], 1)),
    [[null, null], [null, null]])
  assert.deepEqual(balances(holdingsHistory([], [], 1, '01-Jan-2026', '01-Feb-2026')), [[null, null], [null, null]])
  assert.deepEqual(balances(holdingsHistory([txn('2026-01-01', -10, 1)], [], 1)), [[1, null]])
})

test('fractional units close to zero close the holding without a floating-point negative balance', () => {
  const rows = holdingsHistory([txn('2026-01-01', 1, 0.1), txn('2026-01-02', 2, 0.2), txn('2026-01-03', 4, 0.3, 'SELL')], [], 1)
  assert.deepEqual(balances(rows).at(-1), [0, null])
})

test('transaction activity filters by calendar date and fund, then sorts newest and highest ID first', () => {
  const transactions = [txn('2026-01-01', 100, 10, 'BUY', 1, 1), txn('01-Feb-2026', 20, 2, 'BUY', 1, 2),
    txn('2026-02-01T08:00:00', 30, 3, 'SELL', 1, 3), txn('2026-03-01', 50, 5, 'BUY', 1, 4),
    txn('2026-02-01', 1000, 100, 'BUY', 2, 5)]
  assert.deepEqual(transactionActivity(transactions, '1', '01-Feb-2026', '28-Feb-2026').map(row => row.mutualFundTxnId), [3, 2])
  assert.equal(transactions[0].mutualFundTxnId, 1)
})

test('analytics view buttons expose selection and switch to each view', () => {
  let selected = 'value'
  const element = AnalyticsViewSelector({ view: selected, onChange: value => { selected = value } })
  const buttons = element.props.children
  assert.deepEqual(buttons.map(button => button.props['aria-pressed']), [true, false, false, false])
  for (const [index, expected] of [[1, 'cash'], [2, 'holdings'], [3, 'transactions'], [0, 'value']]) {
    buttons[index].props.onClick()
    assert.equal(selected, expected)
  }
})

test('cash flow view provides monthly/quarterly control, signed bars, and exact table amounts', () => {
  const html = renderToStaticMarkup(<CashFlowView transactions={[txn('2026-01-01', 100, 10), txn('2026-02-01', 150, 10, 'SELL')]}
    values={[]} fundId={1} fromDate="01-Jan-2026" toDate="31-Mar-2026" />)
  for (const label of ['Group by', 'Monthly', 'Quarterly', 'BUY', 'SELL', 'Net flow', 'Mar-2026', '-150.00']) {
    assert.ok(html.includes(label), label)
  }
  assert.match(html, /<caption>Cash-flow amounts for the selected range/)
  assert.match(html, /Zero means no recorded cash flow/)
  assert.doesNotMatch(html, /NaN|Infinity/)
  const zeros = renderToStaticMarkup(<CashFlowChart rows={cashFlowHistory([], [], 1, '01-Jan-2026', '31-Jan-2026')} />)
  assert.doesNotMatch(zeros, /NaN|Infinity/)
  assert.match(zeros, /height="0"/)
})

test('holdings view has independent unit/cost charts and a readable table without value snapshots', () => {
  const html = renderToStaticMarkup(<HoldingsView transactions={[txn('2026-01-01', 100, 10)]} values={[]} fundId={1} />)
  assert.match(html, /Units held over time/)
  assert.match(html, /Average cost per unit over time/)
  assert.match(html, /<caption>Holdings at range boundaries/)
  assert.match(html, /10.000/)
  assert.match(html, /10.00/)
  assert.match(html, /Same-day purchases are pooled before sales/)
  assert.doesNotMatch(html, /NaN|Infinity/)
})

test('holdings chart breaks cost paths at a closed holding and renders a zero unit balance', () => {
  const rows = holdingsHistory([txn('2026-01-01', 100, 10), txn('2026-01-02', 100, 10, 'SELL'), txn('2026-01-03', 100, 5)], [], 1)
  const cost = renderToStaticMarkup(<HoldingsChart rows={rows} metric="averageCost" label="Average cost per unit" digits={2} />)
  const path = /<path d="([^"]+)"/.exec(cost)[1]
  assert.equal((path.match(/M /g) || []).length, 2)
  assert.doesNotMatch(path, /H /)
  const units = renderToStaticMarkup(<HoldingsChart rows={rows} metric="units" label="Units held" digits={3} />)
  assert.match(units, /02-Jan-2026: Units held 0.000/)
})

test('transactions show recorded numbers, missing prices, and paginate loaded history', () => {
  const transactions = Array.from({ length: 25 }, (_, index) => ({ ...txn('2026-01-01', 100, 1.23456, 'BUY', 1, index + 1), avgPrice: null }))
  const html = renderToStaticMarkup(<TransactionActivityView transactions={transactions} fundId={1} />)
  assert.match(html, /25 records/)
  assert.match(html, /Page 1 of 2/)
  assert.equal((html.match(/<tr>/g) || []).length, 21)
  assert.match(html, /1.235/)
  assert.match(html, /Unavailable/)
  assert.match(html, /Recorded average price/)
})

test('all detail views expose clear empty states without fabricated holdings or numeric errors', () => {
  for (const [view, message] of [['cash', 'No dated history available'], ['holdings', 'No holdings history available'], ['transactions', 'No transactions in the selected range']]) {
    const html = renderToStaticMarkup(<AnalyticsDetails view={view} transactions={[]} values={[]} fundId={1} />)
    assert.ok(html.includes(message))
    assert.doesNotMatch(html, /<svg|NaN|Infinity/)
  }
  const unknown = renderToStaticMarkup(<HoldingsView transactions={[]} values={[]} fundId={1} fromDate="01-Jan-2026" toDate="31-Jan-2026" />)
  assert.match(unknown, /Units held unavailable/)
  assert.match(unknown, /Average cost per unit unavailable/)
  assert.match(unknown, /Unavailable/)
  assert.doesNotMatch(unknown, /<svg/)
})

test('loading, failed requests, missing selection, and invalid ranges suppress every analytics view', () => {
  for (const [state, message] of [[{ loading: true }, 'Loading analytics history'],
    [{ error: 'History unavailable' }, 'Analytics history could not be loaded'],
    [{ fundId: '' }, 'Select a mutual fund'],
    [{ rangeError: 'From date must be on or before the to date.' }, 'Invalid date range']]) {
    for (const view of ['cash', 'holdings', 'transactions']) {
      const html = renderToStaticMarkup(<AnalyticsResults fundId={1} {...state}>
        <AnalyticsViewSelector view={view} onChange={() => {}} />
        <AnalyticsDetails view={view} transactions={[]} values={[]} fundId={1} fromDate="01-Jan-2026" toDate="31-Jan-2026" />
      </AnalyticsResults>)
      assert.ok(html.includes(message))
      assert.doesNotMatch(html, /<svg|<table|0.00|Analytics view/)
    }
  }
  const ready = renderToStaticMarkup(<AnalyticsResults fundId={1}><span>Ready analytics</span></AnalyticsResults>)
  assert.match(ready, /Ready analytics/)
})
