import test from 'node:test'
import assert from 'node:assert/strict'
import { renderToStaticMarkup } from 'react-dom/server'
import { PortfolioAnalyticsView, PortfolioShare, PORTFOLIO_SELECTION } from './PortfolioAnalytics'
import { AppView } from '../App'

const portfolio = {
  asOfDate: '2026-02-28', totalValue: 400, knownValueTotal: 400, totalInvested: 270, totalBought: 350,
  gainLoss: 130, returnPercentage: 48.148148, fundCount: 2, valuedFundCount: 2,
  funds: [
    { mutualFundId: 1, mutualFundName: 'Alpha', brokerAccountId: 1, brokerName: 'Broker', accountId: 'A1',
      totalInvested: 120, totalBought: 150, totalValue: 150, valueAsOfDate: '2026-02-28', lastTransactionDate: '2026-02-28',
      gainLoss: 30, returnPercentage: 25, allocationPercentage: 37.5, contributionPercentage: 42.857143 },
    { mutualFundId: 2, mutualFundName: 'Beta', brokerAccountId: 2, brokerName: 'Broker', accountId: 'A2',
      totalInvested: 150, totalBought: 200, totalValue: 250, valueAsOfDate: '2026-02-27', lastTransactionDate: '2026-02-28',
      gainLoss: 100, returnPercentage: 66.666667, allocationPercentage: 62.5, contributionPercentage: 57.142857 }
  ],
  brokerAccounts: [
    { brokerAccountId: 1, brokerName: 'Broker', accountId: 'A1', totalValue: 150, knownValueTotal: 150,
      totalInvested: 120, fundCount: 1, valuedFundCount: 1, allocationPercentage: 37.5 },
    { brokerAccountId: 2, brokerName: 'Broker', accountId: 'A2', totalValue: 250, knownValueTotal: 250,
      totalInvested: 150, fundCount: 1, valuedFundCount: 1, allocationPercentage: 62.5 }
  ]
}

const render = props => renderToStaticMarkup(<PortfolioAnalyticsView data={portfolio} onSelectFund={() => {}} onRetry={() => {}} {...props} />)

test('portfolio renders totals, separate fund/broker allocations and contribution shares', () => {
  const html = render({ fromDate: '01-Feb-2026', toDate: '28-Feb-2026' })
  for (const text of ['400.00', '270.00', '130.00', '48.15%', '37.50%', '62.50%', '42.86%', '57.14%',
    'Alpha', 'Beta', 'A1', 'A2', 'Fund comparison and contribution shares', 'Allocation by broker account',
    'Transactions before 01-Feb-2026 are included', 'cumulative BUY amount']) assert.ok(html.includes(text), text)
  assert.equal((html.match(/<table>/g) || []).length, 2)
  assert.match(html, /aria-label="Value allocation for Alpha"/)
  assert.match(html, /aria-label="Contribution share for Beta"/)
  assert.match(html, /27-Feb-2026/)
  assert.match(html, /Some funds include transactions after their snapshot date/)
  assert.doesNotMatch(html, /NaN|Infinity/)
})

test('missing valuations are unavailable rather than displayed as zero or complete totals', () => {
  const data = { ...portfolio, totalValue: null, gainLoss: null, returnPercentage: null, knownValueTotal: 150, valuedFundCount: 1,
    funds: portfolio.funds.map((fund, index) => ({ ...fund, allocationPercentage: null, ...(index === 1 ? {
      totalValue: null, valueAsOfDate: null, gainLoss: null, returnPercentage: null } : {}) })),
    brokerAccounts: portfolio.brokerAccounts.map((broker, index) => ({ ...broker, allocationPercentage: null, ...(index === 1 ? {
      totalValue: null, knownValueTotal: 0, valuedFundCount: 0 } : {}) })) }
  const html = render({ data })
  assert.match(html, /1 funds have no value snapshot/)
  assert.match(html, /Known snapshot value: 150.00/)
  assert.match(html, /<dt>Total portfolio value<\/dt><dd>Unavailable<\/dd>/)
  assert.doesNotMatch(html, /<meter[^>]+aria-label="Value allocation/)
  assert.match(html, /aria-label="Contribution share for Beta"/)
})

test('zero percentages remain visible while unavailable percentages have no allocation meter', () => {
  const zero = renderToStaticMarkup(<PortfolioShare value={0} label="Zero share" />)
  assert.match(zero, /0.00%/)
  assert.match(zero, /value="0"/)
  const missing = renderToStaticMarkup(<PortfolioShare value={null} label="Unknown share" />)
  assert.match(missing, /Unavailable/)
  assert.doesNotMatch(missing, /<meter/)
})

test('loading, errors, and an empty portfolio suppress all totals and comparisons', () => {
  for (const [props, message] of [[{ loading: true }, 'Loading portfolio analytics'],
    [{ error: 'Request failed' }, 'Portfolio analytics could not be loaded'],
    [{ data: { fundCount: 0 } }, 'No funds in your portfolio']]) {
    const html = render(props)
    assert.ok(html.includes(message))
    assert.doesNotMatch(html, /<table>|<meter|400.00|Alpha/)
  }
  assert.match(render({ loading: true }), /role="status"/)
  assert.match(render({ error: 'Request failed' }), /role="alert"/)
})

function descendants(element) {
  if (!element || typeof element !== 'object') return []
  if (Array.isArray(element)) return element.flatMap(descendants)
  return [element, ...descendants(element.props?.children)]
}

test('fund drilldown and retry controls invoke their callbacks', () => {
  let selected
  let retries = 0
  const view = PortfolioAnalyticsView({ data: portfolio, onSelectFund: id => { selected = id } })
  const button = descendants(view).find(item => item.type === 'button' && item.props['aria-label'] === 'View history for Beta')
  button.props.onClick()
  assert.equal(selected, 2)
  const failed = PortfolioAnalyticsView({ error: 'Unavailable', onRetry: () => { retries++ } })
  descendants(failed).find(item => item.type === 'button').props.onClick()
  assert.equal(retries, 1)
})

test('analytics retains fund selection and date filters alongside an optional portfolio selection', () => {
  const html = renderToStaticMarkup(<AppView user={{ id: 1, username: 'alice' }} checking={false} path="/" activeTab="analytics"
    navigate={() => {}} logout={() => {}} loggedIn={() => {}} setActiveTab={() => {}} error="" />)
  assert.ok(html.includes(`value="${PORTFOLIO_SELECTION}"`))
  assert.match(html, /All funds/)
  assert.match(html, /Select Mutual Fund/)
  assert.match(html, /From date/)
  assert.match(html, /To date/)
})
