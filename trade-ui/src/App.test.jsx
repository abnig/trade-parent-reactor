import test from 'node:test'
import assert from 'node:assert/strict'
import { renderToStaticMarkup } from 'react-dom/server'
import { AppView } from './App'
import { formatDisplayDate, dateRangeError, isWithinDateRange } from './utils/date'

test('formats analytics, transaction, and fund value dates as DD-Mon-YYYY', () => {
  assert.equal(formatDisplayDate('2026-09-08T15:30:00Z'), '08-Sep-2026')
  assert.equal(formatDisplayDate(null), '-')
})
import { historyDate, investmentHistory } from './components/investmentHistory'

test('investment history includes earlier purchases, same-day sales, and transaction-only dates', () => {
  const transaction = (txnDate, amount, transactionType = 'BUY', mutualFundId = 1) =>
    ({ txnDate, amount, transactionType, mutualFundId })
  const history = investmentHistory([
    transaction('2026-02-01T18:00:00', 30, 'SELL'),
    transaction('2026-01-01T12:00:00', 100),
    transaction('2026-02-01T09:00:00', 50),
    transaction('2026-01-01', 999, 'BUY', 2),
    transaction('2026-03-01', 200, 'SELL')
  ], '1', ['2025-12-01', '2026-02-01', '2026-04-01'].map(historyDate))
  assert.deepEqual(history.map(point => point.invested), [0, 100, 120, -80, -80])
  assert.equal(history[2].date.getTime(), historyDate('2026-02-01').getTime())
})

test('date ranges carry forward cumulative investment without including future transactions', () => {
  const transactions = [
    { mutualFundId: 1, txnDate: '2026-01-01', amount: 100, transactionType: 'BUY' },
    { mutualFundId: 1, txnDate: '2026-01-15', amount: 30, transactionType: 'SELL' },
    { mutualFundId: 2, txnDate: '2026-01-01', amount: 999, transactionType: 'BUY' },
    { mutualFundId: 1, txnDate: '2026-03-01', amount: 50, transactionType: 'BUY' }
  ]
  const dates = ['2026-02-01', '2026-02-28'].map(historyDate)
  const history = investmentHistory(transactions, '1', dates, '2026-02-01', '2026-02-28')
  assert.deepEqual(history, dates.map(date => ({ date, invested: 70 })))
  assert.deepEqual(investmentHistory(transactions, 1, dates, '2026-02-01', '2026-03-01')
    .map(point => point.invested), [70, 70, 120])
  assert.deepEqual(investmentHistory(transactions, 1, [historyDate('2025-12-01')], '', '2025-12-31')
    .map(point => point.invested), [0])
  assert.deepEqual(investmentHistory(transactions, 1, [historyDate('2026-04-01')], '2026-04-01')
    .map(point => point.invested), [120])
})

test('investment history handles no transactions and rejects invalid selected-fund data', () => {
  assert.deepEqual(investmentHistory([], 1, [historyDate('2026-01-01')]).map(point => point.invested), [0])
  assert.throws(() => investmentHistory([
    { mutualFundId: 1, txnDate: 'invalid', amount: 100, transactionType: 'BUY' }
  ], 1, []), /invalid transaction/)
})

function render(properties = {}) {
  return renderToStaticMarkup(<AppView user={null} checking={false} path="/" activeTab="brokers" error=""
    navigate={() => {}} logout={() => {}} loggedIn={() => {}} setActiveTab={() => {}} {...properties} />)
}

test('public home contains only account links and no portfolio or authentication forms', () => {
  const html = render()
  assert.match(html, /href="\/register"/)
  assert.match(html, /href="\/login"/)
  assert.doesNotMatch(html, /Broker Accounts|Transactions|Fund Values|Analytics|<form|<table/)
})

test('session verification does not mount data views or flash guest links', () => {
  const html = render({ checking: true, user: { id: 1, username: 'alice' } })
  assert.match(html, /Checking session/)
  assert.doesNotMatch(html, /Broker Accounts|href="\/register"|href="\/login"|<table/)
})

