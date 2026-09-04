import { useEffect, useMemo, useState } from 'react'
import api from '../api/api'

const empty = {
  brokerAccountId: '',
  mutualFundName: ''
}

const PAGE_SIZE_OPTIONS = [10, 15, 20, 25]

export default function MutualFunds() {
  const [items, setItems] = useState([])
  const [brokers, setBrokers] = useState([])
  const [form, setForm] = useState(empty)
  const [editingId, setEditingId] = useState(null)
  const [showForm, setShowForm] = useState(false)

  const [searchText, setSearchText] = useState('')
  const [selectedBrokerId, setSelectedBrokerId] = useState('')

  const [currentPage, setCurrentPage] = useState(1)
  const [pageSize, setPageSize] = useState(10)

  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)

  const load = async () => {
    try {
      setLoading(true)

      const [funds, brokerAccounts] = await Promise.all([
        api.mutualFunds.all(),
        api.brokerAccounts.all()
      ])

      setItems(funds || [])
      setBrokers(brokerAccounts || [])
      setError('')
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
  }, [])

  const submit = async e => {
    e.preventDefault()

    try {
      const data = {
        brokerAccountId: Number(form.brokerAccountId),
        mutualFundName: form.mutualFundName.trim()
      }

      if (editingId) {
        await api.mutualFunds.update(editingId, {
          ...data,
          mutualFundId: editingId
        })
      } else {
        await api.mutualFunds.create(data)
      }

      reset()
      await load()
    } catch (e) {
      setError(e.message)
    }
  }

  const reset = () => {
    setForm({ ...empty })
    setEditingId(null)
    setShowForm(false)
  }

  const edit = x => {
    setForm({
      brokerAccountId: String(x.brokerAccountId),
      mutualFundName: x.mutualFundName || ''
    })

    setEditingId(x.mutualFundId)
    setShowForm(true)
    setError('')
  }

  const remove = async id => {
    if (!confirm('Delete this mutual fund?')) {
      return
    }

    try {
      await api.mutualFunds.remove(id)
      await load()

      // Make sure the current page remains valid after deletion.
      setCurrentPage(page => Math.max(1, page))
    } catch (e) {
      setError(e.message)
    }
  }

  const brokerName = id =>
    brokers.find(
      b => String(b.id) === String(id)
    )?.brokerName || `Broker #${id}`

  /*
   * Filter funds by:
   * 1. Mutual fund name
   * 2. Broker account
   */
  const filteredItems = useMemo(() => {
    const search = searchText.trim().toLowerCase()

    return items.filter(item => {
      const matchesSearch =
        !search ||
        (item.mutualFundName || '')
          .toLowerCase()
          .includes(search)

      const matchesBroker =
        !selectedBrokerId ||
        String(item.brokerAccountId) === String(selectedBrokerId)

      return matchesSearch && matchesBroker
    })
  }, [items, searchText, selectedBrokerId])

  /*
   * Calculate pagination.
   */
  const totalItems = filteredItems.length

  const totalPages = Math.max(
    1,
    Math.ceil(totalItems / pageSize)
  )

  /*
   * If filtering reduces the number of pages,
   * move back to the last valid page.
   */
  useEffect(() => {
    if (currentPage > totalPages) {
      setCurrentPage(totalPages)
    }
  }, [currentPage, totalPages])

  /*
   * Get only the records for the current page.
   */
  const paginatedItems = useMemo(() => {
    const startIndex = (currentPage - 1) * pageSize
    const endIndex = startIndex + pageSize

    return filteredItems.slice(startIndex, endIndex)
  }, [filteredItems, currentPage, pageSize])

  const startItem =
    totalItems === 0
      ? 0
      : (currentPage - 1) * pageSize + 1

  const endItem =
    totalItems === 0
      ? 0
      : Math.min(currentPage * pageSize, totalItems)

  /*
   * Reset pagination when the filter changes.
   */
  const handleSearchChange = value => {
    setSearchText(value)
    setCurrentPage(1)
  }

  const handleBrokerChange = value => {
    setSelectedBrokerId(value)
    setCurrentPage(1)
  }

  const clearFilters = () => {
    setSearchText('')
    setSelectedBrokerId('')
    setCurrentPage(1)
  }

  const handlePageSizeChange = value => {
    const newPageSize = Math.min(
      Number(value),
      25
    )

    setPageSize(newPageSize)
    setCurrentPage(1)
  }

  /*
   * Generate page numbers.
   *
   * Example:
   * 1 2 3 4 5
   */
  const pageNumbers = Array.from(
    { length: totalPages },
    (_, index) => index + 1
  )

  return (
    <section>
      <div className="section-header">
        <div>
          <h2>Mutual Funds</h2>
          <p>Manage funds linked to broker accounts.</p>
        </div>

        <button
          className="primary"
          onClick={() => {
            reset()
            setShowForm(true)
          }}
        >
          + Add Mutual Fund
        </button>
      </div>

      {error && (
        <div className="error">
          {error}
        </div>
      )}

      {showForm && (
        <form
          className="form-card"
          onSubmit={submit}
        >
          <div className="form-grid">
            <label>
              Broker Account

              <select
                required
                value={form.brokerAccountId}
                onChange={e =>
                  setForm({
                    ...form,
                    brokerAccountId: e.target.value
                  })
                }
              >
                <option value="">
                  Select account
                </option>

                {brokers.map(b => (
                  <option
                    key={b.id}
                    value={b.id}
                  >
                    {b.brokerName} — {b.accountId}
                  </option>
                ))}
              </select>
            </label>

            <label>
              Mutual Fund Name

              <input
                required
                value={form.mutualFundName}
                onChange={e =>
                  setForm({
                    ...form,
                    mutualFundName: e.target.value
                  })
                }
              />
            </label>
          </div>

          <div className="form-actions">
            <button
              type="button"
              className="secondary"
              onClick={reset}
            >
              Cancel
            </button>

            <button
              className="primary"
              type="submit"
            >
              {editingId ? 'Update' : 'Create'}
            </button>
          </div>
        </form>
      )}

      {/* Filters */}
      <div className="filter-card">
        <div className="filter-field">
          <label>Search Mutual Funds</label>

          <input
            type="text"
            placeholder="Search by fund name..."
            value={searchText}
            onChange={e =>
              handleSearchChange(e.target.value)
            }
          />
        </div>

        <div className="filter-field">
          <label>Broker Account</label>

          <select
            value={selectedBrokerId}
            onChange={e =>
              handleBrokerChange(e.target.value)
            }
          >
            <option value="">
              All Broker Accounts
            </option>

            {brokers.map(b => (
              <option
                key={b.id}
                value={b.id}
              >
                {b.brokerName} — {b.accountId}
              </option>
            ))}
          </select>
        </div>

        <div className="filter-actions">
          <button
            type="button"
            className="secondary"
            onClick={clearFilters}
          >
            Clear Filters
          </button>
        </div>
      </div>

      {/* Table */}
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Broker</th>
              <th>Mutual Fund</th>
              <th>Actions</th>
            </tr>
          </thead>

          <tbody>
            {loading ? (
              <tr>
                <td
                  colSpan="3"
                  className="empty"
                >
                  Loading...
                </td>
              </tr>
            ) : paginatedItems.length === 0 ? (
              <tr>
                <td
                  colSpan="3"
                  className="empty"
                >
                  {totalItems === 0
                    ? 'No mutual funds found.'
                    : 'No mutual funds found on this page.'}
                </td>
              </tr>
            ) : (
              paginatedItems.map(x => (
                <tr
                  key={x.mutualFundId}
                >
                  <td>
                    {brokerName(
                      x.brokerAccountId
                    )}
                  </td>

                  <td>
                    {x.mutualFundName}
                  </td>

                  <td>
                    <div className="actions">
                      <button
                        onClick={() => edit(x)}
                      >
                        Edit
                      </button>

                      <button
                        className="danger-text"
                        onClick={() =>
                          remove(
                            x.mutualFundId
                          )
                        }
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

      {/* Pagination */}
      {!loading && totalItems > 0 && (
        <div className="pagination-container">
          <div className="pagination-info">
            Showing {startItem}–{endItem} of{' '}
            {totalItems} funds
          </div>

          <div className="pagination-controls">
            <button
              className="pagination-button"
              disabled={currentPage === 1}
              onClick={() =>
                setCurrentPage(
                  page => Math.max(1, page - 1)
                )
              }
            >
              ‹ Previous
            </button>

            <div className="pagination-pages">
              {pageNumbers.map(page => (
                <button
                  key={page}
                  className={`pagination-button ${
                    currentPage === page
                      ? 'active'
                      : ''
                  }`}
                  onClick={() =>
                    setCurrentPage(page)
                  }
                >
                  {page}
                </button>
              ))}
            </div>

            <button
              className="pagination-button"
              disabled={
                currentPage === totalPages
              }
              onClick={() =>
                setCurrentPage(
                  page =>
                    Math.min(
                      totalPages,
                      page + 1
                    )
                )
              }
            >
              Next ›
            </button>
          </div>

          <div className="page-size-control">
            <label htmlFor="pageSize">
              Rows per page
            </label>

            <select
              id="pageSize"
              value={pageSize}
              onChange={e =>
                handlePageSizeChange(
                  e.target.value
                )
              }
            >
              {PAGE_SIZE_OPTIONS.map(size => (
                <option
                  key={size}
                  value={size}
                >
                  {size}
                </option>
              ))}
            </select>
          </div>
        </div>
      )}
    </section>
  )
}