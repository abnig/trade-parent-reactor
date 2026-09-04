const PAGE_SIZES = [10, 20, 50, 100]

export default function Pagination({
  page,
  size,
  totalPages,
  totalElements,
  first,
  last,
  loading = false,
  onPrevious,
  onNext,
  onSizeChange
}) {
  const hasPages = totalPages > 0
  const pageLabel = hasPages ? `Page ${page + 1} of ${totalPages}` : 'Page 0 of 0'

  return (
    <div className="pagination-container" aria-label="Pagination">
      <div className="pagination-info">{totalElements} record{totalElements === 1 ? '' : 's'}</div>

      <div className="pagination-controls">
        <button
          type="button"
          className="pagination-button"
          aria-label="Previous page"
          disabled={loading || first || !hasPages}
          onClick={onPrevious}
        >
          ‹ Previous
        </button>
        <span className="pagination-page" aria-live="polite">{pageLabel}</span>
        <button
          type="button"
          className="pagination-button"
          aria-label="Next page"
          disabled={loading || last || !hasPages}
          onClick={onNext}
        >
          Next ›
        </button>
      </div>

      <div className="page-size-control">
        <label htmlFor="pagination-page-size">Rows per page</label>
        <select
          id="pagination-page-size"
          value={size}
          disabled={loading}
          onChange={(event) => onSizeChange(Number(event.target.value))}
        >
          {PAGE_SIZES.map((option) => <option key={option} value={option}>{option}</option>)}
        </select>
      </div>
    </div>
  )
}