test('authenticated dashboard hides Register and Login and shows Logout', () => {
  const html = render({ user: { id: 1, username: 'alice' } })
  assert.match(html, /alice/)
  assert.match(html, /Logout/)
  assert.match(html, /Broker Accounts/)
  assert.doesNotMatch(html, /href="\/register"|href="\/login"/)
})

test('register and login routes render their forms without portfolio tabs', () => {
  const registration = render({ path: '/register' })
  assert.match(registration, /Create account/)
  assert.match(registration, /Confirm password/)
  assert.doesNotMatch(registration, /Broker Accounts|Analytics/)
  const login = render({ path: '/login' })
  assert.match(login, /autoComplete="current-password"/)
  assert.doesNotMatch(login, /Broker Accounts|Analytics/)
})

test('clearing the user removes portfolio content', () => {
  assert.match(render({ user: { id: 1, username: 'alice' } }), /Broker Accounts/)
  assert.doesNotMatch(render({ user: null }), /Broker Accounts|alice|Logout|<table/)
})


test('analytics shows a fund selector without pagination controls', () => {
  const html = render({ user: { id: 1, username: 'alice' }, activeTab: 'analytics' })
  assert.match(html, /Select Mutual Fund/)
  assert.match(html, /From date/)
  assert.match(html, /To date/)
  assert.doesNotMatch(html, /aria-label="Pagination"|Rows per page|Previous page|Next page/)
})

import { ProfileForm, profileFormValues, profileUpdatePayload } from './components/Profile'

const profile = { userId: 1, username: 'alice', email: 'alice@example.com', firstName: 'Alice', lastName: null,
  phoneNumber: null, avatarUrl: null, hintQuestion: 1, hintAnswerSet: true }
const questions = [{ id: 1, text: 'First school?' }, { id: 2, text: 'First pet?' }]
function renderProfile(form = profileFormValues(profile)) {
  return renderToStaticMarkup(<ProfileForm profile={profile} form={form} questions={questions}
    saving={false} onChange={() => {}} onSubmit={() => {}} onCancel={() => {}} />)
}

test('profile navigation and content are available only after authentication', () => {
  assert.doesNotMatch(render(), /My profile|Loading profile/)
  assert.match(render({ user: { id: 1, username: 'alice' }, activeTab: 'profile' }), /My profile|Loading profile/)
})

test('profile has immutable username and shows only hint status', () => {
  const html = renderProfile()
  assert.match(html, /readOnly=""/)
  assert.match(html, /An answer is saved securely/)
  assert.doesNotMatch(html, /name="hintAnswer"|name="currentPassword"/)
})

test('sensitive edits request password confirmation and a replacement hint answer', () => {
  const form = profileFormValues(profile)
  assert.match(renderProfile({ ...form, email: 'new@example.com' }), /name="currentPassword"/)
  const html = renderProfile({ ...form, changeHint: true })
  assert.match(html, /name="hintAnswer"/)
  assert.match(html, /name="currentPassword"/)
  assert.match(html, /name="hintQuestion"/)
})

test('profile payload excludes ownership, username, and unchanged hints', () => {
  const form = profileFormValues(profile)
  const payload = profileUpdatePayload({ ...form, username: 'bob', userId: 2, hintAnswer: 'stale answer' })
  assert.deepEqual(Object.keys(payload).sort(), ['avatarUrl', 'email', 'firstName', 'lastName', 'phoneNumber'])
  const sensitive = profileUpdatePayload({ ...form, changeHint: true, hintQuestion: '2', hintAnswer: 'new answer', currentPassword: 'test password' })
  assert.equal(sensitive.hintQuestion, 2)
  assert.equal(sensitive.hintAnswer, 'new answer')
  assert.equal(sensitive.currentPassword, 'test password')
})

