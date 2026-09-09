import { parseCalendarDate } from '../utils/date'

// Compare end-of-day values with all cash flows recorded on that calendar date.
export const historyDate = parseCalendarDate

export function investmentHistory(transactions, fundId, valueDates, fromDate = '', toDate = '') {
  const changes = new Map()
  for (const transaction of transactions) {
    if (String(transaction.mutualFundId) !== String(fundId)) continue
    const time = historyDate(transaction.txnDate).getTime()
    const amount = Number(transaction.amount)
    if (!Number.isFinite(time) || !Number.isFinite(amount) ||
        !['BUY', 'SELL'].includes(transaction.transactionType)) {
      throw new Error('Investment history contains an invalid transaction.')
    }
    const change = transaction.transactionType === 'SELL' ? -Math.abs(amount) : amount
    changes.set(time, (changes.get(time) || 0) + change)
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
