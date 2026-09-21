import test from 'node:test'
import assert from 'node:assert/strict'
import { renderToStaticMarkup } from 'react-dom/server'
import { advancedReturns, xirr } from './advancedReturnMetrics'
import AdvancedReturns from './AdvancedReturns'
import { parseCalendarDate } from '../utils/date'

const snapshot = (valueAsOfDate, totalValue, valId = 1, mutualFundId = 1) => ({ valueAsOfDate, totalValue, valId, mutualFundId })
const txn = (txnDate, amount, transactionType = 'BUY', mutualFundId = 1) => ({ txnDate, amount, transactionType, mutualFundId })
const flow = (date, amount) => ({ date: parseCalendarDate(date), amount })
const close = (actual, expected, tolerance = 1e-8) => assert.ok(actual !== null && Math.abs(actual - expected) < tolerance, `${actual} != ${expected}`)

test('XIRR solves annual gains, losses, zero return and irregular purchase schedules', () => {
  for (const [terminal, expected] of [[110, 10], [75, -25], [100, 0]]) {
    close(xirr([flow('2025-01-01', -100), flow('2026-01-01', terminal)]).value, expected)
  }
  // 100 × 1.1² + 100 × 1.1 = 231, with two 365-day periods.
  close(xirr([flow('2025-01-01', -100), flow('2026-01-01', -100), flow('2027-01-01', 231)]).value, 10)
  const days = 181
  close(xirr([flow('2025-01-01', -100), flow('2025-07-01', 110)]).value, (1.1 ** (365 / days) - 1) * 100)
})

test('XIRR uses calendar day counts across leap years and daylight saving transitions', () => {
  close(xirr([flow('2024-01-01', -100), flow('2025-01-01', 110)]).value, (1.1 ** (365 / 366) - 1) * 100)
  close(xirr([flow('2026-03-07', -100), flow('2026-03-09', 100.1)]).value, (1.001 ** (365 / 2) - 1) * 100)
})

test('XIRR nets and sorts same-day payments before checking sign changes', () => {
  const flows = [flow('2026-01-01', 150), flow('2025-01-01', -100), flow('2026-01-01', -40)]
  close(xirr(flows).value, 10)
  close(xirr([...flows].reverse()).value, 10)
})

test('XIRR does not invent roots for ambiguous, one-sign, invalid or same-day histories', () => {
  assert.match(xirr([flow('2025-01-01', -100), flow('2026-01-01', 230), flow('2027-01-01', -132)]).reason, /unique XIRR/)
  for (const flows of [[], [flow('2025-01-01', -100)], [flow('2025-01-01', -100), flow('2025-01-01', 110)],
    [flow('2025-01-01', 100), flow('2026-01-01', 110)], [flow('bad', -100), flow('2026-01-01', 110)],
    [flow('2025-01-01', -Infinity), flow('2026-01-01', 110)]]) {
    assert.equal(xirr(flows).value, null)
  }
  assert.match(xirr([flow('2025-01-01', -1), flow('2025-01-02', 1e100)]).reason, /supported rate range/)
  close(xirr([flow('2025-01-01', -1e200), flow('2026-01-01', 1.1e200)]).value, 10)
})

test('covered period uses snapshots within the range and excludes opening-day and later transactions', () => {
  const transactions = [txn('2024-01-01', 800), txn('2025-01-01', 100), txn('2026-01-15', 900),
    txn('2025-02-01', 999, 'BUY', 2)]
  const values = [snapshot('2024-12-31', 99), snapshot('2025-01-01', 100), snapshot('2026-01-01', 110), snapshot('2026-02-01', 200)]
  const data = advancedReturns(transactions, values, '1', '01-Jan-2025', '31-Jan-2026')
  close(data.xirr.value, 10)
  close(data.twr.value, 10)
  assert.equal(data.snapshotCount, 2)
  assert.equal(data.hasLaterTransactions, true)
  assert.equal(advancedReturns(transactions, values, 1, '01-Jan-2025', '01-Jan-2026').hasLaterTransactions, false)
})

test('time-weighted return links cash-flow-adjusted intervals and sales do not create drawdown', () => {
  // 100 -> (160 - 50) / 100 = 1.10; then (136 + 40) / 160 = 1.10.
  const data = advancedReturns([txn('2025-07-01', 50), txn('2026-01-01', -40, 'SELL')],
    [snapshot('2025-01-01', 100), snapshot('2025-07-01', 160), snapshot('2026-01-01', 136)], 1)
  close(data.twr.value, 21)
  close(data.maxDrawdown.value, 0)
  close(data.observations.at(-1).index, 121)
  assert.equal(data.peakValue.value, 160)
  const neutral = advancedReturns([txn('2025-07-01', 100), txn('2026-01-01', 150, 'SELL')],
    [snapshot('2025-01-01', 100), snapshot('2025-07-01', 200), snapshot('2026-01-01', 50)], 1)
  close(neutral.twr.value, 0)
  close(neutral.maxDrawdown.value, 0)
  close(neutral.xirr.value, 0)
})

