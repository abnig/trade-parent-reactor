import test from 'node:test'
import assert from 'node:assert/strict'
import { renderToStaticMarkup } from 'react-dom/server'
import { MutualFundForm, fundPayload } from './MutualFunds'

const form = { brokerAccountId: '1', mutualFundName: ' Index Fund ', isin: 'INF123456789', plan: 'Direct Growth', folioNumber: '00001234/05' }

test('fund create and edit forms expose optional metadata and preserve folio formatting', () => {
  for (const editingId of [null, 10]) {
    const html = renderToStaticMarkup(<MutualFundForm form={form} brokers={[]} editingId={editingId} setForm={() => {}} />)
    assert.match(html, /ISIN<input value="INF123456789"/)
    assert.match(html, /Plan<input value="Direct Growth"/)
    assert.match(html, /Folio Number<input value="00001234\/05"/)
    assert.match(html, new RegExp(`>${editingId ? 'Update' : 'Create'}</button>`))
    assert.doesNotMatch(html, /(?:ISIN|Plan|Folio Number)<input[^>]*required/)
  }
})

test('fund submissions include metadata and explicit clearing without converting folios to numbers', () => {
  assert.deepEqual(fundPayload(form), { ...form, brokerAccountId: 1, mutualFundName: 'Index Fund' })
  const blank = fundPayload({ ...form, isin: '', plan: '', folioNumber: '' })
  assert.equal(blank.isin, '')
  assert.equal(blank.plan, '')
  assert.equal(blank.folioNumber, '')
})

test('fund form accepts N/A source metadata and ISIN text without length limits', () => {
  for (const input of [
    { ...form, isin: 'N/A', plan: 'N/A', folioNumber: 'N/A' },
    { ...form, isin: 'longer-than-twelve-characters' }
  ]) {
    const html = renderToStaticMarkup(<MutualFundForm form={input} brokers={[]} setForm={() => {}} />)
    assert.ok(html.includes(`ISIN<input value="${input.isin}"`))
    assert.doesNotMatch(html, /minLength|maxLength|pattern=/)
    assert.equal(fundPayload(input).isin, input.isin)
    assert.equal(fundPayload(input).plan, input.plan)
    assert.equal(fundPayload(input).folioNumber, input.folioNumber)
  }
})
