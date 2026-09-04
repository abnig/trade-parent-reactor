import { useEffect, useMemo, useState } from 'react'
import api from '../api/api'
import Pagination from './Pagination'

const initialPage = { page: 0, size: 20, totalPages: 0, totalElements: 0, first: true, last: true }

const formatValue = (value) =>
  Number(value).toLocaleString(undefined, {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  })

const formatDate = (value) => {
  if (!value) return '-'
  return new Date(value).toLocaleDateString()
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
  const [selectedFundId, setSelectedFundId] = useState('')
  const [fundPaging, setFundPaging] = useState(initialPage)
  const [historyComplete, setHistoryComplete] = useState(true)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    const load = async () => {
      try {
        setLoading(true)

        const fundData = await api.mutualFunds.all(fundPaging)

        setFunds(fundData.content)
        setFundPaging(fundData)
        setError('')
      } catch (e) {
        setError(e.message || 'Failed to load analytics data.')
      } finally {
        setLoading(false)
      }
    }

    load()
  }, [fundPaging.page, fundPaging.size])

  useEffect(() => {
    let active = true
    if (!selectedFundId) {
      setValues([])
      setHistoryComplete(true)
      return () => { active = false }
    }

    const loadHistory = async () => {
      try {
        setLoading(true)
        const response = await api.values.byFund(selectedFundId, { page: 0, size: 100 })
        if (!active) return
        setValues(response.content)
        setHistoryComplete(response.last)
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
    if (!selectedFundId || !historyComplete) {
      return []
    }

    return values
      .filter(
        (item) =>
          String(item.mutualFundId) === String(selectedFundId)
      )
      .map((item) => ({
        ...item,
        date: new Date(item.valueAsOfDate),
        value: Number(item.totalValue)
      }))
      .filter(
        (item) =>
          !Number.isNaN(item.date.getTime()) &&
          Number.isFinite(item.value)
      )
      .sort(
        (a, b) =>
          a.date.getTime() - b.date.getTime()
      )
  }, [values, selectedFundId])

  /*
   * Calculate SVG chart coordinates.
   */
  const chart = useMemo(() => {
    const width = 1000
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
        yTicks: [],
        xTicks: []
      }
    }

    const rawMin = Math.min(
      ...chartData.map((item) => item.value)
    )

    const rawMax = Math.max(
      ...chartData.map((item) => item.value)
    )

    const range = rawMax - rawMin

    const padding =
      range === 0
        ? Math.max(Math.abs(rawMax) * 0.05, 1)
        : range * 0.1

    const minValue = Math.max(
      0,
      rawMin - padding
    )

    const maxValue = rawMax + padding

    const minTime =
      chartData[0].date.getTime()

    const maxTime =
      chartData[chartData.length - 1].date.getTime()

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

    const points = chartData.map((item) => ({
      ...item,
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

    const maxXTicks = 6

    const step =
      chartData.length <= maxXTicks
        ? 1
        : Math.ceil(
            chartData.length / maxXTicks
          )

    const xTicks = chartData
      .map((item, index) => ({
        ...item,
        index
      }))
      .filter(
        (item, index) =>
          index % step === 0 ||
          index === chartData.length - 1
      )
      .reduce((result, item) => {
        if (
          !result.some(
            (existing) =>
              existing.index === item.index
          )
        ) {
          result.push(item)
        }

        return result
      }, [])

    return {
      width,
      height,
      margin,
      plotWidth,
      plotHeight,
      points,
      yTicks,
      xTicks
    }
  }, [chartData])

  const linePoints = chart.points
    .map(
      (point) =>
        `${point.x},${point.y}`
    )
    .join(' ')

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
      </div>

      {!loading && <Pagination {...fundPaging}
        onPrevious={() => setFundPaging((current) => ({ ...current, page: current.page - 1 }))}
        onNext={() => setFundPaging((current) => ({ ...current, page: current.page + 1 }))}
        onSizeChange={(size) => setFundPaging((current) => ({ ...current, page: 0, size }))}
      />}

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

      ) : !historyComplete ? (
        <div className="analytics-empty">
          <h3>Complete value history is not available</h3>
          <p>This fund has more than 100 valuation records. A dedicated backend history endpoint is needed before this chart can be rendered accurately.</p>
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
                Total value over time
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

          <div className="analytics-chart-wrap">

            <svg
              className="analytics-chart"
              viewBox={`0 0 ${chart.width} ${chart.height}`}
              role="img"
              aria-label={`Total value trend for ${
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

              {/* X-axis dates */}
              {chart.xTicks.map((tick) => (
                <g
                  key={`${tick.valId}-${tick.index}`}
                >
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
                    textAnchor="middle"
                    className="chart-axis-text"
                  >
                    {formatDate(
                      tick.valueAsOfDate
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
                    {formatValue(
                      point.value
                    )}
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