test('transaction API calendar dates display and accumulate correctly in analytics', () => {
  assert.equal(formatDisplayDate('09-Sep-2026'), '09-Sep-2026')
  assert.equal(historyDate('09-Sep-2026').getTime(), historyDate('2026-09-09T23:30:00Z').getTime())
  assert.ok(Number.isNaN(historyDate('31-Feb-2026').getTime()))
  const history = investmentHistory([
    { mutualFundId: 1, txnDate: '31-Aug-2026', amount: 100, transactionType: 'BUY' },
    { mutualFundId: 1, txnDate: '09-Sep-2026', amount: 25, transactionType: 'SELL' }
  ], 1, [historyDate('2026-09-09')], '2026-09-01', '2026-09-09')
  assert.deepEqual(history.map(point => point.invested), [75])
})

test('analytics provides formatted text fields and calendar pickers for both range endpoints', () => {
  const html = render({ user: { username: 'alice' }, activeTab: 'analytics' })
  assert.equal((html.match(/type="date"/g) || []).length, 2)
  assert.equal((html.match(/placeholder="DD-Mon-YYYY"/g) || []).length, 2)
  assert.doesNotMatch(html, /type="datetime-local"/)
})

test('date ranges validate calendar dates and compare chronologically across months and years', () => {
  assert.equal(dateRangeError('', ''), '')
  assert.equal(dateRangeError('31-Dec-2025', '01-Jan-2026'), '')
  assert.equal(dateRangeError('29-Feb-2024', ''), '')
  assert.equal(dateRangeError('09-Sep-2026', '09-Sep-2026'), '')
  assert.match(dateRangeError('01-Jan-2026', '31-Dec-2025'), /on or before/)
  for (const invalid of ['31-Feb-2026', '29-Feb-2025', '09-Sept-2026', '2026-09-09', '09-Sep-']) {
    assert.match(dateRangeError(invalid, ''), /DD-Mon-YYYY/)
  }
  assert.equal(isWithinDateRange('2026-01-01T23:30:00Z', '31-Dec-2025', '01-Jan-2026'), true)
  assert.equal(isWithinDateRange('01-Jan-2026', '31-Dec-2025', '01-Jan-2026'), true)
  assert.equal(isWithinDateRange('2025-12-30', '31-Dec-2025', ''), false)
  assert.equal(isWithinDateRange('2026-01-02', '', '01-Jan-2026'), false)
  assert.equal(isWithinDateRange('invalid', '', ''), false)
  assert.equal(formatDisplayDate('2026-09-01T00:00:00'), '01-Sep-2026')
  assert.equal(formatDisplayDate('31-Feb-2026'), '-')
})

import { FundInvestmentOverview } from './components/MutualFundTransactions'

test('unfiltered transactions show fund totals and a selection prompt instead of individual transactions', () => {
  const html = render({ user: { username: 'alice' }, activeTab: 'transactions' })
  assert.match(html, /Select a fund from the drop-down above/)
  assert.match(html, /Total Invested Amount/)
  assert.doesNotMatch(html, /<th>Transaction Date<\/th>|<th>Actions<\/th>/)
  const overview = renderToStaticMarkup(<FundInvestmentOverview funds={[
    { mutualFundId: 1, mutualFundName: 'Growth', totalInvested: 1250 },
    { mutualFundId: 2, mutualFundName: 'Empty fund', totalInvested: 0 }
  ]} loading={false} error="" />)
  assert.match(overview, /Growth/)
  assert.match(overview, /1,250.00/)
  assert.match(overview, /Empty fund/)
  assert.match(overview, /₹0.00/)
})

test('fund overview shows loading, empty and failure states', () => {
  assert.match(renderToStaticMarkup(<FundInvestmentOverview funds={[]} loading={true} />), /Loading fund totals/)
  assert.match(renderToStaticMarkup(<FundInvestmentOverview funds={[]} loading={false} />), /No mutual funds found/)
  assert.match(renderToStaticMarkup(<FundInvestmentOverview funds={[]} loading={false} error="Unavailable" />), /Unable to load fund totals: Unavailable/)
})

