import test from 'node:test'
import assert from 'node:assert/strict'
import { renderToStaticMarkup } from 'react-dom/server'
import { TransactionMetadataFields, TransactionMetadataDetails } from './MutualFundTransactions'

test('status dropdown offers Processed and Completed and preserves existing statuses on edit', () => {
  for (const status of ['', 'Processed', 'Completed', 'PROCESSING', 'COMPLETE']) {
    const form = { status, exchangeOrderId: '', settlementId: '', remarks: '', tag: '' }
    const html = renderToStaticMarkup(<TransactionMetadataFields form={form} setForm={() => {}} />)
    assert.match(html, /Status<select>/)
    assert.match(html, /<option value="Processed"[^>]*>Processed<\/option>/)
    assert.match(html, /<option value="Completed"[^>]*>Completed<\/option>/)
    assert.match(html, new RegExp(`<option value="${status}"[^>]*selected=""`))
    if (status === 'PROCESSING' || status === 'COMPLETE') {
      assert.match(html, new RegExp(`<option value="${status}" disabled="" selected="">Current: ${status}</option>`))
    }
    let updated
    const fields = TransactionMetadataFields({ form, setForm: value => { updated = value } })
    const select = fields.props.children[0].props.children[1]
    select.props.onChange({ target: { value: 'Completed' } })
    assert.deepEqual(updated, { ...form, status: 'Completed' })
  }
})

test('transaction metadata form accepts optional text and preserves source identifiers and tags', () => {
  const form = { status: 'PROCESSING', exchangeOrderId: '000123', settlementId: '000007', remarks: 'Processing information', tag: '  {"tag": ["coinandroid"]}  ' }
  const html = renderToStaticMarkup(<TransactionMetadataFields form={form} setForm={() => {}} />)
  for (const label of ['Status', 'Exchange Order ID', 'Settlement ID', 'Remarks', 'Tag']) assert.ok(html.includes(label))
  assert.match(html, /value="000123"/)
  assert.match(html, /value="000007"/)
  assert.doesNotMatch(html, /required|type="number"/)
  assert.match(html, /  \{&quot;tag&quot;: \[&quot;coinandroid&quot;\]\}  /)
})

test('transaction details render metadata safely as text including unknown statuses and raw tags', () => {
  const html = renderToStaticMarkup(<TransactionMetadataDetails transaction={{ status: 'SOURCE_STATUS', tag: '<script>alert(1)</script>', settlementId: '000007' }} />)
  assert.match(html, /<summary>SOURCE_STATUS<\/summary>/)
  assert.match(html, /&lt;script&gt;alert\(1\)&lt;\/script&gt;/)
  assert.doesNotMatch(html, /<script>/)
  assert.match(html, /000007/)
  assert.equal(renderToStaticMarkup(<TransactionMetadataDetails transaction={{}} />), '—')
})
