import { formatDisplayDate } from '../utils/date'
import { useEffect, useState } from 'react'
import api from '../api/api'
import Pagination from './Pagination'

const empty = { brokerName: '', accountId: '' }
const initialPage = { page: 0, size: 20, totalPages: 0, totalElements: 0, first: true, last: true }

export default function BrokerAccounts() {
  const [items, setItems] = useState([])
  const [paging, setPaging] = useState(initialPage)
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
        const response = await api.brokerAccounts.all({ page: paging.page, size: paging.size })
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
  }, [paging.page, paging.size, refreshKey])

  const reload = () => setRefreshKey((key) => key + 1)
  const reset = () => { setForm(empty); setEditingId(null); setShowForm(false) }

  const submit = async (event) => {
    event.preventDefault()
    try {
      if (editingId) await api.brokerAccounts.update(editingId, { ...form, id: editingId })
      else await api.brokerAccounts.create(form)
      reset()
      reload()
    } catch (e) { setError(e.message) }
  }

  const edit = (account) => {
    setForm({ brokerName: account.brokerName || '', accountId: account.accountId || '' })
    setEditingId(account.id)
    setShowForm(true)
  }

  const remove = async (id) => {
    if (!window.confirm('Delete this broker account?')) return
    try {
      await api.brokerAccounts.remove(id)
      if (items.length === 1 && paging.page > 0) setPaging((current) => ({ ...current, page: current.page - 1 }))
      else reload()
    } catch (e) { setError(e.message) }
  }

  return <section>
    <SectionHeader title="Broker Accounts" button="Add Broker Account" onClick={() => { reset(); setShowForm(true) }} />
    {error && <div className="error">{error}</div>}
    {showForm && <form className="form-card" onSubmit={submit}>
      <div className="form-grid">
        <label>Broker Name<input required value={form.brokerName} onChange={e => setForm({ ...form, brokerName: e.target.value })} /></label>
        <label>Account ID<input required value={form.accountId} onChange={e => setForm({ ...form, accountId: e.target.value })} /></label>
      </div>
      <FormActions editing={!!editingId} onCancel={reset} />
    </form>}
    <Table loading={loading} empty={items.length === 0} headers={['Broker Name', 'Account ID', 'Created', 'Updated', 'Actions']}>
      {items.map(account => <tr key={account.id}>
        <td>{account.brokerName}</td><td>{account.accountId}</td>
        <td>{formatDate(account.createDate)}</td><td>{formatDate(account.updateDate)}</td>
        <td><Actions onEdit={() => edit(account)} onDelete={() => remove(account.id)} /></td>
      </tr>)}
    </Table>
    {!loading && <Pagination {...paging} loading={loading}
      onPrevious={() => setPaging((current) => ({ ...current, page: current.page - 1 }))}
      onNext={() => setPaging((current) => ({ ...current, page: current.page + 1 }))}
      onSizeChange={(size) => setPaging((current) => ({ ...current, page: 0, size }))}
    />}
  </section>
}

function SectionHeader({ title, button, onClick }) {
  return <div className="section-header"><div><h2>{title}</h2><p>Manage your {title.toLowerCase()}.</p></div><button className="primary" onClick={onClick}>+ {button}</button></div>
}
function FormActions({ editing, onCancel }) { return <div className="form-actions"><button type="button" className="secondary" onClick={onCancel}>Cancel</button><button className="primary" type="submit">{editing ? 'Update' : 'Create'}</button></div> }
function Actions({ onEdit, onDelete }) { return <div className="actions"><button onClick={onEdit}>Edit</button><button className="danger-text" onClick={onDelete}>Delete</button></div> }
function Table({ loading, empty, headers, children }) { return <div className="table-wrap">{loading ? <div className="empty">Loading...</div> : <table><thead><tr>{headers.map(h => <th key={h}>{h}</th>)}</tr></thead><tbody>{empty ? <tr><td colSpan={headers.length} className="empty">No broker accounts found.</td></tr> : children}</tbody></table>}</div> }

function formatDate(value) { return formatDisplayDate(value) }
