import { useState, useEffect } from 'react';

/**
 * Seconds elapsed since `running` last became true; 0 while it is false.
 * Used to show how long a synchronous AI grade has been in flight.
 */
export default function useElapsedSeconds(running) {
  const [elapsed, setElapsed] = useState(0);

  useEffect(() => {
    if (!running) {
      setElapsed(0);
      return;
    }
    const startedAt = Date.now();
    const id = setInterval(() => setElapsed(Math.floor((Date.now() - startedAt) / 1000)), 1000);
    return () => clearInterval(id);
  }, [running]);

  return elapsed;
}

export const formatElapsed = (seconds) =>
  `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;
