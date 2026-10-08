import test from 'node:test'
import assert from 'node:assert/strict'
import { renderToStaticMarkup } from 'react-dom/server'
import { CoinUploadFields, coinUploadOptions, loadAllPages } from './CoinUploadForm'

const form = { brokerAccountId: '7', periodStart: '01-Oct-2026', periodEnd: '31-Oct-2026', dateFormat: 'dd/MM/uuuu', postingPolicy: 'ORDER_ONLY', matches: [{ record: '2', fund: '42' }] }

test('Coin upload sends explicit dates and matching choices without an owner override', () => {
  assert.deepEqual(coinUploadOptions({ ...form, ownerUserId: 99 }), {
    brokerAccountId: 7, periodStart: '2026-10-01', periodEnd: '2026-10-31', dateFormat: 'dd/MM/uuuu', postingPolicy: 'ORDER_ONLY', fundOverrides: { 2: 42 }
  })
  assert.throws(() => coinUploadOptions({ ...form, dateFormat: '' }), /date format/)
  assert.throws(() => coinUploadOptions({ ...form, periodStart: '01-Nov-2026' }), /must not be after/)
  assert.throws(() => coinUploadOptions({ ...form, matches: [...form.matches, ...form.matches] }), /unique positive/)
  assert.throws(() => coinUploadOptions({ ...form, matches: [{ record: '0', fund: '42' }] }), /unique positive/)
})

test('Coin form exposes explicit posting options and existing account and fund choices', () => {
  const html = renderToStaticMarkup(<CoinUploadFields form={form} setForm={() => {}} brokers={[{ id: 7, brokerName: 'Zerodha', accountId: 'Synthetic' }]} funds={[{ mutualFundId: 42, mutualFundName: 'Example fund', folioNumber: '000001' }]} fundsLoading={false} />)
  for (const text of ['Export start date', 'Export end date', 'Dates in the CSV', 'Orders only', 'TRADE_DATE_MIDNIGHT', 'TRADE_DATE_ORDER_TIME', 'CSV record number', 'Example fund', '000001']) assert.ok(html.includes(text), text)
  assert.doesNotMatch(html, /ownerUserId|input-file|System\.exit/)
})

test('Coin account and fund selectors load all pages', async () => {
  const seen = []
  const rows = await loadAllPages(async page => { seen.push(page); return { content: [{ id: page.page + 1 }], totalPages: 2 } })
  assert.deepEqual(rows, [{ id: 1 }, { id: 2 }])
  assert.deepEqual(seen, [{ page: 0, size: 100 }, { page: 1, size: 100 }])
  await assert.rejects(loadAllPages(async page => { if (page.page) throw new Error('Unavailable'); return { content: [], totalPages: 2 } }), /Unavailable/)
})
