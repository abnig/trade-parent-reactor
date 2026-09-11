import { formatMetric } from './analyticsMetrics'
import { formatDisplayDate } from '../utils/date'
import { spacedDateTicks } from './chartTicks'

const HEIGHT = 320
const TOP = 20
const BOTTOM = 260
const LEFT = 110
const RIGHT = 25

function scale(values) {
  const min = values.reduce((result, value) => Math.min(result, value), 0)
  const max = values.reduce((result, value) => Math.max(result, value), 0)
  const upper = max === min ? min + 1 : max
  return {
    y: value => TOP + (upper - value) / (upper - min) * (BOTTOM - TOP),
    ticks: Array.from({ length: 5 }, (_, index) => min + (upper - min) * index / 4)
  }
}

function Grid({ width, y, ticks, digits = 2 }) {
  return <>{ticks.map(value => <g key={value}>
    <line x1={LEFT} x2={width - RIGHT} y1={y(value)} y2={y(value)} className="chart-grid-line" />
    <text x={LEFT - 12} y={y(value) + 4} textAnchor="end" className="chart-axis-text">{formatMetric(value, digits)}</text>
  </g>)}<line x1={LEFT} x2={width - RIGHT} y1={y(0)} y2={y(0)} className="chart-axis-line" /></>
}

export function CashFlowChart({ rows }) {
  const width = Math.max(680, LEFT + RIGHT + rows.length * 110)
  const groupWidth = (width - LEFT - RIGHT) / rows.length
  const { y, ticks } = scale(rows.flatMap(row => [row.bought, -row.sold, row.net]))
  const series = [{ key: 'bought', label: 'BUY', color: '#2563eb', sign: 1 },
    { key: 'sold', label: 'SELL', color: '#c2410c', sign: -1 },
    { key: 'net', label: 'Net flow', color: '#172033', sign: 1 }]
  return <>
    <p className="chart-explanation">BUY adds investment; SELL is shown below zero as an outflow. Net flow = BUY − SELL.</p>
    <div className="chart-legend">{series.map(item => <span key={item.key}><i style={{ background: item.color }} />{item.label}</span>)}</div>
    <div className="analytics-chart-wrap" role="region" aria-label="Scrollable cash-flow chart" tabIndex={0}>
      <svg className="analytics-detail-chart" style={{ minWidth: width }} viewBox={`0 0 ${width} ${HEIGHT}`} role="img" aria-label="BUY, SELL, and net cash flows by period; exact amounts are in the table below">
        <Grid width={width} y={y} ticks={ticks} />
        {rows.map((row, index) => {
          const center = LEFT + groupWidth * (index + 0.5)
          return <g key={row.date.getTime()}>
            {series.map((item, offset) => {
              const value = row[item.key] * item.sign
              return <rect key={item.key} x={center + (offset - 1.5) * 22} y={Math.min(y(value), y(0))}
                width={18} height={Math.abs(y(value) - y(0))} fill={item.color}>
                <title>{`${row.label}: ${item.label} ${formatMetric(value)}`}</title>
              </rect>
            })}
            <text x={center} y={BOTTOM + 28} textAnchor="middle" className="chart-axis-text">{row.label}</text>
          </g>
        })}
      </svg>
    </div>
  </>
}

export function HoldingsChart({ rows, metric, label, digits }) {
  const known = rows.filter(row => row[metric] !== null)
  if (!known.length) return <p className="analytics-detail-empty">{label} unavailable for this range.</p>
  const width = Math.max(680, rows.length * 30)
  const start = rows[0].date.getTime()
  const span = rows.at(-1).date.getTime() - start
  const x = date => span === 0 ? (LEFT + width - RIGHT) / 2 : LEFT + (date.getTime() - start) / span * (width - LEFT - RIGHT)
  const { y, ticks } = scale(known.map(row => row[metric]))
  let connected = false
  const path = rows.map(row => {
    if (row[metric] === null) {
      connected = false
      return ''
    }
    const command = connected ? `H ${x(row.date)} V ${y(row[metric])}` : `M ${x(row.date)} ${y(row[metric])}`
    connected = true
    return command
  }).join(' ')
  const dates = spacedDateTicks(rows.map(row => ({ ...row, x: x(row.date) })))
  return <div className="analytics-chart-wrap" role="region" aria-label={`Scrollable ${label.toLowerCase()} chart`} tabIndex={0}>
    <svg className="analytics-detail-chart" style={{ minWidth: width }} viewBox={`0 0 ${width} ${HEIGHT}`} role="img" aria-label={`${label} over time; unavailable balances are gaps; exact values are in the holdings table`}>
      <Grid width={width} y={y} ticks={ticks} digits={digits} />
      <path d={path} fill="none" className="chart-line" />
      {known.map(row => <circle key={row.date.getTime()} cx={x(row.date)} cy={y(row[metric])} r={4} className="chart-point">
        <title>{`${formatDisplayDate(row.date)}: ${label} ${formatMetric(row[metric], digits)}`}</title>
      </circle>)}
      {dates.map((row, index) => <text key={row.date.getTime()} x={row.x} y={BOTTOM + 28}
        textAnchor={dates.length === 1 ? 'middle' : index === 0 ? 'start' : index === dates.length - 1 ? 'end' : 'middle'}
        className="chart-axis-text">{formatDisplayDate(row.date)}</text>)}
    </svg>
  </div>
}