test('fund overview grand total sums net investments across all funds', () => {
  const html = renderToStaticMarkup(<FundInvestmentOverview funds={[
    { mutualFundId: 1, mutualFundName: 'Growth', totalInvested: 1250.50 },
    { mutualFundId: 2, mutualFundName: 'Income', totalInvested: '249.75' },
    { mutualFundId: 3, mutualFundName: 'Redeemed', totalInvested: -100 },
    { mutualFundId: 4, mutualFundName: 'Empty', totalInvested: null }
  ]} loading={false} />)
  assert.match(html, /<tfoot>.*Grand Total.*₹1,400.25.*<\/tfoot>/)
  assert.match(renderToStaticMarkup(<FundInvestmentOverview funds={[]} loading={false} />), /<tfoot>.*Grand Total.*₹0.00.*<\/tfoot>/)
  assert.doesNotMatch(renderToStaticMarkup(<FundInvestmentOverview funds={[]} loading={true} />), /Grand Total/)
  assert.doesNotMatch(renderToStaticMarkup(<FundInvestmentOverview funds={[]} loading={false} error="Unavailable" />), /Grand Total/)
})

import { FundValueOverview } from './components/MutualFundValues'

test('unfiltered fund values show the latest-value overview and selection prompt', () => {
  const html = render({ user: { username: 'alice' }, activeTab: 'values' })
  assert.match(html, /Select a fund from the drop-down above to see its individual transactions/)
  assert.match(html, /Latest Value/)
  assert.doesNotMatch(html, /<th>Actions<\/th>/)
})

test('fund value overview shows dated valuations, missing values and their grand total', () => {
  const html = renderToStaticMarkup(<FundValueOverview funds={[
    { mutualFundId: 1, mutualFundName: 'Growth', latestValue: { totalValue: '1250.50', valueAsOfDate: '09-Sep-2026' } },
    { mutualFundId: 2, mutualFundName: 'Income', latestValue: { totalValue: 249.75, valueAsOfDate: '01-Sep-2026' } },
    { mutualFundId: 3, mutualFundName: 'Empty', latestValue: null },
    { mutualFundId: 4, mutualFundName: 'Zero', latestValue: { totalValue: 0, valueAsOfDate: '02-Sep-2026' } }
  ]} loading={false} />)
  assert.match(html, /Growth<\/td><td>₹1,250.50<\/td><td>09-Sep-2026/)
  assert.match(html, /Empty<\/td><td>No value recorded<\/td><td>-/)
  assert.match(html, /Zero<\/td><td>₹0.00/)
  assert.match(html, /<tfoot>.*Grand Total.*₹1,500.25.*<\/tfoot>/)
})

test('fund value overview handles loading, empty and failure without misleading totals', () => {
  const loading = renderToStaticMarkup(<FundValueOverview funds={[]} loading={true} />)
  assert.match(loading, /Loading latest fund values/)
  assert.doesNotMatch(loading, /Grand Total/)
  const error = renderToStaticMarkup(<FundValueOverview funds={[]} loading={false} error="Unavailable" />)
  assert.match(error, /Unable to load latest fund values/)
  assert.doesNotMatch(error, /Grand Total/)
  const empty = renderToStaticMarkup(<FundValueOverview funds={[]} loading={false} />)
  assert.match(empty, /No mutual funds found/)
  assert.match(empty, /<tfoot>.*Grand Total.*₹0.00/)
})

import DateInput, { toPickerDate, fromPickerDate } from './components/DateInput'

