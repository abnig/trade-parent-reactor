import { historyDate, normalizedTransactions } from './investmentHistory'
import { dailyHoldings } from './holdingsBalances'
import { isWithinDateRange } from '../utils/date'

export function valueHistory(values, fundId, fromDate = '', toDate = '') {
  return values.filter(item => String(item.mutualFundId) === String(fundId))
    .map(item => ({ ...item, date: historyDate(item.valueAsOfDate), value: Number(item.totalValue) }))
    .filter(item => item.totalValue != null && String(item.totalValue).trim() !== '' &&
      Number.isFinite(item.value) && isWithinDateRange(item.valueAsOfDate, fromDate, toDate))
    .sort((a, b) => a.date - b.date || Number(a.valId) - Number(b.valId))
}

export function analyticsSummary(transactions, values, fundId, fromDate = '', toDate = '') {
  const history = normalizedTransactions(transactions, fundId)
    .filter(item => !toDate || item.time <= historyDate(toDate).getTime())
  const activity = history.filter(item => isWithinDateRange(item.txnDate, fromDate, toDate))
  const netInvestment = history.reduce((sum, item) => sum + item.change, 0)
  const openingInvestment = history.filter(item => fromDate && item.time < historyDate(fromDate).getTime())
    .reduce((sum, item) => sum + item.change, 0)
  const latest = valueHistory(values, fundId, fromDate, toDate).at(-1)
  const gainLoss = latest ? latest.value - netInvestment : null
  const units = dailyHoldings(history).at(-1)?.units ?? null
  return {
    netInvestment, openingInvestment, latestValue: latest?.value ?? null,
    valueDate: latest?.date ?? null, gainLoss,
    returnPercentage: gainLoss !== null && netInvestment > 0 ? gainLoss / netInvestment * 100 : null,
    units,
    totalBought: activity.filter(item => item.transactionType === 'BUY').reduce((sum, item) => sum + item.amount, 0),
    totalSold: activity.filter(item => item.transactionType === 'SELL').reduce((sum, item) => sum + Math.abs(item.amount), 0),
    transactionCount: activity.length,
    hasLaterTransactions: Boolean(latest && history.some(item => item.time > latest.date.getTime()))
  }
}

export function formatMetric(value, digits = 2) {
  return value == null || !Number.isFinite(value) ? 'Unavailable' :
    (Object.is(Number(value.toFixed(digits)), -0) ? 0 : value).toLocaleString(undefined, {
      minimumFractionDigits: digits, maximumFractionDigits: digits
    })
}