test('drawdown measures observed market declines and recovery on the adjusted index', () => {
  const data = advancedReturns([], [snapshot('2026-01-01', 100), snapshot('2026-02-01', 120),
    snapshot('2026-03-01', 90), snapshot('2026-04-01', 108)], 1)
  close(data.maxDrawdown.value, -25)
  close(data.currentDrawdown.value, -10)
  assert.equal(data.peakValue.value, 120)
  close(data.twr.value, 8)
})

test('missing cash-flow valuations disable TWR and drawdown while XIRR remains available', () => {
  const data = advancedReturns([txn('2025-07-01', 50)], [snapshot('2025-01-01', 100), snapshot('2026-01-01', 165)], 1)
  assert.equal(data.twr.value, null)
  assert.match(data.twr.reason, /01-Jul-2025/)
  assert.equal(data.maxDrawdown.value, null)
  assert.equal(data.currentDrawdown.value, null)
  assert.ok(data.xirr.value > 0)
  assert.ok(data.observations.every(row => row.index === null && row.drawdown === null))
})

test('same-day net zero flows need no intermediate valuation and order is irrelevant', () => {
  const transactions = [txn('2025-07-01', 50), txn('2025-07-01', 50, 'SELL')]
  const values = [snapshot('2025-01-01', 100), snapshot('2026-01-01', 110)]
  close(advancedReturns(transactions, values, 1).twr.value, 10)
  assert.deepEqual(advancedReturns(transactions, values, 1), advancedReturns([...transactions].reverse(), values, 1))
})

test('decimal BUY and SELL amounts do not create phantom flows or negative adjusted values', () => {
  const transactions = [txn('2025-07-01', 0.1), txn('2025-07-01', 0.2), txn('2025-07-01', 0.3, 'SELL')]
  const values = [snapshot('2025-01-01', 100), snapshot('2026-01-01', 110)]
  for (const history of [transactions, [...transactions].reverse()]) {
    close(advancedReturns(history, values, 1).twr.value, 10)
    close(advancedReturns(history, values, 1).xirr.value, 10)
  }
  close(advancedReturns([txn('2026-01-01', 0.1), txn('2026-01-01', 0.2)],
    [snapshot('2025-01-01', 100), snapshot('2026-01-01', 0.3)], 1).twr.value, -100)
})

test('a full sale can have zero return but a zero opening value breaks later intervals', () => {
  const values = [snapshot('2025-01-01', 100), snapshot('2026-01-01', 0)]
  const transactions = [txn('2026-01-01', 100, 'SELL')]
  const closed = advancedReturns(transactions, values, 1)
  close(closed.twr.value, 0)
  close(closed.xirr.value, 0)
  close(closed.maxDrawdown.value, 0)
  const restarted = advancedReturns([...transactions, txn('2026-02-01', 100)], [...values, snapshot('2026-02-01', 100)], 1)
  assert.equal(restarted.twr.value, null)
  assert.match(restarted.twr.reason, /zero balances/)
  const loss = advancedReturns([], values, 1)
  close(loss.twr.value, -100)
  close(loss.maxDrawdown.value, -100)
  assert.equal(loss.xirr.value, null)
})

test('negative valuations, negative purchases and impossible timing produce explanations', () => {
  const values = [snapshot('2025-01-01', 100), snapshot('2026-01-01', 110)]
  const negativeBuy = advancedReturns([txn('2025-07-01', -50)], values, 1)
  assert.equal(negativeBuy.xirr.value, null)
  assert.equal(negativeBuy.twr.value, null)
  assert.match(negativeBuy.xirr.reason, /Negative purchase/)
  const negativeValue = advancedReturns([], [values[0], snapshot('2026-01-01', -10)], 1)
  assert.equal(negativeValue.twr.value, null)
  assert.equal(negativeValue.xirr.value, null)
  assert.equal(negativeValue.peakValue.value, null)
  assert.match(advancedReturns([txn('2026-01-01', 200)], values, 1).twr.reason, /smaller than its net contribution/)
  assert.throws(() => advancedReturns([txn('2025-01-01', 10, 'OTHER')], values, 1), /invalid transaction/)
})

test('snapshot selection uses highest valid same-day ID and isolates the selected fund', () => {
  const values = [snapshot('2025-01-01', 100), snapshot('2026-01-01T09:00:00', 110, 3),
    snapshot('2026-01-01T22:00:00', 900, 2), snapshot('2026-01-01', null, 4), snapshot('2026-01-01', 9999, 5, 2)]
  const data = advancedReturns([txn('bad', null, 'OTHER', 2)], values, '1')
  close(data.xirr.value, 10)
  close(data.twr.value, 10)
  assert.equal(data.snapshotCount, 2)
  assert.equal(values.length, 5)
})