test('date pickers round-trip calendar dates without changing the API date format', () => {
  for (const [display, native] of [['09-Sep-2026', '2026-09-09'], ['29-Feb-2024', '2024-02-29'], ['01-Jan-2026', '2026-01-01'], ['31-Dec-2025', '2025-12-31']]) {
    assert.equal(toPickerDate(display), native)
    assert.equal(fromPickerDate(native), display)
  }
  assert.equal(toPickerDate(''), '')
  assert.equal(fromPickerDate(''), '')
  assert.equal(toPickerDate('31-Feb-2026'), '')
})

test('shared date picker preserves required and accessibility properties and emits formatted changes', () => {
  let changed
  const input = DateInput({ value: '09-Sep-2026', onChange: value => { changed = value }, required: true, 'aria-label': 'Transaction Date' })
  const html = renderToStaticMarkup(input)
  assert.match(html, /type="date"/)
  assert.match(html, /value="2026-09-09"/)
  assert.match(html, /required=""/)
  assert.match(html, /aria-label="Transaction Date"/)
  assert.match(html, /type="text".*value="09-Sep-2026"/)
  const textInput = input.props.children[0]
  const picker = input.props.children[1].props.children[1]
  let validity
  textInput.props.onChange({ target: { value: '31-Feb-2026', setCustomValidity: message => { validity = message } } })
  assert.match(validity, /valid date/)
  textInput.props.onChange({ target: { value: '10-Sep-2026', setCustomValidity: message => { validity = message } } })
  assert.equal(validity, '')
  assert.equal(changed, '10-Sep-2026')
  const currentTarget = { parentElement: { previousElementSibling: { setCustomValidity: message => { validity = message } } } }
  picker.props.onChange({ target: { value: '2026-10-01' }, currentTarget })
  assert.equal(changed, '01-Oct-2026')
  picker.props.onChange({ target: { value: '' }, currentTarget })
  assert.equal(changed, '')
})

test('login links to a public password recovery screen', () => {
  assert.match(render({ path: '/login' }), /href="\/reset-password">Reset password/)
  const html = render({ path: '/reset-password' })
  assert.match(html, /Reset password/)
  assert.match(html, /name="username"/)
  assert.match(html, /recovery questions/)
  assert.match(html, /href="\/login">Back to login/)
  assert.doesNotMatch(html, /aria-label="Portfolio"/)
})

test('login links to a public email-based username recovery form', () => {
  const login = render({ path: '/login' })
  assert.match(login, /class="reset-password-link" href="\/forgot-username">Forgot username/)
  const html = render({ path: '/forgot-username' })
  assert.match(html, /type="email"[^>]*name="email"/)
  assert.match(html, /Send username/)
  assert.match(html, /href="\/login">Back to login/)
  assert.doesNotMatch(html, /name="password"|aria-label="Portfolio"/)
})

test('profile settings are available from the username control instead of the portfolio tabs', () => {
  const html = render({ user: { id: 1, username: 'alice' }, activeTab: 'profile' })
  assert.match(html, /<header.*class="header-profile" aria-label="alice: Profile settings" aria-current="page"/)
  assert.match(html, /<strong>alice<\/strong><span class="header-profile-caption">Profile settings/)
  const navigation = html.match(/<nav class="tabs".*?<\/nav>/)[0]
  assert.doesNotMatch(navigation, /My profile|Profile settings/)
  assert.doesNotMatch(render(), /header-profile/)
})

test('clicking the username profile control opens the profile settings', () => {
  const calls = []
  const view = AppView({ user: { id: 1, username: 'alice' }, checking: false, activeTab: 'brokers',
    setActiveTab: tab => calls.push(['tab', tab]), navigate: path => calls.push(['path', path]) })
  function findProfile(element) {
    if (element?.props?.className === 'header-profile') return element
    for (const child of [element?.props?.children].flat(Infinity)) {
      if (child && typeof child === 'object') {
        const found = findProfile(child)
        if (found) return found
      }
    }
  }
  findProfile(view).props.onClick()
  assert.deepEqual(calls, [['tab', 'profile'], ['path', '/']])
})
