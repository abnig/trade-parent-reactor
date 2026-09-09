import { formatDisplayDate, parseCalendarDate } from '../utils/date'

export function toPickerDate(value) {
  if (!value) return ''
  const date = parseCalendarDate(value)
  if (Number.isNaN(date.getTime())) return ''
  return `${String(date.getFullYear()).padStart(4, '0')}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

export function fromPickerDate(value) {
  return value ? formatDisplayDate(parseCalendarDate(value)) : ''
}

export default function DateInput({ value, onChange, ...props }) {
  return <span className="date-input">
    <input {...props} type="text" placeholder="DD-Mon-YYYY"
      pattern="[0-9]{2}-[A-Z][a-z]{2}-[0-9]{4}" title="Enter a date such as 09-Sep-2026"
      value={value} onChange={event => {
        const next = event.target.value
        event.target.setCustomValidity(next && !toPickerDate(next) ? 'Enter a valid date in DD-Mon-YYYY format.' : '')
        onChange(next)
      }} />
    <span className="date-input-calendar">
      <svg aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <rect x="3" y="5" width="18" height="16" rx="2" />
        <path d="M16 3v4M8 3v4M3 11h18" />
      </svg>
      <input type="date" aria-label="Choose date" title="Choose date"
        disabled={props.disabled} value={toPickerDate(value)}
        onChange={event => {
          const textInput = event.currentTarget.parentElement.previousElementSibling
          textInput.setCustomValidity('')
          onChange(fromPickerDate(event.target.value))
        }} />
    </span>
  </span>
}
