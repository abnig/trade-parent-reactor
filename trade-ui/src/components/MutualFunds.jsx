import { useEffect, useState } from 'react'
import api from '../api/api'
import Pagination from './Pagination'

const empty = { brokerAccountId: '', mutualFundName: '' }
const initialPage = { page: 0, size: 20, totalPages: 0, totalElements: 0, first: true, last: true }

export default function MutualFunds() {
  const [items, setItems] = useState([])
  const [brokers, setBrokers] = useState([])
  const [paging, setPaging] = useState(initialPage)
  const [selectedBrokerId, setSelectedBrokerId] = useState('')
  const [form, setForm] = useState(empty)
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
        const response = selectedBrokerId
          ? await api.mutualFunds.byBrokerAccount(selectedBrokerId, paging)
          : await api.mutualFunds.all(paging)
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
  }, [paging.page, paging.size, selectedBrokerId, refreshKey])

  useEffect(() => {
    let active = true
    api.brokerAccounts.all({ page: 0, size: 100 })
      .then((response) => { if (active) setBrokers(response.content) })
      .catch((e) => { if (active) setError(e.message) })
    return () => { active = false }
  }, [])

  const reload = () => setRefreshKey((key) => key + 1)
  const reset = () => { setForm(empty); setEditingId(null); setShowForm(false) }

  const submit = async (event) => {
    event.preventDefault()
    try {
      const data = { brokerAccountId: Number(form.brokerAccountId), mutualFundName: form.mutualFundName.trim() }
      if (editingId) await api.mutualFunds.update(editingId, { ...data, mutualFundId: editingId })
      else await api.mutualFunds.create(data)
      reset()
      reload()
    } catch (e) { setError(e.message) }
  }

  const remove = async (id) => {
    if (!window.confirm('Delete this mutual fund?')) return
    try {
      await api.mutualFunds.remove(id)
      if (items.length === 1 && paging.page > 0) setPaging((current) => ({ ...current, page: current.page - 1 }))
      else reload()
    } catch (e) { setError(e.message) }
  }

  const selectBroker = (value) => {
    setSelectedBrokerId(value)
    setPaging((current) => ({ ...current, page: 0 }))
  }

  const brokerName = (id) => brokers.find((broker) => String(broker.id) === String(id))?.brokerName || `Broker #${id}`

  return <section>
    <div className="section-header"><div><h2>Mutual Funds</h2><p>Manage funds linked to broker accounts.</p></div><button className="primary" onClick={() => { reset(); setShowForm(true) }}>+ Add Mutual Fund</button></div>
    {error && <div className="error">{error}</div>}
    {showForm && <form className="form-card" onSubmit={submit}><div className="form-grid">
      <label>Broker Account<select required value={form.brokerAccountId} onChange={e => setForm({ ...form, brokerAccountId: e.target.value })}><option value="">Select account</option>{brokers.map(b => <option key={b.id} value={b.id}>{b.brokerName} — {b.accountId}</option>)}</select></label>
      <label>Mutual Fund Name<input required value={form.mutualFundName} onChange={e => setForm({ ...form, mutualFundName: e.target.value })} /></label>
    </div><div className="form-actions"><button type="button" className="secondary" onClick={reset}>Cancel</button><button className="primary" type="submit">{editingId ? 'Update' : 'Create'}</button></div></form>}
    <div className="filter-card"><label>Broker Account<select value={selectedBrokerId} onChange={e => selectBroker(e.target.value)}><option value="">All Broker Accounts</option>{brokers.map(b => <option key={b.id} value={b.id}>{b.brokerName} — {b.accountId}</option>)}</select></label>{selectedBrokerId && <button type="button" className="secondary clear-filter" onClick={() => selectBroker('')}>Clear Filter</button>}</div>
    <div className="table-wrap"><table><thead><tr><th>Broker</th><th>Mutual Fund</th><th>Actions</th></tr></thead><tbody>
      {loading ? <tr><td colSpan="3" className="empty">Loading...</td></tr> : items.length === 0 ? <tr><td colSpan="3" className="empty">No mutual funds found.</td></tr> : items.map((fund) => <tr key={fund.mutualFundId}><td>{brokerName(fund.brokerAccountId)}</td><td>{fund.mutualFundName}</td><td><div className="actions"><button onClick={() => { setForm({ brokerAccountId: String(fund.brokerAccountId), mutualFundName: fund.mutualFundName || '' }); setEditingId(fund.mutualFundId); setShowForm(true) }}>Edit</button><button className="danger-text" onClick={() => remove(fund.mutualFundId)}>Delete</button></div></td></tr>)}
    </tbody></table></div>
    {!loading && <Pagination {...paging} loading={loading} onPrevious={() => setPaging(c => ({ ...c, page: c.page - 1 }))} onNext={() => setPaging(c => ({ ...c, page: c.page + 1 }))} onSizeChange={(size) => setPaging(c => ({ ...c, page: 0, size }))} />}
  </section>
}
