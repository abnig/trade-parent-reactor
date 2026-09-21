import { valueHistory } from './analyticsMetrics'
import { normalizedTransactions } from './investmentHistory'
import { formatDisplayDate, parseCalendarDate } from '../utils/date'

const unavailable = reason => ({ value: null, reason })
const metric = value => Number.isFinite(value) ? { value, reason: '' } : unavailable('The calculation exceeds the supported numeric range.')
const NEED_SNAPSHOTS = 'At least two dated value snapshots are required.'

// Compensated summation keeps same-day decimal amounts from creating a
// phantom cash flow (for example, BUY 0.10 + BUY 0.20 - SELL 0.30).
function cashTotal(amounts) {
  let total = 0
  let correction = 0
  let magnitude = 0
  for (const amount of amounts) {
    const next = total + amount
    correction += Math.abs(total) >= Math.abs(amount) ? (total - next) + amount : (amount - next) + total
    total = next
    magnitude += Math.abs(amount)
  }
  const result = total + correction
  if (!Number.isFinite(result) || !Number.isFinite(magnitude)) return NaN
  return Math.abs(result) <= 8 * Number.EPSILON * magnitude ? 0 : result
}

// Calendar days, independent of DST and the browser's time zone.
const calendarDay = date => {
  const utc = new Date(0)
  utc.setUTCFullYear(date.getFullYear(), date.getMonth(), date.getDate())
  return utc.getTime() / 86400000
}

/** Annualized XIRR in percent, actual calendar days / 365.
 * Only a negative-then-positive cash-flow sequence is solved: multiple sign
 * changes can have multiple roots, so no arbitrary guess is used to choose one.
 */
export function xirr(cashFlows) {
  const grouped = new Map()
  for (const { date, amount } of cashFlows) {
    if (!(date instanceof Date) || !Number.isFinite(date.getTime()) || !Number.isFinite(amount)) {
      return unavailable('XIRR requires valid dated cash flows.')
    }
    const day = calendarDay(date)
    if (!grouped.has(day)) grouped.set(day, [])
    grouped.get(day).push(amount)
  }
  const flows = [...grouped].map(([day, amounts]) => [day, cashTotal(amounts)])
    .sort(([a], [b]) => a - b).filter(([, amount]) => amount !== 0)
  if (flows.some(([, amount]) => !Number.isFinite(amount))) return unavailable('Cash-flow totals exceed the supported numeric range.')
  if (flows.length < 2 || !flows.some(([, amount]) => amount < 0) || !flows.some(([, amount]) => amount > 0)) {
    return unavailable('XIRR requires payments and receipts on different dates.')
  }
  let positive = false
  for (const [, amount] of flows) {
    if (amount > 0) positive = true
    else if (positive) return unavailable('Cash flows change sign more than once; a unique XIRR is not guaranteed.')
  }
  const terms = flows.map(([day, amount]) => ({ years: (day - flows[0][0]) / 365, amount }))
  // Solve in log(1 + rate). Scaling by the largest term avoids overflow for
  // long histories, large amounts, and rates close to -100%.
  const npv = logRate => {
    const logs = terms.map(({ years, amount }) => Math.log(Math.abs(amount)) - years * logRate)
    const scale = logs.reduce((maximum, value) => Math.max(maximum, value), -Infinity)
    return terms.reduce((sum, term, index) => sum + Math.sign(term.amount) * Math.exp(logs[index] - scale), 0)
  }
  let low = Math.log(1e-10)
  let high = Math.log1p(1e6)
  if (npv(low) < 0 || npv(high) > 0) return unavailable('No XIRR was found within the supported rate range.')
  for (let iteration = 0; iteration < 160; iteration++) {
    const middle = (low + high) / 2
    if (npv(middle) > 0) low = middle
    else high = middle
  }
  return metric(Math.expm1((low + high) / 2) * 100)
}

function dailyFlows(history) {
  const days = new Map()
  for (const item of history) {
    const day = days.get(item.time) || { time: item.time, amounts: [], invalid: false }
    day.amounts.push(item.change)
    day.invalid ||= item.transactionType === 'BUY' && item.amount < 0
    days.set(item.time, day)
  }
  return [...days.values()].map(({ time, amounts, invalid }) => ({ time, change: cashTotal(amounts), invalid }))
}

// Snapshots are assumed to include that day's flows. A flow strictly between
// two snapshots cannot be removed accurately without its own valuation.
function timeWeighted(snapshots, flows) {
  const fail = reason => ({ ...unavailable(reason), points: [] })
  if (snapshots.length < 2) return fail(NEED_SNAPSHOTS)
  if (snapshots.some(row => row.value < 0)) return fail('Negative snapshots cannot support returns or drawdown.')
  const start = snapshots[0].date.getTime()
  const end = snapshots.at(-1).date.getTime()
  const included = flows.filter(flow => flow.time > start && flow.time <= end)
  let flowIndex = 0
  let index = 1
  let peak = 1
  let worst = 0
  const points = [{ ...snapshots[0], index: 100, drawdown: 0 }]
  for (let i = 1; i < snapshots.length; i++) {
    const previous = snapshots[i - 1]
    const current = snapshots[i]
    let change = 0
    while (flowIndex < included.length && included[flowIndex].time <= current.date.getTime()) {
      const flow = included[flowIndex++]
      if (flow.invalid || !Number.isFinite(flow.change)) return fail('Invalid purchase amounts prevent a reliable return.')
      if (flow.change !== 0 && flow.time !== current.date.getTime()) {
        return fail(`A value snapshot is missing on cash-flow date ${formatDisplayDate(new Date(flow.time))}.`)
      }
      change += flow.change
    }
    if (previous.value <= 0) return fail('A positive opening value is required for every return interval; zero balances break the series.')
    const beforeFlow = cashTotal([current.value, -change])
    if (beforeFlow < 0) return fail('A snapshot is smaller than its net contribution; cash-flow timing cannot support a return.')
    index *= beforeFlow / previous.value
    if (!Number.isFinite(index * 100)) return fail('The return exceeds the supported numeric range.')
    peak = Math.max(peak, index)
    const drawdown = (index / peak - 1) * 100
    worst = Math.min(worst, drawdown)
    points.push({ ...current, index: index * 100, drawdown })
  }
  return { ...metric((index - 1) * 100), points, maxDrawdown: worst, currentDrawdown: points.at(-1).drawdown }
}

