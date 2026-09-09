const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']

export function formatDisplayDate(value) {
  if (!value) return '-'
  const date = value instanceof Date ? value : /^\d{2}-[A-Z][a-z]{2}-\d{4}$/.test(value) ? parseCalendarDate(value) : new Date(value)
  if (Number.isNaN(date.getTime())) return '-'
  return `${String(date.getDate()).padStart(2, '0')}-${MONTHS[date.getMonth()]}-${date.getFullYear()}`
}


// Parse API calendar dates explicitly, without browser-dependent string parsing or UTC shifts.
export function parseCalendarDate(value) {
  const text = String(value)
  const display = /^(\d{2})-([A-Z][a-z]{2})-(\d{4})$/.exec(text)
  if (display) {
    const month = MONTHS.indexOf(display[2])
    if (month < 0) return new Date(NaN)
    const date = new Date(0)
    date.setFullYear(Number(display[3]), month, Number(display[1]))
    date.setHours(0, 0, 0, 0)
    return date.getMonth() === month && date.getDate() === Number(display[1]) ? date : new Date(NaN)
  }
  return new Date(`${text.slice(0, 10)}T00:00:00`)
}

export function dateRangeError(fromDate, toDate) {
  for (const value of [fromDate, toDate]) {
    if (value && (!/^\d{2}-[A-Z][a-z]{2}-\d{4}$/.test(value) ||
        Number.isNaN(parseCalendarDate(value).getTime()))) {
      return 'Enter dates in DD-Mon-YYYY format, such as 09-Sep-2026.'
    }
  }
  return fromDate && toDate && parseCalendarDate(fromDate) > parseCalendarDate(toDate)
    ? 'From date must be on or before the to date.'
    : ''
}

export function isWithinDateRange(value, fromDate, toDate) {
  const date = parseCalendarDate(value).getTime()
  return Number.isFinite(date) &&
    (!fromDate || date >= parseCalendarDate(fromDate).getTime()) &&
    (!toDate || date <= parseCalendarDate(toDate).getTime())
}
