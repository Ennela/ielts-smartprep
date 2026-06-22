/**
 * Reusable Pagination component for admin list pages.
 *
 * Features:
 * - Prev / Next buttons
 * - Numbered page buttons with ellipsis
 * - "Showing X–Y of Z items" info
 * - Loading fade indicator during page transitions
 */
export default function Pagination({
  page,
  totalPages,
  totalElements,
  size,
  onPageChange,
  isFetching = false,
  isPlaceholderData = false,
}) {
  if (totalPages <= 1 && totalElements <= 0) return null;

  const startItem = page * size + 1;
  const endItem = Math.min((page + 1) * size, totalElements);

  // Generate page numbers to display
  const getPageNumbers = () => {
    const pages = [];
    const maxVisible = 5;

    if (totalPages <= maxVisible + 2) {
      // Show all pages if total is small
      for (let i = 0; i < totalPages; i++) pages.push(i);
    } else {
      // Always show first page
      pages.push(0);

      let start = Math.max(1, page - 1);
      let end = Math.min(totalPages - 2, page + 1);

      // Adjust window to always show maxVisible pages
      if (page <= 2) {
        end = Math.min(maxVisible - 1, totalPages - 2);
      } else if (page >= totalPages - 3) {
        start = Math.max(1, totalPages - maxVisible);
      }

      if (start > 1) pages.push('ellipsis-start');
      for (let i = start; i <= end; i++) pages.push(i);
      if (end < totalPages - 2) pages.push('ellipsis-end');

      // Always show last page
      pages.push(totalPages - 1);
    }

    return pages;
  };

  const pageNumbers = getPageNumbers();

  return (
    <div className="pagination-container" id="pagination-controls">
      {/* Info text */}
      <div className="pagination-info">
        {totalElements > 0 ? (
          <span>
            Showing <strong>{startItem}</strong>–<strong>{endItem}</strong> of{' '}
            <strong>{totalElements}</strong> items
          </span>
        ) : (
          <span>No items found</span>
        )}
        {isFetching && isPlaceholderData && (
          <span className="pagination-loading-dot" title="Loading next page…" />
        )}
      </div>

      {/* Navigation */}
      {totalPages > 1 && (
        <div className="pagination-nav">
          <button
            className="btn btn-sm btn-outline pagination-btn"
            disabled={page === 0}
            onClick={() => onPageChange(page - 1)}
            id="pagination-prev"
          >
            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2.5">
              <path d="M15 18l-6-6 6-6" />
            </svg>
            Prev
          </button>

          <div className="pagination-pages">
            {pageNumbers.map((p, idx) =>
              typeof p === 'string' ? (
                <span key={p} className="pagination-ellipsis">
                  …
                </span>
              ) : (
                <button
                  key={p}
                  className={`pagination-page-btn ${p === page ? 'active' : ''}`}
                  onClick={() => onPageChange(p)}
                  id={`pagination-page-${p}`}
                >
                  {p + 1}
                </button>
              )
            )}
          </div>

          <button
            className="btn btn-sm btn-outline pagination-btn"
            disabled={page >= totalPages - 1}
            onClick={() => onPageChange(page + 1)}
            id="pagination-next"
          >
            Next
            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2.5">
              <path d="M9 18l6-6-6-6" />
            </svg>
          </button>
        </div>
      )}
    </div>
  );
}
