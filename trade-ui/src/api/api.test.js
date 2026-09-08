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