function monthlyReturns(snapshots, flows) {
  if (snapshots.length < 2) return []
  const start = snapshots[0].date
  const end = snapshots.at(-1).date
  const cursor = new Date(start)
  cursor.setDate(cursor.getDate() + 1)
  cursor.setDate(1)
  const byDate = new Map(snapshots.map(row => [row.date.getTime(), row]))
  const rows = []
  while (cursor <= end) {
    const opening = new Date(cursor)
    opening.setDate(0)
    const closing = new Date(cursor)
    closing.setMonth(closing.getMonth() + 1, 0)
    const complete = opening >= start && closing <= end
    let result
    if (!complete) result = unavailable('Partial month: both month-end boundaries must be inside the covered period.')
    else if (!byDate.has(opening.getTime()) || !byDate.has(closing.getTime())) {
      result = unavailable('Missing opening or closing calendar month-end snapshot.')
    } else {
      result = timeWeighted(snapshots.filter(row => row.date >= opening && row.date <= closing), flows)
    }
    rows.push({ date: new Date(cursor), opening, closing, complete, value: result.value, reason: result.reason })
    cursor.setMonth(cursor.getMonth() + 1)
  }
  return rows
}

export function advancedReturns(transactions, values, fundId, fromDate = '', toDate = '') {
  // Highest value ID wins when multiple valid snapshots share a calendar date.
  const snapshots = [...new Map(valueHistory(values, fundId, fromDate, toDate)
    .map(row => [row.date.getTime(), row])).values()]
  const history = normalizedTransactions(transactions, fundId)
  const flows = dailyFlows(history)
  const first = snapshots[0]
  const last = snapshots.at(-1)
  const twr = timeWeighted(snapshots, flows)
  let moneyWeighted = unavailable(NEED_SNAPSHOTS)
  if (snapshots.length >= 2) {
    const included = flows.filter(flow => flow.time > first.date.getTime() && flow.time <= last.date.getTime())
    if (first.value <= 0 || last.value < 0) moneyWeighted = unavailable('XIRR needs a positive opening value and a nonnegative closing value.')
    else if (included.some(flow => flow.invalid)) moneyWeighted = unavailable('Negative purchase amounts prevent a reliable XIRR.')
    else moneyWeighted = xirr([
      { date: first.date, amount: -first.value },
      ...included.map(flow => ({ date: new Date(flow.time), amount: -flow.change })),
      { date: last.date, amount: last.value }
    ])
  }
  const monthly = monthlyReturns(snapshots, flows)
  const completeMonths = monthly.filter(row => row.complete)
  let volatility = unavailable('At least two consecutive complete monthly returns are required.')
  if (completeMonths.length >= 2) {
    if (completeMonths.some(row => row.value === null)) volatility = unavailable('Missing monthly returns prevent a continuous volatility sample.')
    else {
      const mean = completeMonths.reduce((sum, row) => sum + row.value, 0) / completeMonths.length
      volatility = metric(Math.sqrt(completeMonths.reduce((sum, row) => sum + (row.value - mean) ** 2, 0) / (completeMonths.length - 1)))
    }
  }
  const peak = snapshots.length && snapshots.every(row => row.value >= 0)
    ? snapshots.reduce((best, row) => row.value > best.value ? row : best) : null
  return {
    startDate: first?.date ?? null, endDate: last?.date ?? null, snapshotCount: snapshots.length,
    hasLaterTransactions: Boolean(last && history.some(row => row.time > last.date.getTime() &&
      (!toDate || row.time <= parseCalendarDate(toDate).getTime()))),
    xirr: moneyWeighted, twr,
    maxDrawdown: twr.value === null ? unavailable(twr.reason) : metric(twr.maxDrawdown),
    currentDrawdown: twr.value === null ? unavailable(twr.reason) : metric(twr.currentDrawdown),
    peakValue: peak ? { ...metric(peak.value), date: peak.date } : unavailable(snapshots.length
      ? 'Negative snapshots prevent a reliable peak value.' : 'No snapshot history is available.'),
    monthly, volatility,
    annualizedVolatility: volatility.value === null ? unavailable(volatility.reason) : metric(volatility.value * Math.sqrt(12)),
    volatilityMonths: completeMonths.filter(row => row.value !== null).length,
    observations: snapshots.map((row, i) => ({ ...row, index: twr.points[i]?.index ?? null, drawdown: twr.points[i]?.drawdown ?? null }))
  }
}
