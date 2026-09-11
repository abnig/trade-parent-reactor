import { parseCalendarDate } from '../utils/date'

// Compare end-of-day values with all cash flows recorded on that calendar date.
export const historyDate = parseCalendarDate

export function investmentHistory(transactions, fundId, valueDates, fromDate = '', toDate = '') {
  const changes = new Map()
  for (const transaction of normalizedTransactions(transactions, fundId)) {
    changes.set(transaction.time, (changes.get(transaction.time) || 0) + transaction.change)
  }
  const times = [...new Set([...changes.keys(), ...valueDates.map(date => date.getTime())])].sort((a, b) => a - b)
  let invested = 0
  const fromTime = fromDate ? historyDate(fromDate).getTime() : -Infinity
  const toTime = toDate ? historyDate(toDate).getTime() : Infinity
  // Accumulate the full history before filtering so the opening balance carries forward.
  return times.map(time => {
    invested += changes.get(time) || 0
    return { date: new Date(time), invested }
  }).filter(point => point.date.getTime() >= fromTime && point.date.getTime() <= toTime)
}

// Shared validation and signed cash flows for both the chart and summary.
export function normalizedTransactions(transactions, fundId) {
  return transactions.filter(item => String(item.mutualFundId) === String(fundId)).map(item => {
    const time = historyDate(item.txnDate).getTime()
    const amount = Number(item.amount)
    if (!Number.isFinite(time) || item.amount == null || String(item.amount).trim() === '' ||
        !Number.isFinite(amount) || !['BUY', 'SELL'].includes(item.transactionType)) {
      throw new Error('Investment history contains an invalid transaction.')
    }
    return { ...item, time, amount, change: item.transactionType === 'SELL' ? -Math.abs(amount) : amount }
  }).sort((a, b) => a.time - b.time)
}
