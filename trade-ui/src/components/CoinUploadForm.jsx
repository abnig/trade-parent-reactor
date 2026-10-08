import { useEffect, useRef, useState } from 'react'
import api from '../api/api'
import DateInput, { toPickerDate } from './DateInput'

const empty = { brokerAccountId: '', periodStart: '', periodEnd: '', dateFormat: '', postingPolicy: 'ORDER_ONLY', matches: [] }

export async function loadAllPages(load) {
  const result = []
  let page = 0
  let response
  do {
    response = await load({ page, size: 100 })
    result.push(...response.content)
    page += 1
  } while (page < response.totalPages)
  return result
}

export function coinUploadOptions(form) {
  const periodStart = toPickerDate(form.periodStart)
  const periodEnd = toPickerDate(form.periodEnd)
  if (!form.brokerAccountId || !periodStart || !periodEnd || !form.dateFormat || !form.postingPolicy) throw new Error('Select an account, export period, date format and posting policy.')
  if (periodStart > periodEnd) throw new Error('Export start date must not be after its end date.')
  const fundOverrides = {}
  for (const match of form.matches) {
    const record = Number(match.record)
    if (!Number.isSafeInteger(record) || record < 1 || !match.fund || fundOverrides[record]) throw new Error('Each fund match needs a unique positive CSV record number and an existing fund.')
    fundOverrides[record] = Number(match.fund)
  }
  return { brokerAccountId: Number(form.brokerAccountId), periodStart, periodEnd, dateFormat: form.dateFormat, postingPolicy: form.postingPolicy, fundOverrides }
}

export default function CoinUploadForm({ file, uploading, setUploading, onCancel, onImported, onSettled }) {
  const [form, setForm] = useState(empty)
  const [brokers, setBrokers] = useState([])
  const [funds, setFunds] = useState([])
  const [loading, setLoading] = useState(true)
  const [fundsLoading, setFundsLoading] = useState(false)
  const [error, setError] = useState('')
  const [fields, setFields] = useState({})
  const active = useRef(true)
  useEffect(() => {
    active.current = true
    return () => { active.current = false }
  }, [])
  useEffect(() => {
    let current = true
    loadAllPages(api.brokerAccounts.all).then(items => { if (current) setBrokers(items) })
      .catch(e => { if (current) setError(e.message) }).finally(() => { if (current) setLoading(false) })
    return () => { current = false }
  }, [])
  useEffect(() => {
    let current = true
    setFunds([])
    if (!form.brokerAccountId) { setFundsLoading(false); return }
    setFundsLoading(true)
    loadAllPages(page => api.mutualFunds.byBrokerAccount(form.brokerAccountId, page))
      .then(items => { if (current) setFunds(items) })
      .catch(e => { if (current) setError(e.message) }).finally(() => { if (current) setFundsLoading(false) })
    return () => { current = false }
  }, [form.brokerAccountId])
  const submit = async event => {
    event.preventDefault()
    if (uploading) return
    try {
      const options = coinUploadOptions(form)
      setUploading(true); setError(''); setFields({})
      const result = await api.transactions.upload(file, options)
      if (result.status !== 'COMPLETED' || !result.importResult) throw new Error('The server did not confirm a completed Coin import.')
      if (active.current) onImported(result)
    } catch (e) {
      if (active.current) { setError(e.message); setFields(e.fieldErrors || {}) }
    } finally {
      if (active.current) { setUploading(false); onSettled() }
    }
  }
  return <form className="form-card" onSubmit={submit} aria-label="Import Coin CSV">
    <h3>Import {file.name}</h3>
    <p>Use the export’s full date range. Existing orders moving from PROCESSING to COMPLETE are updated without adding duplicate orders.</p>
    {error && <div className="error" role="alert">{error}</div>}
    {Object.keys(fields).length > 0 && <ul className="error" aria-label="CSV validation errors">{Object.entries(fields).map(([field, code]) => <li key={field}>{field}: {code}</li>)}</ul>}
    <fieldset disabled={uploading || loading}>
      <CoinUploadFields form={form} setForm={setForm} brokers={brokers} funds={funds} fundsLoading={fundsLoading} />
    </fieldset>
    {loading && <p role="status">Loading broker accounts…</p>}
    {uploading && <p role="status">Validating and importing records. This may take a while.</p>}
    <div className="form-actions"><button type="button" className="secondary" disabled={uploading} onClick={onCancel}>Cancel</button><button type="submit" className="primary" disabled={uploading || loading || !brokers.length}>{uploading ? 'Importing…' : 'Import Coin CSV'}</button></div>
  </form>
}

export function CoinUploadFields({ form, setForm, brokers, funds, fundsLoading }) {
  const changeMatch = (index, patch) => setForm({ ...form, matches: form.matches.map((match, i) => i === index ? { ...match, ...patch } : match) })
  return <>
    <div className="form-grid">
      <label>Broker account<select required value={form.brokerAccountId} onChange={e => setForm({ ...form, brokerAccountId: e.target.value, matches: [] })}><option value="">Select account</option>{brokers.map(b => <option key={b.id} value={b.id}>{b.brokerName} — {b.accountId}</option>)}</select></label>
      <label>Export start date<DateInput required value={form.periodStart} onChange={value => setForm({ ...form, periodStart: value })} /></label>
      <label>Export end date<DateInput required value={form.periodEnd} onChange={value => setForm({ ...form, periodEnd: value })} /></label>
      <label>Dates in the CSV<select required value={form.dateFormat} onChange={e => setForm({ ...form, dateFormat: e.target.value })}><option value="">Choose source date format</option><option value="dd/MM/uuuu">Day/month/year</option><option value="MM/dd/uuuu">Month/day/year</option><option value="uuuu-MM-dd">Year-month-day</option></select></label>
      <label>Import completed orders as<select required value={form.postingPolicy} onChange={e => setForm({ ...form, postingPolicy: e.target.value })}><option value="ORDER_ONLY">Orders only — keep portfolio totals unchanged</option><option value="TRADE_DATE_MIDNIGHT">Transactions dated on the trade date at midnight</option><option value="TRADE_DATE_ORDER_TIME">Transactions dated using trade date and order time</option></select></label>
    </div>
    <p>Pending orders remain orders. To include completed orders in portfolio totals, select a transaction option and confirm its date convention matches your records.</p>
    <details><summary>Match records with missing fund details</summary>
      <p>Use this when a CSV record has no folio or cannot uniquely identify a fund. Data records start at 1 after the header. Supplied details must still match the selected fund.</p>
      {form.matches.map((match, index) => <div className="form-grid" key={index}>
        <label>CSV record number<input type="number" min="1" step="1" required value={match.record} onChange={e => changeMatch(index, { record: e.target.value })} /></label>
        <label>Existing mutual fund<select required value={match.fund} onChange={e => changeMatch(index, { fund: e.target.value })}><option value="">Select fund</option>{funds.map(f => <option key={f.mutualFundId} value={f.mutualFundId}>{f.mutualFundName} — {f.folioNumber || 'No folio'}</option>)}</select></label>
        <button type="button" className="secondary" onClick={() => setForm({ ...form, matches: form.matches.filter((_, i) => i !== index) })}>Remove match</button>
      </div>)}
      <button type="button" className="secondary" disabled={!form.brokerAccountId || fundsLoading || !funds.length} onClick={() => setForm({ ...form, matches: [...form.matches, { record: '', fund: '' }] })}>Add fund match</button>
    </details>
  </>
}
