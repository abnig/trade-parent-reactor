import test from 'node:test'
import assert from 'node:assert/strict'
import api from './api.js'

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
