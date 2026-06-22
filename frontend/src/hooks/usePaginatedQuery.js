import { useQuery, keepPreviousData } from '@tanstack/react-query';
import { useState, useCallback } from 'react';

/**
 * Custom hook for paginated queries using TanStack Query.
 *
 * @param {object} options
 * @param {string|string[]} options.queryKey - Base query key
 * @param {function} options.queryFn - Async function receiving (page, size) and returning axios response
 * @param {object} [options.filters={}] - Filter state that should invalidate the query
 * @param {number} [options.defaultSize=20] - Default page size
 * @param {boolean} [options.enabled=true] - Whether to enable the query
 * @param {number} [options.refetchInterval] - Optional polling interval in ms
 *
 * @returns {object} Pagination state and query result
 */
export function usePaginatedQuery({
  queryKey,
  queryFn,
  filters = {},
  defaultSize = 20,
  enabled = true,
  refetchInterval,
}) {
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(defaultSize);

  // Build a stable query key from the base key + filters + pagination
  const fullQueryKey = [
    ...(Array.isArray(queryKey) ? queryKey : [queryKey]),
    { ...filters, page, size },
  ];

  const query = useQuery({
    queryKey: fullQueryKey,
    queryFn: () => queryFn(page, size),
    placeholderData: keepPreviousData,
    enabled,
    refetchInterval,
    select: (res) => res.data?.data, // Extract Page<T> from ApiResponse wrapper
  });

  // Derived state from Spring Data Page response
  const pageData = query.data;
  const content = pageData?.content || [];
  const totalPages = pageData?.totalPages || 0;
  const totalElements = pageData?.totalElements || 0;

  const goToPage = useCallback((newPage) => {
    setPage(Math.max(0, Math.min(newPage, totalPages - 1)));
  }, [totalPages]);

  const nextPage = useCallback(() => {
    setPage((p) => Math.min(p + 1, Math.max(0, totalPages - 1)));
  }, [totalPages]);

  const prevPage = useCallback(() => {
    setPage((p) => Math.max(0, p - 1));
  }, []);

  const resetPage = useCallback(() => {
    setPage(0);
  }, []);

  return {
    // Data
    content,
    totalPages,
    totalElements,
    pageData,

    // Pagination state
    page,
    size,
    setPage: goToPage,
    setSize,
    nextPage,
    prevPage,
    resetPage,

    // Query state
    isLoading: query.isLoading,
    isFetching: query.isFetching,
    isPlaceholderData: query.isPlaceholderData,
    isError: query.isError,
    error: query.error,
    refetch: query.refetch,
  };
}
