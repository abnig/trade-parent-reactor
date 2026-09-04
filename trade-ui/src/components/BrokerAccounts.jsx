import { useEffect, useState } from 'react'
import api from '../api/api'

const empty = { brokerName: '', accountId: '' }

export default function BrokerAccounts() {
  const [items, setItems] = useState([])
  const [form, setForm] = useState(empty)
  const [editingId, setEditingId] = useState(null)
  const [showForm, setShowForm] = useState(false)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)

  const load = async () => {
    try { setLoading(true); setItems(await api.brokerAccounts.all()); setError('') }
    catch (e) { setError(e.message) }
    finally { setLoading(false) }
  }

  useEffect(() => { load() }, [])

  const submit = async (e) => {
    e.preventDefault()
    try {
      if (editingId) await api.brokerAccounts.update(editingId, { ...form, id: editingId })
      else await api.brokerAccounts.create(form)
      reset()
      load()
    } catch (e) { setError(e.message) }
  }

  const reset = () => { setForm(empty); setEditingId(null); setShowForm(false) }

  const edit = (x) => {
    setForm({ brokerName: x.brokerName || '', accountId: x.accountId || '' })
    setEditingId(x.id); setShowForm(true)
  }

  const remove = async (id) => {
    if (!confirm('Delete this broker account?')) return
    try { await api.brokerAccounts.remove(id); load() }
    catch (e) { setError(e.message) }
  }

  return <section>
    <SectionHeader title="Broker Accounts" button="Add Broker Account" onClick={() => { reset(); setShowForm(true) }} />
    {error && <div className="error">{error}</div>}
    {showForm && <form className="form-card" onSubmit={submit}>
      <div className="form-grid">
        <label>Broker Name<input required value={form.brokerName} onChange={e => setForm({...form, brokerName:e.target.value})}/></label>
        <label>Account ID<input required value={form.accountId} onChange={e => setForm({...form, accountId:e.target.value})}/></label>
      </div>
      <FormActions editing={!!editingId} onCancel={reset} />
    </form>}
    <Table loading={loading} headers={['Broker Name','Account ID','Created','Updated','Actions']}>
      {items.map(x => <tr key={x.id}>
        <td>{x.brokerName}</td><td>{x.accountId}</td>
        <td>{formatDate(x.createDate)}</td><td>{formatDate(x.updateDate)}</td>
        <td><Actions onEdit={() => edit(x)} onDelete={() => remove(x.id)} /></td>
      </tr>)}
    </Table>
  </section>
}

function SectionHeader({title, button, onClick}) {
  return <div className="section-header"><div><h2>{title}</h2><p>Manage your {title.toLowerCase()}.</p></div><button className="primary" onClick={onClick}>+ {button}</button></div>
}
function FormActions({editing,onCancel}) { return <div className="form-actions"><button type="button" className="secondary" onClick={onCancel}>Cancel</button><button className="primary" type="submit">{editing?'Update':'Create'}</button></div> }
function Actions({onEdit,onDelete}) { return <div className="actions"><button onClick={onEdit}>Edit</button><button className="danger-text" onClick={onDelete}>Delete</button></div> }
function Table({loading,headers,children}) { return <div className="table-wrap">{loading?<div className="empty">Loading...</div>:<table><thead><tr>{headers.map(h=><th key={h}>{h}</th>)}</tr></thead><tbody>{children}</tbody></table>}</div> }

function formatDate(x) { 
  if (!x) return '-';
  
  const date = new Date(x);
  
  // Use toLocaleDateString with an English locale to get the 3-letter month (e.g., "Jan")
  const day = date.toLocaleDateString('en-GB', { day: '2-digit' });
  const month = date.toLocaleDateString('en-GB', { month: 'short' });
  const year = date.toLocaleDateString('en-GB', { year: 'numeric' });
  
  return `${day}-${month}-${year}`;
}