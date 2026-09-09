import DateInput from './DateInput'
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
        const response = selectedFundId ? await api.values.byFund(selectedFundId, paging) : await api.values.latestByFund()
        if (!active) return
        if (selectedFundId) {
          setItems(response.content)
          setPaging(response)
        } else {
          setFunds(response)
        }
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

  const reload = () => setRefreshKey((key) => key + 1)
  const resetForm = () => { setForm(emptyForm); setEditingId(null); setShowForm(false) }
  const getFundName = (id) => funds.find((fund) => String(fund.mutualFundId) === String(id))?.mutualFundName || `Fund #${id}`

  const handleSubmit = async (event) => {
    event.preventDefault()
    try {
      const data = { mutualFundId: Number(form.mutualFundId), totalValue: Number(form.totalValue), valueAsOfDate: form.valueAsOfDate }
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
    setItems([])
    setLoading(true)
    setError('')
    setSelectedFundId(value)
    setPaging((current) => ({ ...current, page: 0 }))
  }

  return <section>
    <div className="section-header"><div><h2>Mutual Fund Values</h2><p>Maintain historical fund valuation records.</p></div><button type="button" className="primary" onClick={() => { resetForm(); setShowForm(true) }}>+ Add Fund Value</button></div>
    {error && <div className="error">{error}</div>}
    {showForm && <form className="form-card" onSubmit={handleSubmit}><div className="form-grid">
      <label>Mutual Fund<select required value={form.mutualFundId} onChange={e => setForm({ ...form, mutualFundId: e.target.value })}><option value="">Select fund</option>{funds.map(fund => <option key={fund.mutualFundId} value={fund.mutualFundId}>{fund.mutualFundName}</option>)}</select></label>
      <label>Total Value<input required type="number" min="0" step="0.01" value={form.totalValue} onChange={e => setForm({ ...form, totalValue: e.target.value })} /></label>
      <label>Value As Of<DateInput required value={form.valueAsOfDate} onChange={value => setForm({ ...form, valueAsOfDate: value })} /></label>
    </div><div className="form-actions"><button type="button" className="secondary" onClick={resetForm}>Cancel</button><button type="submit" className="primary">{editingId !== null ? 'Update' : 'Create'}</button></div></form>}
    <div className="filter-card"><label>Filter by Mutual Fund<select value={selectedFundId} onChange={e => selectFund(e.target.value)}><option value="">All Mutual Funds</option>{funds.map(fund => <option key={fund.mutualFundId} value={fund.mutualFundId}>{fund.mutualFundName}</option>)}</select></label>{selectedFundId && <button type="button" className="secondary clear-filter" onClick={() => selectFund('')}>Clear Filter</button>}</div>
    {!selectedFundId ? <FundValueOverview funds={funds} loading={loading} error={error} /> : <>
    <div className="table-wrap"><table><thead><tr><th>Mutual Fund</th><th>Total Value</th><th>Value As Of</th><th>Actions</th></tr></thead><tbody>
      {loading ? <tr><td colSpan="4" className="empty">Loading...</td></tr> : items.length === 0 ? <tr><td colSpan="4" className="empty">No fund values found.</td></tr> : items.map((item) => <tr key={item.valId}><td>{getFundName(item.mutualFundId)}</td><td>{Number(item.totalValue).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</td><td>{formatDate(item.valueAsOfDate)}</td><td><div className="actions"><button type="button" onClick={() => { setForm({ mutualFundId: String(item.mutualFundId), totalValue: String(item.totalValue), valueAsOfDate: toInputDate(item.valueAsOfDate) }); setEditingId(item.valId); setShowForm(true) }}>Edit</button><button type="button" className="danger-text" onClick={() => handleDelete(item.valId)}>Delete</button></div></td></tr>)}
    </tbody></table></div>
    {!loading && <Pagination {...paging} loading={loading} onPrevious={() => setPaging(c => ({ ...c, page: c.page - 1 }))} onNext={() => setPaging(c => ({ ...c, page: c.page + 1 }))} onSizeChange={(size) => setPaging(c => ({ ...c, page: 0, size }))} />}
    </>}
  </section>
}

function formatDate(value) { return formatDisplayDate(value) }
function toInputDate(value) { return value ? formatDate(value) : '' }

export function FundValueOverview({ funds, loading, error }) {
  const currency = (value) => new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(Number(value ?? 0))
  return <section aria-label="Latest values by fund">
    <p className="transaction-note">Select a fund from the drop-down above to see its individual transactions.</p>
    <div className="table-wrap"><table>
      <thead><tr><th>Mutual Fund</th><th>Latest Value</th><th>Value As Of</th></tr></thead>
      <tbody>{loading ? <tr><td colSpan="3" className="empty">Loading latest fund values...</td></tr>
        : error ? <tr><td colSpan="3" className="empty">Unable to load latest fund values.</td></tr>
        : funds.length === 0 ? <tr><td colSpan="3" className="empty">No mutual funds found.</td></tr>
        : funds.map(fund => <tr key={fund.mutualFundId}>
          <td>{fund.mutualFundName}</td>
          <td>{fund.latestValue ? currency(fund.latestValue.totalValue) : 'No value recorded'}</td>
          <td>{formatDate(fund.latestValue?.valueAsOfDate)}</td>
        </tr>)}</tbody>
      {!loading && !error && <tfoot><tr><th scope="row">Grand Total</th>
        <td><strong>{currency(funds.reduce((total, fund) => total + Number(fund.latestValue?.totalValue ?? 0), 0))}</strong></td><td />
      </tr></tfoot>}
    </table></div>
  </section>
}
