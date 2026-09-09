import test from 'node:test'
import assert from 'node:assert/strict'
import api from './api.js'

test('analytics loads every transaction page for the selected fund', async (t) => {
  const urls = []
  t.mock.method(globalThis, 'fetch', async (url) => {
    urls.push(url)
    const page = Number(new URL(url, 'http://localhost').searchParams.get('page'))
    return new Response(JSON.stringify({ content: [{ mutualFundTxnId: page + 1 }], totalPages: 3 }))
  })
  assert.deepEqual(await api.analytics.transactionHistory(7), [
    { mutualFundTxnId: 1 }, { mutualFundTxnId: 2 }, { mutualFundTxnId: 3 }
  ])
  assert.deepEqual(urls, [0, 1, 2].map(page => `/api/mutual-fund-txns/mutual-fund/7?page=${page}&size=100`))
})

test('analytics rejects partial transaction history when a later page fails', async (t) => {
  t.mock.method(globalThis, 'fetch', async (url) => url.includes('page=0')
    ? new Response(JSON.stringify({ content: [{ amount: 100 }], totalPages: 2 }))
    : new Response(JSON.stringify({ message: 'History unavailable' }), { status: 500 }))
  await assert.rejects(api.analytics.transactionHistory(7), /History unavailable/)
})

test('registration posts the supplied fields to the API', async (t) => {
  const data = { username: 'alice', email: 'alice@example.com', password: 'a-long-password' }
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    if (url === '/api/auth/csrf') return new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'test-csrf' }))
    assert.equal(options.headers['X-CSRF-TOKEN'], 'test-csrf')
    assert.equal(options.credentials, 'same-origin')
    assert.equal(url, '/api/auth/register')
    assert.equal(options.method, 'POST')
    assert.equal(options.headers['Content-Type'], 'application/json')
    assert.deepEqual(JSON.parse(options.body), data)
    return new Response(JSON.stringify({ id: 1, roles: ['ROLE_USER'] }), { status: 201 })
  })
  assert.deepEqual(await api.auth.register(data), { id: 1, roles: ['ROLE_USER'] })
})

test('registration surfaces duplicate errors for the form', async (t) => {
  t.mock.method(globalThis, 'fetch', async (url) => url === '/api/auth/csrf'
    ? new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'test-csrf' }))
    : new Response(JSON.stringify({ message: 'Username or email is already registered.' }), { status: 409 }))
  await assert.rejects(api.auth.register({}), /Username or email is already registered/)
})


test('login submits form credentials with CSRF and does not expect a token response', async (t) => {
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    if (url === '/api/auth/csrf') return new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'fresh-token' }))
    assert.equal(url, '/api/auth/login')
    assert.equal(options.headers['Content-Type'], 'application/x-www-form-urlencoded')
    assert.equal(options.headers['X-CSRF-TOKEN'], 'fresh-token')
    assert.equal(new URLSearchParams(options.body).get('password'), 'a&b+c password')
    return new Response(null, { status: 204 })
  })
  assert.equal(await api.auth.login({ username: 'alice', password: 'a&b+c password' }), null)
})

test('failed CSRF retrieval prevents a write', async (t) => {
  const fetch = t.mock.method(globalThis, 'fetch', async () => new Response(null, { status: 503 }))
  await assert.rejects(api.auth.logout(), /Unable to verify your session/)
  assert.equal(fetch.mock.callCount(), 1)
})

test('expired data requests notify the UI and preserve the HTTP status', async (t) => {
  let event
  const originalWindow = globalThis.window
  globalThis.window = { dispatchEvent: value => { event = value } }
  t.after(() => { if (originalWindow === undefined) delete globalThis.window; else globalThis.window = originalWindow })
  t.mock.method(globalThis, 'fetch', async () => new Response(JSON.stringify({ message: 'Login required.' }), { status: 401 }))
  await assert.rejects(api.brokerAccounts.all(), error => error.status === 401)
  assert.equal(event.type, 'session-expired')
})


