import test from 'node:test'
import assert from 'node:assert/strict'
import { renderToStaticMarkup } from 'react-dom/server'
import { AppView } from './App'

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
