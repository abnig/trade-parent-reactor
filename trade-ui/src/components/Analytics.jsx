import DateInput from './DateInput'
import { spacedDateTicks } from './chartTicks'
import { useEffect, useMemo, useState } from 'react'
import api from '../api/api'
import { historyDate, investmentHistory } from './investmentHistory'
import { formatDisplayDate, dateRangeError, isWithinDateRange } from '../utils/date'


const formatValue = (value) =>
  Number(value).toLocaleString(undefined, {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  })

const formatDate = (value) => {
  return formatDisplayDate(value)
}

const formatAxisValue = (value) => {
  const number = Number(value)

  if (Math.abs(number) >= 1000000) {
    return `${(number / 1000000).toFixed(1)}M`
  }

  if (Math.abs(number) >= 1000) {
    return `${(number / 1000).toFixed(0)}K`
  }

  return number.toFixed(0)
}

export default function Analytics() {
  const [funds, setFunds] = useState([])
  const [values, setValues] = useState([])
  const [transactions, setTransactions] = useState([])
  const [selectedFundId, setSelectedFundId] = useState('')
  const [fromDate, setFromDate] = useState('')
  const [toDate, setToDate] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const rangeError = dateRangeError(fromDate, toDate)

  useEffect(() => {
    let active = true
    const load = async () => {
      try {
        setLoading(true)

        const fundData = await api.analytics.funds()
        if (!active) return

        setFunds(fundData)
        setError('')
      } catch (e) {
        if (active) setError(e.message || 'Failed to load analytics data.')
      } finally {
        if (active) setLoading(false)
      }
    }

    load()
    return () => { active = false }
  }, [])

  useEffect(() => {
    let active = true
    if (!selectedFundId) {
      setValues([])
      setTransactions([])
      return () => { active = false }
    }

    setValues([])
    setTransactions([])
    const loadHistory = async () => {
      try {
        setLoading(true)
        const [response, transactionData] = await Promise.all([
          api.analytics.valueHistory(selectedFundId),
          api.analytics.transactionHistory(selectedFundId)
        ])
        if (!active) return
        // Validate before displaying a potentially misleading comparison.
        investmentHistory(transactionData, selectedFundId, [])
        setValues(response)
        setTransactions(transactionData)
        setError('')
      } catch (e) {
        if (active) setError(e.message || 'Failed to load value history.')
      } finally {
        if (active) setLoading(false)
      }
    }
    loadHistory()
    return () => { active = false }
  }, [selectedFundId])

  const selectedFund = funds.find(
    (fund) =>
      String(fund.mutualFundId) === String(selectedFundId)
  )

  /*
   * Get values for the selected mutual fund
   * and sort them by Value As Of date ascending.
   */
  const chartData = useMemo(() => {
    if (!selectedFundId) {
      return []
    }

    return values
      .filter(
        (item) =>
          String(item.mutualFundId) === String(selectedFundId)
      )
      .map((item) => ({
        ...item,
        date: historyDate(item.valueAsOfDate),
        value: Number(item.totalValue)
      }))
      .filter(
        (item) =>
          !Number.isNaN(item.date.getTime()) &&
          Number.isFinite(item.value) &&
          isWithinDateRange(item.valueAsOfDate, fromDate, toDate)
      )
      .sort(
        (a, b) =>
          a.date.getTime() - b.date.getTime()
      )
  }, [values, selectedFundId, fromDate, toDate])

  const investments = useMemo(() => investmentHistory(
    transactions, selectedFundId, chartData.map(item => item.date), fromDate, toDate
  ), [transactions, selectedFundId, chartData, fromDate, toDate])

  /*
   * Calculate SVG chart coordinates.
   */
  const chart = useMemo(() => {
    const width = Math.max(1400, investments.length * 40)
    const height = 480

    const margin = {
      top: 28,
      right: 40,
      bottom: 72,
      left: 82
    }

    const plotWidth =
      width - margin.left - margin.right

    const plotHeight =
      height - margin.top - margin.bottom

    if (!chartData.length) {
      return {
        width,
        height,
        margin,
        plotWidth,
        plotHeight,
        points: [],
        investmentPoints: [],
        yTicks: [],
        xTicks: []
      }
    }

    const rawMin = investments.reduce((min, item) => Math.min(min, item.invested),
      chartData.reduce((min, item) => Math.min(min, item.value), Infinity))

    const rawMax = investments.reduce((max, item) => Math.max(max, item.invested),
      chartData.reduce((max, item) => Math.max(max, item.value), -Infinity))

    const range = rawMax - rawMin

    const padding =
      range === 0
        ? Math.max(Math.abs(rawMax) * 0.05, 1)
        : range * 0.1

    const minValue = Math.max(
      rawMin < 0 ? -Infinity : 0,
      rawMin - padding
    )

    const maxValue = rawMax + padding

    const minTime =
      investments[0].date.getTime()

    const maxTime =
      investments[investments.length - 1].date.getTime()

    const timeRange =
      maxTime - minTime

    const x = (time) =>
      timeRange === 0
        ? margin.left + plotWidth / 2
        : margin.left +
          ((time - minTime) / timeRange) *
            plotWidth

    const y = (value) =>
      margin.top +
      ((maxValue - value) /
        (maxValue - minValue)) *
        plotHeight

    const investedByDate = new Map(investments.map(item => [item.date.getTime(), item.invested]))
    const investmentPoints = investments.map(item => ({ ...item, x: x(item.date.getTime()), y: y(item.invested) }))
    const points = chartData.map((item) => ({
      ...item,
      invested: investedByDate.get(item.date.getTime()),
      x: x(item.date.getTime()),
      y: y(item.value)
    }))

    const tickCount = 5

    const yTicks = Array.from(
      { length: tickCount + 1 },
      (_, index) => {
        const value =
          minValue +
          ((maxValue - minValue) * index) /
            tickCount

        return {
          value,
          y: y(value)
        }
      }
    ).reverse()

    const xTicks = spacedDateTicks(investmentPoints)

    return {
      width,
      height,
      margin,
      plotWidth,
      plotHeight,
      points,
      investmentPoints,
      yTicks,
      xTicks
    }
  }, [chartData, investments])

  const linePoints = chart.points
    .map(
      (point) =>
        `${point.x},${point.y}`
    )
    .join(' ')

  const investmentPath = chart.investmentPoints.map((point, index) =>
    index === 0 ? `M ${point.x} ${point.y}` : `H ${point.x} V ${point.y}`
  ).join(' ')

  return (
    <section>
      <div className="section-header">
        <div>
          <h2>Analytics</h2>
          <p>
            View the historical value trend
            for a mutual fund.
          </p>
        </div>
      </div>

      {error && (
        <div className="error">
          {error}
        </div>
      )}

      {/* Mutual Fund Selection */}
      <div className="filter-card analytics-filter">
        <label>
          Select Mutual Fund

          <select
            value={selectedFundId}
            onChange={(event) =>
              setSelectedFundId(
                event.target.value
              )
            }
            disabled={loading}
          >
            <option value="">
              Select Mutual Fund
            </option>

            {funds.map((fund) => (
              <option
                key={fund.mutualFundId}
                value={fund.mutualFundId}
              >
                {fund.mutualFundName}
              </option>
            ))}
          </select>
        </label>
        <label>
          From date
          <DateInput
            aria-invalid={Boolean(rangeError)}
            aria-describedby={rangeError ? "date-range-error" : undefined}
            value={fromDate}
            onChange={setFromDate}
            disabled={loading}
          />
        </label>
        <label>
          To date
          <DateInput
            aria-invalid={Boolean(rangeError)}
            aria-describedby={rangeError ? "date-range-error" : undefined}
            value={toDate}
            onChange={setToDate}
            disabled={loading}
          />
        </label>
      </div>

      {rangeError && (
        <div id="date-range-error" className="error" role="alert">{rangeError}</div>
      )}

      {/* No fund selected */}
      {!selectedFundId ? (
        <div className="analytics-empty">
          <h3>
            Select a mutual fund
          </h3>

          <p>
            Choose a mutual fund above
            to view its value history.
          </p>
        </div>

      ) : loading ? (
        <div className="analytics-empty" role="status">Loading value history…</div>

      ) : error ? (
        <div className="analytics-empty">Value history could not be loaded. Please try again.</div>

      ) : rangeError ? (
        <div className="analytics-empty">
          <h3>Invalid date range</h3>
          <p>{rangeError}</p>
        </div>

      ) : chartData.length === 0 ? (

        /* No data */
        <div className="analytics-empty">
          <h3>
            No value history available
          </h3>

          <p>
            {selectedFund?.mutualFundName ||
              'The selected fund'}{' '}
            has no fund values to plot.
          </p>
        </div>

      ) : (

        /* Chart */
        <div className="analytics-card">

          <div className="analytics-card-header">
            <div>
              <h3>
                {selectedFund?.mutualFundName ||
                  'Mutual Fund'}
              </h3>

              <p>
                Total value and cumulative net investment over time
              </p>
            </div>

            <div className="analytics-summary">
              <span>
                {chartData.length}{' '}
                data point
                {chartData.length === 1
                  ? ''
                  : 's'}
              </span>

              <strong>
                {formatValue(
                  chartData[
                    chartData.length - 1
                  ].value
                )}
              </strong>

              <small>
                Latest total value
              </small>
            </div>
          </div>

          <div className="chart-legend">
            <span><i className="chart-value-swatch" />Total value</span>
            <span><i className="chart-investment-swatch" />Cumulative net investment</span>
          </div>
          <p className="chart-explanation">Net investment = BUY amounts − SELL amounts. Hover over a value marker for the date, investment, and gain/loss.</p>
          <div
            className="analytics-chart-wrap"
            role="region"
            aria-label="Scrollable analytics graph"
            tabIndex={0}
          >

            <svg
              className="analytics-chart"
              style={{ minWidth: chart.width }}
              viewBox={`0 0 ${chart.width} ${chart.height}`}
              role="img"
              aria-label={`Total value and cumulative net investment trend for ${
                selectedFund?.mutualFundName ||
                'selected mutual fund'
              }`}
            >

              {/* Y-axis grid and labels */}
              {chart.yTicks.map((tick) => (
                <g key={tick.value}>

                  <line
                    x1={chart.margin.left}
                    x2={
                      chart.width -
                      chart.margin.right
                    }
                    y1={tick.y}
                    y2={tick.y}
                    className="chart-grid-line"
                  />

                  <text
                    x={
                      chart.margin.left - 12
                    }
                    y={tick.y + 5}
                    textAnchor="end"
                    className="chart-axis-text"
                  >
                    {formatAxisValue(
                      tick.value
                    )}
                  </text>

                </g>
              ))}

              {/* Y axis */}
              <line
                x1={chart.margin.left}
                x2={chart.margin.left}
                y1={chart.margin.top}
                y2={
                  chart.height -
                  chart.margin.bottom
                }
                className="chart-axis-line"
              />

              {/* X axis */}
              <line
                x1={chart.margin.left}
                x2={
                  chart.width -
                  chart.margin.right
                }
                y1={
                  chart.height -
                  chart.margin.bottom
                }
                y2={
                  chart.height -
                  chart.margin.bottom
                }
                className="chart-axis-line"
              />

              {/* X-axis date grid, markers, and labels */}
              {chart.xTicks.map((tick, index) => (
                <g
                  key={tick.date.getTime()}
                >
                  <line
                    x1={tick.x}
                    x2={tick.x}
                    y1={chart.margin.top}
                    y2={chart.height - chart.margin.bottom}
                    className="chart-grid-line"
                  />

                  <line
                    x1={tick.x}
                    x2={tick.x}
                    y1={
                      chart.height -
                      chart.margin.bottom
                    }
                    y2={
                      chart.height -
                      chart.margin.bottom +
                      7
                    }
                    className="chart-axis-line"
                  />

                  <text
                    x={tick.x}
                    y={
                      chart.height -
                      chart.margin.bottom +
                      28
                    }
                    textAnchor={
                      chart.xTicks.length === 1 ? 'middle'
                        : index === 0 ? 'start'
                          : index === chart.xTicks.length - 1 ? 'end' : 'middle'
                    }
                    className="chart-axis-text"
                  >
                    {formatDate(
                      tick.date
                    )}
                  </text>
                </g>
              ))}

              {/* Line */}
              {chart.points.length > 1 && (
                <polyline
                  points={linePoints}
                  fill="none"
                  className="chart-line"
                />
              )}

              {/* Data points */}
              <path d={investmentPath} fill="none" className="chart-investment-line" />
              {chart.investmentPoints.map(point => (
                <circle key={point.date.getTime()} cx={point.x} cy={point.y} r="3" fill="black">
                  <title>{formatDate(point.date)}: Net investment {formatValue(point.invested)}</title>
                </circle>
              ))}
              {chart.points.map((point) => (
                <circle
                  key={point.valId}
                  cx={point.x}
                  cy={point.y}
                  r="5"
                  className="chart-point"
                >
                  <title>
                    {formatDate(
                      point.valueAsOfDate
                    )}
                    :{' '}
                    Total value {formatValue(point.value)}
                    {'; Net investment '}{formatValue(point.invested)}
                    {'; Gain/loss '}{formatValue(point.value - point.invested)}
                  </title>
                </circle>
              ))}

              {/* Y-axis title */}
              <text
                x={18}
                y={
                  chart.margin.top +
                  chart.plotHeight / 2
                }
                textAnchor="middle"
                transform={`rotate(-90 18 ${
                  chart.margin.top +
                  chart.plotHeight / 2
                })`}
                className="chart-axis-title"
              >
                Total Value
              </text>

              {/* X-axis title */}
              <text
                x={
                  chart.margin.left +
                  chart.plotWidth / 2
                }
                y={chart.height - 10}
                textAnchor="middle"
                className="chart-axis-title"
              >
                Value As Of
              </text>

            </svg>
          </div>
        </div>
      )}
    </section>
  )
}
