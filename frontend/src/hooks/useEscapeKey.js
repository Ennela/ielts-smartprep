import { useEffect, useRef } from 'react';

/**
 * Calls onEscape when Escape is pressed while `active` is true.
 *
 * The latest callback is kept in a ref, so pages can pass an inline handler
 * without re-subscribing the listener on every render.
 */
export default function useEscapeKey(active, onEscape) {
  const callbackRef = useRef(onEscape);

  useEffect(() => {
    callbackRef.current = onEscape;
  });

  useEffect(() => {
    if (!active) return undefined;
    const onKeyDown = (event) => {
      if (event.key === 'Escape') callbackRef.current();
    };
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [active]);
}
