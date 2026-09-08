import { useEffect, useState } from 'react'
import api from '../api/api'
import Pagination from './Pagination'
import { formatDisplayDate } from '../utils/date'

const empty = { mutualFundId: '', transactionType: 'BUY', amount: '', units: '', avgPrice: '', txnDate: '' }
const initialPage = { page: 0, size: 20, totalPages: 0, totalElements: 0, first: true, last: true }

export default function MutualFundTransactions() {
  const [items, setItems] = useState([])
  const [funds, setFunds] = useState([])
  const [paging, setPaging] = useState(initialPage)
  const [selectedFundId, setSelectedFundId] = useState('')
  const [form, setForm] = useState(empty)
  const [editingId, setEditingId] = useState(null)
  const [showForm, setShowForm] = useState(false)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [refreshKey, setRefreshKey] = useState(0)
  const [transactionSummary, setTransactionSummary] = useState(null)
  const [summaryLoading, setSummaryLoading] = useState(true)
  const [summaryError, setSummaryError] = useState('')
  const [summaryRefreshKey, setSummaryRefreshKey] = useState(0)

  useEffect(() => {
    let active = true
    const load = async () => {
      try {
        setLoading(true)
        const response = selectedFundId
          ? await api.transactions.byFund(selectedFundId, paging)
          : await api.transactions.all(paging)
        if (!active) return
        setItems(response.content)
        setPaging(response)
        setError('')
      } catch (e) {
        if (active) setError(e.message)
      } finally {
        if (active) setLoading(false)
      }
    }
    load()
    return () => { active = false }
  }, [paging.page, paging.size, selectedFundId, refreshKey])

  useEffect(() => {
    let active = true
    api.mutualFunds.all({ page: 0, size: 100 })
      .then((response) => { if (active) setFunds(response.content) })
      .catch((e) => { if (active) setError(e.message) })
    return () => { active = false }
  }, [])

  useEffect(() => {
    let active = true
    const loadSummary = async () => {
      try {
        setSummaryLoading(true)
        const response = await api.transactions.summary({ mutualFundId: selectedFundId || 0 })
        if (!active) return
        setTransactionSummary(response)
        setSummaryError('')
      } catch (e) {
        if (active) setSummaryError(e.message)
      } finally {
        if (active) setSummaryLoading(false)
      }
    }
    loadSummary()
    return () => { active = false }
  }, [selectedFundId, summaryRefreshKey])

  const reload = () => setRefreshKey((key) => key + 1)
  const reloadSummary = () => setSummaryRefreshKey((key) => key + 1)
  const reset = () => { setForm(empty); setEditingId(null); setShowForm(false) }
  const fundName = (id) => funds.find((fund) => String(fund.mutualFundId) === String(id))?.mutualFundName || `Fund #${id}`

  const submit = async (event) => {
    event.preventDefault()
    try {
      const data = {
        mutualFundId: Number(form.mutualFundId), transactionType: form.transactionType,
        amount: Number(form.amount), units: Number(form.units), avgPrice: Number(form.avgPrice),
        txnDate: new Date(form.txnDate).toISOString()
      }
      if (editingId) await api.transactions.update(editingId, { ...data, mutualFundTxnId: editingId })
      else await api.transactions.create(data)
      reset()
      reload()
      reloadSummary()
    } catch (e) { setError(e.message) }
  }

  const remove = async (id) => {
    if (!window.confirm('Delete this transaction?')) return
    try {
      await api.transactions.remove(id)
      reloadSummary()
      if (items.length === 1 && paging.page > 0) setPaging(c => ({ ...c, page: c.page - 1 }))
      else reload()
    } catch (e) { setError(e.message) }
  }

  const selectFund = (value) => {
    setSelectedFundId(value)
    setPaging((current) => ({ ...current, page: 0 }))
  }

  return <section>
    <div className="section-header"><div><h2>Mutual Fund Transactions</h2><p>Track purchases, redemptions and other transactions.</p></div><button className="primary" onClick={() => { reset(); setShowForm(true) }}>+ Add Transaction</button></div>
    {error && <div className="error">{error}</div>}
    {showForm && <form className="form-card" onSubmit={submit}><div className="form-grid">
      <label>Mutual Fund<select required value={form.mutualFundId} onChange={e => setForm({ ...form, mutualFundId: e.target.value })}><option value="">Select fund</option>{funds.map(f => <option key={f.mutualFundId} value={f.mutualFundId}>{f.mutualFundName}</option>)}</select></label>
      <label>Transaction Type<select value={form.transactionType} onChange={e => setForm({ ...form, transactionType: e.target.value })}><option value="BUY">BUY</option><option value="SELL">SELL</option></select></label>
      <label>Amount<input required type="number" min="0" step="0.01" value={form.amount} onChange={e => setForm({ ...form, amount: e.target.value })} /></label>
      <label>Units<input required type="number" min="0" step="0.001" value={form.units} onChange={e => setForm({ ...form, units: e.target.value })} /></label>
      <label>Average Price<input required type="number" min="0" step="0.001" value={form.avgPrice} onChange={e => setForm({ ...form, avgPrice: e.target.value })} /></label>
      <label>Transaction Date<input required type="datetime-local" value={form.txnDate} onChange={e => setForm({ ...form, txnDate: e.target.value })} /></label>
    </div><div className="form-actions"><button type="button" className="secondary" onClick={reset}>Cancel</button><button className="primary" type="submit">{editingId ? 'Update' : 'Create'}</button></div></form>}
    <div className="filter-card"><label>Filter by Mutual Fund<select value={selectedFundId} onChange={e => selectFund(e.target.value)}><option value="">All Mutual Funds</option>{funds.map(f => <option key={f.mutualFundId} value={f.mutualFundId}>{f.mutualFundName}</option>)}</select></label>{selectedFundId && <button className="secondary clear-filter" type="button" onClick={() => selectFund('')}>Clear Filter</button>}</div>
    <section className="transaction-summary" aria-label="Transaction summary">
      {summaryLoading ? <p className="summary-label">Loading transaction summary...</p> : summaryError ? <p className="error" role="alert">Unable to load transaction summary: {summaryError}</p> : <>
        <div className="summary-item"><span className="summary-label">Total Transaction Value</span><strong>{formatCurrency(transactionSummary?.totalValue)}</strong></div>
        <div className="summary-item"><span className="summary-label">Total Transaction Units</span><strong>{formatUnits(transactionSummary?.totalUnits)}</strong></div>
      </>}
    </section>
    <div className="table-wrap"><table><thead><tr><th>Mutual Fund</th><th>Type</th><th>Amount</th><th>Units</th><th>Avg Price</th><th>Transaction Date</th><th>Actions</th></tr></thead><tbody>
      {loading ? <tr><td colSpan="7" className="empty">Loading...</td></tr> : items.length === 0 ? <tr><td colSpan="7" className="empty">No transactions found.</td></tr> : items.map(t => <tr key={t.mutualFundTxnId}><td>{fundName(t.mutualFundId)}</td><td>{t.transactionType}</td><td>{formatNumber(t.amount)}</td><td>{formatNumber(t.units)}</td><td>{formatNumber(t.avgPrice)}</td><td>{formatDate(t.txnDate)}</td><td><div className="actions"><button onClick={() => { setForm({ mutualFundId: String(t.mutualFundId), transactionType: t.transactionType || 'BUY', amount: String(t.amount ?? ''), units: String(t.units ?? ''), avgPrice: String(t.avgPrice ?? ''), txnDate: toInputDate(t.txnDate) }); setEditingId(t.mutualFundTxnId); setShowForm(true) }}>Edit</button><button className="danger-text" onClick={() => remove(t.mutualFundTxnId)}>Delete</button></div></td></tr>)}
    </tbody></table></div>
    {!loading && <Pagination {...paging} loading={loading} onPrevious={() => setPaging(c => ({ ...c, page: c.page - 1 }))} onNext={() => setPaging(c => ({ ...c, page: c.page + 1 }))} onSizeChange={(size) => setPaging(c => ({ ...c, page: 0, size }))} />}
  </section>
}

function formatNumber(value) { return Number(value || 0).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 }) }
function formatCurrency(value) { return new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(Number(value ?? 0)) }
function formatUnits(value) { return new Intl.NumberFormat('en-IN', { minimumFractionDigits: 0, maximumFractionDigits: 4 }).format(Number(value ?? 0)) }
function formatDate(value) { return formatDisplayDate(value) }
function toInputDate(value) { return value ? new Date(value).toISOString().slice(0, 16) : '' }
