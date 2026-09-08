import { useEffect, useState } from 'react'
import api from '../api/api'
import Pagination from './Pagination'
import { formatDisplayDate } from '../utils/date'

const emptyForm = { mutualFundId: '', totalValue: '', valueAsOfDate: '' }
const initialPage = { page: 0, size: 20, totalPages: 0, totalElements: 0, first: true, last: true }

export default function MutualFundValues() {
  const [items, setItems] = useState([])
  const [funds, setFunds] = useState([])
  const [paging, setPaging] = useState(initialPage)
  const [selectedFundId, setSelectedFundId] = useState('')
  const [form, setForm] = useState(emptyForm)
  const [editingId, setEditingId] = useState(null)
  const [showForm, setShowForm] = useState(false)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [refreshKey, setRefreshKey] = useState(0)

  useEffect(() => {
    let active = true
    const load = async () => {
      try {
        setLoading(true)
        const response = selectedFundId ? await api.values.byFund(selectedFundId, paging) : await api.values.all(paging)
        if (!active) return
        setItems(response.content)
        setPaging(response)
        setError('')
      } catch (e) {
        if (active) setError(e.message || 'Failed to load fund values.')
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
      .catch((e) => { if (active) setError(e.message || 'Failed to load mutual funds.') })
    return () => { active = false }
  }, [])

  const reload = () => setRefreshKey((key) => key + 1)
  const resetForm = () => { setForm(emptyForm); setEditingId(null); setShowForm(false) }
  const getFundName = (id) => funds.find((fund) => String(fund.mutualFundId) === String(id))?.mutualFundName || `Fund #${id}`

  const handleSubmit = async (event) => {
    event.preventDefault()
    try {
      const data = { mutualFundId: Number(form.mutualFundId), totalValue: Number(form.totalValue), valueAsOfDate: new Date(form.valueAsOfDate).toISOString() }
      if (editingId !== null) await api.values.update(editingId, { ...data, valId: editingId })
      else await api.values.create(data)
      resetForm()
      reload()
    } catch (e) { setError(e.message || 'Failed to save fund value.') }
  }

  const handleDelete = async (id) => {
    if (!window.confirm('Delete this fund value?')) return
    try {
      await api.values.remove(id)
      if (items.length === 1 && paging.page > 0) setPaging(c => ({ ...c, page: c.page - 1 }))
      else reload()
    } catch (e) { setError(e.message || 'Failed to delete fund value.') }
  }

  const selectFund = (value) => {
    setSelectedFundId(value)
    setPaging((current) => ({ ...current, page: 0 }))
  }

  return <section>
    <div className="section-header"><div><h2>Mutual Fund Values</h2><p>Maintain historical fund valuation records.</p></div><button type="button" className="primary" onClick={() => { resetForm(); setShowForm(true) }}>+ Add Fund Value</button></div>
    {error && <div className="error">{error}</div>}
    {showForm && <form className="form-card" onSubmit={handleSubmit}><div className="form-grid">
      <label>Mutual Fund<select required value={form.mutualFundId} onChange={e => setForm({ ...form, mutualFundId: e.target.value })}><option value="">Select fund</option>{funds.map(fund => <option key={fund.mutualFundId} value={fund.mutualFundId}>{fund.mutualFundName}</option>)}</select></label>
      <label>Total Value<input required type="number" min="0" step="0.01" value={form.totalValue} onChange={e => setForm({ ...form, totalValue: e.target.value })} /></label>
      <label>Value As Of<input required type="datetime-local" value={form.valueAsOfDate} onChange={e => setForm({ ...form, valueAsOfDate: e.target.value })} /></label>
    </div><div className="form-actions"><button type="button" className="secondary" onClick={resetForm}>Cancel</button><button type="submit" className="primary">{editingId !== null ? 'Update' : 'Create'}</button></div></form>}
    <div className="filter-card"><label>Filter by Mutual Fund<select value={selectedFundId} onChange={e => selectFund(e.target.value)}><option value="">All Mutual Funds</option>{funds.map(fund => <option key={fund.mutualFundId} value={fund.mutualFundId}>{fund.mutualFundName}</option>)}</select></label>{selectedFundId && <button type="button" className="secondary clear-filter" onClick={() => selectFund('')}>Clear Filter</button>}</div>
    <div className="table-wrap"><table><thead><tr><th>Mutual Fund</th><th>Total Value</th><th>Value As Of</th><th>Actions</th></tr></thead><tbody>
      {loading ? <tr><td colSpan="4" className="empty">Loading...</td></tr> : items.length === 0 ? <tr><td colSpan="4" className="empty">No fund values found.</td></tr> : items.map((item) => <tr key={item.valId}><td>{getFundName(item.mutualFundId)}</td><td>{Number(item.totalValue).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</td><td>{formatDate(item.valueAsOfDate)}</td><td><div className="actions"><button type="button" onClick={() => { setForm({ mutualFundId: String(item.mutualFundId), totalValue: String(item.totalValue), valueAsOfDate: toInputDate(item.valueAsOfDate) }); setEditingId(item.valId); setShowForm(true) }}>Edit</button><button type="button" className="danger-text" onClick={() => handleDelete(item.valId)}>Delete</button></div></td></tr>)}
    </tbody></table></div>
    {!loading && <Pagination {...paging} loading={loading} onPrevious={() => setPaging(c => ({ ...c, page: c.page - 1 }))} onNext={() => setPaging(c => ({ ...c, page: c.page + 1 }))} onSizeChange={(size) => setPaging(c => ({ ...c, page: 0, size }))} />}
  </section>
}

function formatDate(value) { return formatDisplayDate(value) }
function toInputDate(value) { return value ? new Date(value).toISOString().slice(0, 16) : '' }
