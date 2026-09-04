import { useEffect, useState } from 'react'
import api from '../api/api'

const emptyForm = {
  mutualFundId: '',
  totalValue: '',
  valueAsOfDate: ''
}

export default function MutualFundValues() {
  const [items, setItems] = useState([])
  const [funds, setFunds] = useState([])
  const [selectedFundId, setSelectedFundId] = useState('')
  const [form, setForm] = useState({ ...emptyForm })
  const [editingId, setEditingId] = useState(null)
  const [showForm, setShowForm] = useState(false)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)

  const loadValues = async () => {
    try {
      setLoading(true)
      const values = await api.values.all()
      setItems(Array.isArray(values) ? values : [])
      setError('')
    } catch (e) {
      setError(e.message || 'Failed to load fund values.')
    } finally {
      setLoading(false)
    }
  }

  const loadFunds = async () => {
    try {
      const mutualFunds = await api.mutualFunds.all()
      setFunds(Array.isArray(mutualFunds) ? mutualFunds : [])
    } catch (e) {
      setError(e.message || 'Failed to load mutual funds.')
    }
  }

  useEffect(() => {
    loadValues()
    loadFunds()
  }, [])

  // Filter by Mutual Fund, then sort by Value As Of Date ascending.
  const displayedItems = [...items]
    .filter((item) =>
      selectedFundId
        ? String(item.mutualFundId) === String(selectedFundId)
        : true
    )
    .sort((a, b) => {
      const dateA = new Date(a.valueAsOfDate).getTime()
      const dateB = new Date(b.valueAsOfDate).getTime()

      if (Number.isNaN(dateA)) return 1
      if (Number.isNaN(dateB)) return -1
      return dateA - dateB
    })

  const getFundName = (fundId) => {
    const fund = funds.find(
      (item) => String(item.mutualFundId) === String(fundId)
    )

    return fund ? fund.mutualFundName : `Fund #${fundId}`
  }

  const resetForm = () => {
    setForm({ ...emptyForm })
    setEditingId(null)
    setShowForm(false)
  }

  const handleSubmit = async (event) => {
    event.preventDefault()

    try {
      const data = {
        mutualFundId: Number(form.mutualFundId),
        totalValue: Number(form.totalValue),
        valueAsOfDate: new Date(form.valueAsOfDate).toISOString()
      }

      if (editingId !== null) {
        await api.values.update(editingId, {
          ...data,
          valId: editingId
        })
      } else {
        await api.values.create(data)
      }

      resetForm()
      await loadValues()
    } catch (e) {
      setError(e.message || 'Failed to save fund value.')
    }
  }

  const handleEdit = (item) => {
    setForm({
      mutualFundId: String(item.mutualFundId),
      totalValue: String(item.totalValue),
      valueAsOfDate: toInputDate(item.valueAsOfDate)
    })
    setEditingId(item.valId)
    setShowForm(true)
  }

  const handleDelete = async (id) => {
    if (!window.confirm('Delete this fund value?')) return

    try {
      await api.values.remove(id)
      await loadValues()
    } catch (e) {
      setError(e.message || 'Failed to delete fund value.')
    }
  }

  const handleAdd = () => {
    setForm({ ...emptyForm })
    setEditingId(null)
    setShowForm(true)
  }

  return (
    <section>
      <div className="section-header">
        <div>
          <h2>Mutual Fund Values</h2>
          <p>Maintain historical fund valuation records.</p>
        </div>

        <button type="button" className="primary" onClick={handleAdd}>
          + Add Fund Value
        </button>
      </div>

      {error && <div className="error">{error}</div>}
	  
	  {/* Add/Edit form - shown only when requested */}
	  {showForm && (
	    <form className="form-card" onSubmit={handleSubmit}>
	      <div className="form-grid">
	        <label>
	          Mutual Fund
	          <select
	            required
	            value={form.mutualFundId}
	            onChange={(event) =>
	              setForm({ ...form, mutualFundId: event.target.value })
	            }
	          >
	            <option value="">Select fund</option>
	            {funds.map((fund) => (
	              <option key={fund.mutualFundId} value={fund.mutualFundId}>
	                {fund.mutualFundName}
	              </option>
	            ))}
	          </select>
	        </label>

	        <label>
	          Total Value
	          <input
	            required
	            type="number"
	            min="0"
	            step="0.01"
	            value={form.totalValue}
	            onChange={(event) =>
	              setForm({ ...form, totalValue: event.target.value })
	            }
	          />
	        </label>

	        <label>
	          Value As Of
	          <input
	            required
	            type="datetime-local"
	            value={form.valueAsOfDate}
	            onChange={(event) =>
	              setForm({ ...form, valueAsOfDate: event.target.value })
	            }
	          />
	        </label>
	      </div>

	      <div className="form-actions">
	        <button type="button" className="secondary" onClick={resetForm}>
	          Cancel
	        </button>
	        <button type="submit" className="primary">
	          {editingId !== null ? 'Update' : 'Create'}
	        </button>
	      </div>
	    </form>
	  )}
	  

      {/* Listing filter */}
      <div className="filter-card">
        <label>
          Filter by Mutual Fund
          <select
            value={selectedFundId}
            onChange={(event) => setSelectedFundId(event.target.value)}
          >
            <option value="">All Mutual Funds</option>
            {funds.map((fund) => (
              <option key={fund.mutualFundId} value={fund.mutualFundId}>
                {fund.mutualFundName}
              </option>
            ))}
          </select>
        </label>

        {selectedFundId && (
          <button
            type="button"
            className="secondary clear-filter"
            onClick={() => setSelectedFundId('')}
          >
            Clear Filter
          </button>
        )}
      </div>


      {/* Listing: ID column intentionally removed */}
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Mutual Fund</th>
              <th>Total Value</th>
              <th>Value As Of</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan="4" className="empty">
                  Loading...
                </td>
              </tr>
            ) : displayedItems.length === 0 ? (
              <tr>
                <td colSpan="4" className="empty">
                  No fund values found.
                </td>
              </tr>
            ) : (
              displayedItems.map((item) => (
                <tr key={item.valId}>
                  <td>{getFundName(item.mutualFundId)}</td>
                  <td>
                    {Number(item.totalValue).toLocaleString(undefined, {
                      minimumFractionDigits: 2,
                      maximumFractionDigits: 2
                    })}
                  </td>
                  <td>{formatDate(item.valueAsOfDate)}</td>
                  <td>
                    <div className="actions">
                      <button type="button" onClick={() => handleEdit(item)}>
                        Edit
                      </button>
                      <button
                        type="button"
                        className="danger-text"
                        onClick={() => handleDelete(item.valId)}
                      >
                        Delete
                      </button>
                    </div>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </section>
  )
}

function formatDate(x) { 
  if (!x) return '-';
  
  const date = new Date(x);
  
  // Use toLocaleDateString with an English locale to get the 3-letter month (e.g., "Jan")
  const day = date.toLocaleDateString('en-GB', { day: '2-digit' });
  const month = date.toLocaleDateString('en-GB', { month: 'short' });
  const year = date.toLocaleDateString('en-GB', { year: 'numeric' });
  
  return `${day}-${month}-${year}`;
}

function toInputDate(value) {
  return value ? new Date(value).toISOString().slice(0, 16) : ''
}
