import { useEffect, useState } from 'react'
import api from '../api/api'

const empty = {
  mutualFundId: '',
  transactionType: 'BUY',
  amount: '',
  units: '',
  avgPrice: '',
  txnDate: ''
}

export default function MutualFundTransactions() {

  const [items, setItems] = useState([])
  const [funds, setFunds] = useState([])

  const [form, setForm] = useState(empty)
  const [editingId, setEditingId] = useState(null)

  const [showForm, setShowForm] = useState(false)

  const [selectedFundId, setSelectedFundId] = useState('')

  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)


  /*
   * Load all transactions
   */
  const load = async () => {

    try {

      setLoading(true)

      const data = await api.transactions.all()

      setItems(data)

      setError('')

    } catch (e) {

      setError(e.message)

    } finally {

      setLoading(false)

    }
  }


  /*
   * Load mutual funds and transactions
   */
  useEffect(() => {

    load()

    api.mutualFunds
      .all()
      .then(setFunds)
      .catch(e => setError(e.message))

  }, [])


  /*
   * Filter transactions based on selected mutual fund
   */
  const filteredItems = selectedFundId
    ? items.filter(
        x => String(x.mutualFundId) === String(selectedFundId)
      )
    : items

  const totals = filteredItems.reduce(
    (summary, transaction) => {
      const amount = Number(transaction.amount) || 0
      const units = Number(transaction.units) || 0

      if (transaction.transactionType === 'SELL') {
        summary.amount -= amount
        summary.units -= units
      } else {
        summary.amount += amount
        summary.units += units
      }

      return summary
    },
    { amount: 0, units: 0 }
  )

  const finalAvgPrice =
    totals.units !== 0
      ? totals.amount / totals.units
      : 0


  /*
   * CREATE / UPDATE
   */
  const submit = async e => {

    e.preventDefault()

    try {

		const data = {
		  mutualFundId: Number(form.mutualFundId),
		  transactionType: form.transactionType,
		  amount: Number(form.amount),
		  units: Number(form.units),
		  avgPrice: Number(form.avgPrice),
		  txnDate: new Date(form.txnDate).toISOString()
		}


      if (editingId) {

        await api.transactions.update(
          editingId,
          {
            ...data,
            mutualFundTxnId: editingId
          }
        )

      } else {

        await api.transactions.create(data)

      }

      reset()

      await load()

    } catch (e) {

      setError(e.message)

    }
  }


  /*
   * RESET FORM
   */
  const reset = () => {

    setForm(empty)

    setEditingId(null)

    setShowForm(false)

  }


  /*
   * EDIT
   */
  const edit = x => {

	setForm({
	  mutualFundId: String(x.mutualFundId),
	  transactionType: x.transactionType || 'BUY',
	  amount: String(x.amount ?? ''),
	  units: String(x.units ?? ''),
	  avgPrice: String(x.avgPrice ?? ''),
	  txnDate: toInputDate(x.txnDate)
	})

    setEditingId(x.mutualFundTxnId)

    setShowForm(true)
  }


  /*
   * DELETE
   */
  const remove = async id => {

    if (!confirm('Delete this transaction?')) {
      return
    }

    try {

      await api.transactions.remove(id)

      await load()

    } catch (e) {

      setError(e.message)

    }
  }


  /*
   * GET MUTUAL FUND NAME
   */
  const fundName = id => {

    return funds.find(
      f => f.mutualFundId === id
    )?.mutualFundName || `Fund #${id}`

  }


  return (
    <section>

      {/* =========================
          HEADER
         ========================= */}

      <div className="section-header">

        <div>

          <h2>
            Mutual Fund Transactions
          </h2>

          <p>
            Track purchases, redemptions and other transactions.
          </p>

        </div>


        <button
          className="primary"
          onClick={() => {
            reset()
            setShowForm(true)
          }}
        >
          + Add Transaction
        </button>

      </div>


      {/* =========================
          ERROR
         ========================= */}

      {error && (
        <div className="error">
          {error}
        </div>
      )}


      {/* =========================
          CREATE / UPDATE FORM
         ========================= */}

      {showForm && (

        <form
          className="form-card"
          onSubmit={submit}
        >

          <div className="form-grid">

            {/* Mutual Fund */}

            <label>
              Mutual Fund

              <select
                required
                value={form.mutualFundId}
                onChange={e =>
                  setForm({
                    ...form,
                    mutualFundId: e.target.value
                  })
                }
              >

                <option value="">
                  Select fund
                </option>

                {funds.map(f => (

                  <option
                    key={f.mutualFundId}
                    value={f.mutualFundId}
                  >
                    {f.mutualFundName}
                  </option>

                ))}

              </select>

            </label>
			
			<label>
			  Transaction Type

			  <select
			    required
			    value={form.transactionType}
			    onChange={e =>
			      setForm({
			        ...form,
			        transactionType: e.target.value
			      })
			    }
			  >
			    <option value="BUY">
			      BUY
			    </option>

			    <option value="SELL">
			      SELL
			    </option>
			  </select>
			</label>


            {/* Amount */}

            <label>
              Amount

              <input
                required
                type="number"
                min="0"
                step="0.01"
                value={form.amount}
                onChange={e =>
                  setForm({
                    ...form,
                    amount: e.target.value
                  })
                }
              />

            </label>


            {/* Units */}

            <label>
              Units

              <input
                required
                type="number"
                min="0"
                step="0.001"
                value={form.units}
                onChange={e =>
                  setForm({
                    ...form,
                    units: e.target.value
                  })
                }
              />

            </label>


            {/* Average Price */}

            <label>
              Average Price

              <input
                required
                type="number"
                min="0"
                step="0.001"
                value={form.avgPrice}
                onChange={e =>
                  setForm({
                    ...form,
                    avgPrice: e.target.value
                  })
                }
              />

            </label>


            {/* Transaction Date */}

            <label>
              Transaction Date

              <input
                required
                type="datetime-local"
                value={form.txnDate}
                onChange={e =>
                  setForm({
                    ...form,
                    txnDate: e.target.value
                  })
                }
              />

            </label>

          </div>


          {/* Form Actions */}

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


      {/* =========================
          FILTER
         ========================= */}

      <div className="filter-card">

        <label>

          Filter by Mutual Fund

          <select
            value={selectedFundId}
            onChange={e =>
              setSelectedFundId(e.target.value)
            }
          >

            <option value="">
              All Mutual Funds
            </option>

            {funds.map(f => (

              <option
                key={f.mutualFundId}
                value={f.mutualFundId}
              >
                {f.mutualFundName}
              </option>

            ))}

          </select>

        </label>

        {selectedFundId && (

          <button
            className="secondary clear-filter"
            onClick={() => setSelectedFundId('')}
          >
            Clear Filter
          </button>

        )}

      </div>


      {/* =========================
          TRANSACTION TABLE
         ========================= */}

      <div className="table-wrap">

        <table>

          <thead>

            <tr>

              {			[
			  'Mutual Fund',
			  'Transaction Type',
			  'Amount',
			  'Units',
			  'Avg Price',
			  'Transaction Date',
			  'Actions'
			].map(h => (

                <th key={h}>
                  {h}
                </th>

              ))}

            </tr>

          </thead>


          <tbody>

            {loading ? (

              <tr>

                <td
                  colSpan="7"
                  className="empty"
                >
                  Loading...
                </td>

              </tr>

            ) : filteredItems.length === 0 ? (

              <tr>

                <td
                  colSpan="7"
                  className="empty"
                >
                  No transactions found.
                </td>

              </tr>

            ) : (

              filteredItems.map(x => (

                <tr
                  key={x.mutualFundTxnId}
                >
                  {/* Mutual Fund */}

                  <td>
                    {fundName(x.mutualFundId)}
                  </td>

				  {/* Transaction Type*/}	
				  <td>
				    {x.transactionType}
				  </td>

                  {/* Amount */}

                  <td>
                    {formatAmount(x.amount)}
                  </td>


                  {/* Units */}

                  <td>
                    {formatDecimal(x.units)}
                  </td>


                  {/* Average Price */}

                  <td>
                    {formatDecimal(x.avgPrice)}
                  </td>


                  {/* Transaction Date */}

                  <td>
                    {formatDate(x.txnDate)}
                  </td>

                  {/* Actions */}

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
                          remove(x.mutualFundTxnId)
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
	  
	  {/* =========================

	      TRANSACTION TOTALS

	     ========================= */}

	  <div className="transaction-summary">

	    <div className="summary-item">

	      <span className="summary-label">

	        Total Amount

	      </span>

	      <strong>

	        {formatAmount(totals.amount)}

	      </strong>

	    </div>

	    <div className="summary-item">

	      <span className="summary-label">
	        Total Units
	      </span>
	      <strong>
	        {formatDecimal(totals.units)}
	      </strong>
	    </div>
	    <div className="summary-item">
	      <span className="summary-label">
	        Final Average Price / Unit
	      </span>
	      <strong>
	        {formatDecimal(finalAvgPrice)}
	      </strong>
	    </div>
	  </div>

    </section>
  )
}


/*
 * Format amount
 */
function formatAmount(value) {

  if (
    value === null ||
    value === undefined
  ) {
    return '-'
  }

  return Number(value).toLocaleString(
    undefined,
    {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    }
  )
}


/*
 * Format units / average price
 */
function formatDecimal(value) {

  if (
    value === null ||
    value === undefined
  ) {
    return '-'
  }

  return Number(value).toLocaleString(
    undefined,
    {
      minimumFractionDigits: 3,
      maximumFractionDigits: 3
    }
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


/*
 * Convert timestamp to datetime-local input
 */
function toInputDate(x) {

  return x
    ? new Date(x)
        .toISOString()
        .slice(0, 16)
    : ''
}