test('analytics loads complete arrays from dedicated endpoints without paging parameters', async (t) => {
  const records = Array.from({ length: 150 }, (_, id) => ({ valId: id, mutualFundId: 1 }))
  const urls = []
  t.mock.method(globalThis, 'fetch', async (url) => {
    urls.push(url)
    return new Response(JSON.stringify(records))
  })
  assert.deepEqual(await api.analytics.funds(), records)
  assert.deepEqual(await api.analytics.valueHistory(1), records)
  assert.deepEqual(urls, ['/api/analytics/funds', '/api/analytics/funds/1/values'])
})

test('profile updates use the current-user endpoint and CSRF session handling', async (t) => {
  const data = { email: 'alice@example.com', firstName: 'Alice' }
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    if (url === '/api/auth/csrf') return new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'test-csrf' }))
    assert.equal(url, '/api/auth/profile')
    assert.equal(options.method, 'PUT')
    assert.equal(options.credentials, 'same-origin')
    assert.equal(options.headers['X-CSRF-TOKEN'], 'test-csrf')
    assert.deepEqual(JSON.parse(options.body), data)
    return new Response(JSON.stringify({ ...data, userId: 1 }))
  })
  assert.equal((await api.profile.update(data)).userId, 1)
})

test('profile session expiry clears authenticated UI despite the auth URL prefix', async (t) => {
  let event
  const originalWindow = globalThis.window
  globalThis.window = { dispatchEvent: value => { event = value } }
  t.after(() => { if (originalWindow === undefined) delete globalThis.window; else globalThis.window = originalWindow })
  t.mock.method(globalThis, 'fetch', async () => new Response(JSON.stringify({ message: 'Please log in again.' }), { status: 401 }))
  await assert.rejects(api.profile.get(), error => error.status === 401)
  assert.equal(event.type, 'session-expired')
})

test('profile duplicate email errors reach the form', async (t) => {
  t.mock.method(globalThis, 'fetch', async (url) => url === '/api/auth/csrf'
    ? new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'test-csrf' }))
    : new Response(JSON.stringify({ message: 'Email address is already registered.' }), { status: 409 }))
  await assert.rejects(api.profile.update({ email: 'bob@example.com' }), error => error.status === 409 && /already registered/.test(error.message))
})

test('fund value create and update preserve date-only API values', async (t) => {
  const calls = []
  const value = { mutualFundId: 1, totalValue: 500, valueAsOfDate: '09-Sep-2026' }
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    if (url === '/api/auth/csrf') return new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'test-csrf' }))
    calls.push([url, options.method, JSON.parse(options.body)])
    return new Response(JSON.stringify({ ...value, valId: 7 }))
  })
  assert.equal((await api.values.create(value)).valueAsOfDate, '09-Sep-2026')
  assert.equal((await api.values.update(7, value)).valueAsOfDate, '09-Sep-2026')
  assert.deepEqual(calls, [
    ['/api/mutual-fund-values', 'POST', value],
    ['/api/mutual-fund-values/7', 'PUT', value]
  ])
})

test('transaction create and update preserve date-only API values', async (t) => {
  const calls = []
  const txn = { mutualFundId: 1, amount: 100, units: 2, avgPrice: 50, transactionType: 'BUY', txnDate: '09-Sep-2026' }
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    if (url === '/api/auth/csrf') return new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'test-csrf' }))
    calls.push([url, options.method, JSON.parse(options.body)])
    return new Response(JSON.stringify({ ...txn, mutualFundTxnId: 7 }))
  })
  assert.equal((await api.transactions.create(txn)).txnDate, '09-Sep-2026')
  assert.equal((await api.transactions.update(7, txn)).txnDate, '09-Sep-2026')
  assert.deepEqual(calls, [
    ['/api/mutual-fund-txns', 'POST', txn],
    ['/api/mutual-fund-txns/7', 'PUT', txn]
  ])
})