test('monthly returns and sample volatility match hand-worked gains and losses', () => {
  // Monthly returns +10%, -10%; sample stdev = sqrt(200), annualized = sqrt(2400).
  const data = advancedReturns([], [snapshot('2026-01-31', 100), snapshot('2026-02-28', 110), snapshot('2026-03-31', 99)], 1)
  assert.equal(data.monthly.length, 2)
  close(data.monthly[0].value, 10)
  close(data.monthly[1].value, -10)
  close(data.volatility.value, Math.sqrt(200))
  close(data.annualizedVolatility.value, Math.sqrt(2400))
  assert.equal(data.volatilityMonths, 2)
})

test('monthly returns handle year boundaries, leap days and cash flows at month end', () => {
  const data = advancedReturns([txn('2024-01-31', 50)], [snapshot('2023-12-31', 100), snapshot('2024-01-31', 160), snapshot('2024-02-29', 176)], 1)
  assert.equal(data.monthly.length, 2)
  close(data.monthly[0].value, 10)
  close(data.monthly[1].value, 10)
  close(data.volatility.value, 0)
  assert.equal(data.monthly[1].closing.getDate(), 29)
})

test('monthly gaps are unavailable rather than zero and invalidate the volatility sample', () => {
  const data = advancedReturns([], [snapshot('2026-01-31', 100), snapshot('2026-03-31', 121),
    snapshot('2026-04-30', 133.1), snapshot('2026-05-31', 146.41)], 1)
  assert.equal(data.monthly.length, 4)
  assert.equal(data.monthly[0].value, null)
  assert.equal(data.monthly[1].value, null)
  close(data.monthly[2].value, 10)
  assert.equal(data.volatility.value, null)
  assert.match(data.volatility.reason, /Missing monthly returns/)
  close(data.twr.value, 46.41)
})

test('partial months are visible but excluded from volatility and no snapshots are borrowed outside the range', () => {
  const values = [snapshot('2025-12-31', 95), snapshot('2026-01-15', 100), snapshot('2026-01-31', 100),
    snapshot('2026-02-28', 110), snapshot('2026-03-31', 99), snapshot('2026-04-15', 120)]
  const data = advancedReturns([], values, 1, '15-Jan-2026', '15-Apr-2026')
  assert.deepEqual(data.monthly.map(row => row.complete), [false, true, true, false])
  assert.equal(data.monthly[0].value, null)
  assert.equal(data.monthly[3].value, null)
  close(data.volatility.value, Math.sqrt(200))
  assert.equal(advancedReturns([], values, 1, '01-Feb-2026', '15-Apr-2026').monthly[0].date.getMonth(), 2)
})

test('a missing flow-date snapshot invalidates only affected monthly returns', () => {
  const data = advancedReturns([txn('2026-02-15', 50)], [snapshot('2026-01-31', 100), snapshot('2026-02-28', 150), snapshot('2026-03-31', 165)], 1)
  assert.equal(data.monthly[0].value, null)
  close(data.monthly[1].value, 10)
  assert.equal(data.volatility.value, null)
})

test('empty and single-snapshot histories preserve unavailable metrics instead of zero performance', () => {
  for (const values of [[], [snapshot('2026-01-01', 100)], [snapshot('bad', 10)]]) {
    const data = advancedReturns([], values, 1)
    for (const name of ['xirr', 'twr', 'maxDrawdown', 'currentDrawdown', 'volatility', 'annualizedVolatility']) {
      assert.equal(data[name].value, null, name)
      assert.ok(data[name].reason)
    }
    assert.deepEqual(data.monthly, [])
  }
  assert.equal(advancedReturns([], [snapshot('2026-01-01', 100)], 1).peakValue.value, 100)
})

test('returns view renders metric explanations, covered dates and accessible tables', () => {
  const html = renderToStaticMarkup(<AdvancedReturns transactions={[txn('2026-04-01', 500)]}
    values={[snapshot('2026-01-31', 100), snapshot('2026-02-28', 110), snapshot('2026-03-31', 99)]} fundId={1} />)
  for (const text of ['Money-weighted return (XIRR)', 'Time-weighted return', 'Maximum drawdown', 'Current drawdown',
    'Monthly volatility', 'Annualized volatility', 'Peak recorded value', '31-Jan-2026', '31-Mar-2026',
    'Transactions after the last snapshot are excluded', 'Calculation assumptions', '365-day year', '-10.00%']) assert.ok(html.includes(text), text)
  assert.match(html, /<caption>Monthly time-weighted returns/)
  assert.match(html, /<caption>Recorded values and cash-flow-adjusted drawdown/)
  assert.match(html, /role="region" aria-label="Monthly returns" tabindex="0"/)
  assert.doesNotMatch(html, /NaN|Infinity/)
})

test('returns view explains missing data and does not render fabricated returns', () => {
  const empty = renderToStaticMarkup(<AdvancedReturns transactions={[]} values={[]} fundId={1} />)
  assert.match(empty, /No value history available/)
  assert.doesNotMatch(empty, /<table|0.00%/)
  const single = renderToStaticMarkup(<AdvancedReturns transactions={[]} values={[snapshot('2026-01-01', 100)]} fundId={1} />)
  assert.match(single, /At least two dated value snapshots/)
  assert.match(single, /Unavailable/)
  assert.doesNotMatch(single, /NaN|Infinity|0.00%/)
})
