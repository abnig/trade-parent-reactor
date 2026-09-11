const UNIT_TOLERANCE = 1e-9
const positiveNumber = value => value != null && String(value).trim() !== '' &&
  Number.isFinite(Number(value)) && Number(value) > 0

// All purchases on a calendar day enter the pool before that day's sales.
// This makes the result independent of API ordering when intraday order is unknown.
export function dailyHoldings(history) {
  const days = new Map()
  for (const item of history) {
    const day = days.get(item.time) || { bought: 0, sold: 0, cost: 0, validUnits: true, validCost: true }
    const buying = item.transactionType === 'BUY'
    if (!positiveNumber(item.units)) day.validUnits = false
    else day[buying ? 'bought' : 'sold'] += Number(item.units)
    if (buying) {
      day.cost += item.amount
      if (item.amount < 0) day.validCost = false
    }
    days.set(item.time, day)
  }
  let units = 0
  let cost = 0
  let validUnits = true
  let validCost = true
  return [...days].sort(([a], [b]) => a - b).map(([time, day]) => {
    validUnits = validUnits && day.validUnits
    validCost = validCost && day.validCost
    units += day.bought
    cost += day.cost
    const average = units > 0 ? cost / units : null
    units -= day.sold
    if (units < -UNIT_TOLERANCE) validUnits = false
    if (validUnits && Math.abs(units) <= UNIT_TOLERANCE) {
      units = 0
      cost = 0
    } else if (average !== null) {
      cost -= day.sold * average
    }
    return {
      date: new Date(time),
      units: validUnits ? units : null,
      averageCost: validUnits && validCost && units > 0 ? cost / units : null
    }
  })
}