test('fund investment overview loads server totals without transaction pagination', async (t) => {
  const totals = [{ mutualFundId: 1, mutualFundName: 'Fund', totalInvested: 106 }]
  t.mock.method(globalThis, 'fetch', async (url) => {
    assert.equal(url, '/api/mutual-fund-txns/summary/by-fund')
    return new Response(JSON.stringify(totals))
  })
  assert.deepEqual(await api.transactions.fundInvestments(), totals)
})

test('latest fund values fetch the newest record for every fund including unvalued funds', async (t) => {
  const funds = Array.from({ length: 101 }, (_, index) => ({ mutualFundId: index + 1, mutualFundName: `Fund ${index + 1}` }))
  const latest = { valId: 9, totalValue: 1250, valueAsOfDate: '09-Sep-2026' }
  const calls = []
  t.mock.method(globalThis, 'fetch', async (url) => {
    calls.push(url)
    if (url === '/api/analytics/funds') return new Response(JSON.stringify(funds))
    assert.match(url, /^\/api\/mutual-fund-values\/mutual-fund\/\d+\?page=0&size=1$/)
    return new Response(JSON.stringify({ content: url.includes('/1?') ? [latest] : [], totalPages: 20 }))
  })
  const overview = await api.values.latestByFund()
  assert.equal(overview.length, 101)
  assert.deepEqual(overview[0], { ...funds[0], latestValue: latest })
  assert.deepEqual(overview[100], { ...funds[100], latestValue: null })
  assert.equal(calls.length, 102)
})

test('latest fund values reject incomplete totals when a fund request fails', async (t) => {
  t.mock.method(globalThis, 'fetch', async (url) => {
    if (url === '/api/analytics/funds') return new Response(JSON.stringify([{ mutualFundId: 1 }, { mutualFundId: 2 }]))
    if (url.includes('/2?')) return new Response(JSON.stringify({ message: 'Unavailable' }), { status: 500 })
    return new Response(JSON.stringify({ content: [{ totalValue: 100 }] }))
  })
  await assert.rejects(api.values.latestByFund(), /Unavailable/)
})

test('password recovery submits each step through CSRF-protected endpoints', async (t) => {
  const calls = []
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    if (url === '/api/auth/csrf') return new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'test-csrf' }))
    assert.equal(options.headers['X-CSRF-TOKEN'], 'test-csrf')
    calls.push([url, options.method, JSON.parse(options.body)])
    return new Response(null, { status: 204 })
  })
  const start = { username: 'alice' }
  const verify = { challengeId: 'test-challenge', answers: [{ questionId: 1, answer: 'test-answer' }, { questionId: 2, answer: 'test-answer' }] }
  const complete = { token: 'test-token', newPassword: 'test-new-password' }
  await api.auth.startPasswordReset(start)
  await api.auth.verifyPasswordReset(verify)
  await api.auth.completePasswordReset(complete)
  assert.deepEqual(calls, [
    ['/api/auth/password-reset/challenges', 'POST', start],
    ['/api/auth/password-reset/verify', 'POST', verify],
    ['/api/auth/password-reset/complete', 'POST', complete]
  ])
})

test('forgot username sends an email address with CSRF protection and returns generic confirmation', async (t) => {
  const message = 'If an eligible account matches that email address, its username will be sent to it.'
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    if (url === '/api/auth/csrf') return new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'test-csrf' }))
    assert.equal(url, '/api/auth/forgot-username')
    assert.equal(options.method, 'POST')
    assert.equal(options.headers['X-CSRF-TOKEN'], 'test-csrf')
    assert.deepEqual(JSON.parse(options.body), { email: 'alice@example.com' })
    return new Response(JSON.stringify({ message }), { status: 202 })
  })
  assert.deepEqual(await api.auth.forgotUsername({ email: 'alice@example.com' }), { message })
})
