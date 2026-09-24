import { useEffect, useState } from 'react';

/**
 * `value` after it has stopped changing for `delayMs`.
 *
 * Used for search boxes that drive a server query, so typing a word is one request
 * rather than one per letter.
 */
export default function useDebouncedValue(value, delayMs = 300) {
  const [debounced, setDebounced] = useState(value);

  useEffect(() => {
    const id = setTimeout(() => setDebounced(value), delayMs);
    return () => clearTimeout(id);
  }, [value, delayMs]);

  return debounced;
}
