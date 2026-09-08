import test from 'node:test'
import assert from 'node:assert/strict'
import { renderToStaticMarkup } from 'react-dom/server'
import { AppView } from './App'
import { formatDisplayDate } from './utils/date'

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
