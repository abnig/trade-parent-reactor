import { historyDate, normalizedTransactions } from './investmentHistory'
import { valueHistory } from './analyticsMetrics'
import { formatDisplayDate } from '../utils/date'

import { dailyHoldings } from './holdingsBalances'

function rangeBounds(history, snapshots, fromDate, toDate) {
  const times = [...history.map(item => item.time), ...snapshots.map(item => item.date.getTime())]
  let start = fromDate ? historyDate(fromDate).getTime() : times.reduce((a, b) => Math.min(a, b), Infinity)
  let end = toDate ? historyDate(toDate).getTime() : times.reduce((a, b) => Math.max(a, b), -Infinity)
  if (!fromDate && toDate) start = Math.min(start, end)
  if (!toDate && fromDate) end = Math.max(start, end)
  return Number.isFinite(start) && Number.isFinite(end) && start <= end ? { start, end } : null
}

export function transactionActivity(transactions, fundId, fromDate = '', toDate = '') {
  const start = fromDate ? historyDate(fromDate).getTime() : -Infinity
  const end = toDate ? historyDate(toDate).getTime() : Infinity
  return normalizedTransactions(transactions, fundId)
    .filter(item => item.time >= start && item.time <= end)
    .sort((a, b) => b.time - a.time || Number(b.mutualFundTxnId || 0) - Number(a.mutualFundTxnId || 0))
}

export function cashFlowHistory(transactions, values, fundId, fromDate = '', toDate = '', period = 'monthly') {
  if (!['monthly', 'quarterly'].includes(period)) throw new Error('Invalid cash-flow period.')
  const history = normalizedTransactions(transactions, fundId)
  const bounds = rangeBounds(history, valueHistory(values, fundId), fromDate, toDate)
  if (!bounds) return []
  const step = period === 'quarterly' ? 3 : 1
  const periodStart = time => {
    const date = new Date(time)
    date.setDate(1)
    date.setMonth(Math.floor(date.getMonth() / step) * step)
    return date
  }
  const totals = new Map()
  for (const item of history) {
    if (item.time < bounds.start || item.time > bounds.end) continue
    const time = periodStart(item.time).getTime()
    const total = totals.get(time) || { bought: 0, sold: 0, count: 0 }
    total[item.transactionType === 'BUY' ? 'bought' : 'sold'] +=
      item.transactionType === 'BUY' ? item.amount : Math.abs(item.amount)
    total.count += 1
    totals.set(time, total)
  }
  const result = []
  for (let date = periodStart(bounds.start); date.getTime() <= bounds.end;) {
    const next = new Date(date)
    next.setMonth(next.getMonth() + step)
    const last = new Date(next)
    last.setDate(last.getDate() - 1)
    const total = totals.get(date.getTime()) || { bought: 0, sold: 0, count: 0 }
    result.push({
      date: new Date(date),
      label: step === 3 ? `Q${Math.floor(date.getMonth() / 3) + 1} ${date.getFullYear()}` : formatDisplayDate(date).slice(3),
      start: new Date(Math.max(date.getTime(), bounds.start)),
      end: new Date(Math.min(last.getTime(), bounds.end)),
      ...total, net: total.bought - total.sold
    })
    date = next
  }
  return result
}

export function holdingsHistory(transactions, values, fundId, fromDate = '', toDate = '') {
  const history = normalizedTransactions(transactions, fundId)
  const snapshots = valueHistory(values, fundId)
  const bounds = rangeBounds(history, snapshots, fromDate, toDate)
  if (!bounds) return []
  const balances = dailyHoldings(history)
  const times = [...new Set([bounds.start, bounds.end,
    ...history.map(item => item.time), ...snapshots.map(item => item.date.getTime())])]
    .filter(time => time >= bounds.start && time <= bounds.end).sort((a, b) => a - b)
  let index = 0
  let balance = { units: null, averageCost: null }
  return times.map(time => {
    while (index < balances.length && balances[index].date.getTime() <= time) balance = balances[index++]
    return { ...balance, date: new Date(time) }
  })
}
