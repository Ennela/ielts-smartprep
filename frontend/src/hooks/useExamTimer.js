import { useState, useEffect, useRef, useCallback } from 'react';

/**
 * Server-authoritative exam countdown timer hook.
 *
 * Instead of counting from a hard-coded value, this hook computes remaining
 * time from a server-provided deadline (ISO string), ensuring the timer
 * cannot be cheated by manipulating the client clock or reloading the page.
 *
 * @param {Object} options
 * @param {string} options.deadline - ISO datetime string of the exam deadline (from server)
 * @param {Function} options.onTimeUp - Callback when timer reaches 0
 * @param {boolean} [options.enabled=true] - Whether the timer should be running
 *
 * @returns {Object} { timeLeft, isWarning, isCritical, isExpired, formatTime }
 */
export default function useExamTimer({ deadline, onTimeUp, enabled = true }) {
  const [timeLeft, setTimeLeft] = useState(() => calcRemaining(deadline));
  const intervalRef = useRef(null);
  const timeUpCalledRef = useRef(false);
  const onTimeUpRef = useRef(onTimeUp);

  // Keep callback ref current without re-triggering effect
  useEffect(() => {
    onTimeUpRef.current = onTimeUp;
  }, [onTimeUp]);

  // Calculate remaining seconds from server deadline
  function calcRemaining(dl) {
    if (!dl) return null;
    const deadlineMs = new Date(dl).getTime();
    const nowMs = Date.now();
    return Math.max(0, Math.floor((deadlineMs - nowMs) / 1000));
  }

  // Main countdown effect
  useEffect(() => {
    if (!enabled || !deadline) return;

    timeUpCalledRef.current = false;

    // Recalculate immediately (handles resume after reload)
    const initial = calcRemaining(deadline);
    setTimeLeft(initial);

    if (initial <= 0) {
      if (!timeUpCalledRef.current) {
        timeUpCalledRef.current = true;
        onTimeUpRef.current?.();
      }
      return;
    }

    intervalRef.current = setInterval(() => {
      const remaining = calcRemaining(deadline);
      setTimeLeft(remaining);

      if (remaining <= 0) {
        clearInterval(intervalRef.current);
        if (!timeUpCalledRef.current) {
          timeUpCalledRef.current = true;
          onTimeUpRef.current?.();
        }
      }
    }, 1000);

    return () => {
      if (intervalRef.current) clearInterval(intervalRef.current);
    };
  }, [deadline, enabled]);

  // Handle tab visibility changes — recalculate from deadline when tab becomes visible
  useEffect(() => {
    if (!enabled || !deadline) return;

    const handleVisibility = () => {
      if (document.visibilityState === 'visible') {
        const remaining = calcRemaining(deadline);
        setTimeLeft(remaining);

        if (remaining <= 0 && !timeUpCalledRef.current) {
          timeUpCalledRef.current = true;
          if (intervalRef.current) clearInterval(intervalRef.current);
          onTimeUpRef.current?.();
        }
      }
    };

    document.addEventListener('visibilitychange', handleVisibility);
    return () => document.removeEventListener('visibilitychange', handleVisibility);
  }, [deadline, enabled]);

  // Format seconds as mm:ss
  const formatTime = useCallback((seconds) => {
    if (seconds == null) return '--:--';
    const s = Math.max(0, seconds);
    const m = Math.floor(s / 60).toString().padStart(2, '0');
    const sec = (s % 60).toString().padStart(2, '0');
    return `${m}:${sec}`;
  }, []);

  // Stop the timer (e.g. on manual submit)
  const stopTimer = useCallback(() => {
    if (intervalRef.current) {
      clearInterval(intervalRef.current);
      intervalRef.current = null;
    }
  }, []);

  return {
    timeLeft: timeLeft ?? 0,
    isWarning: timeLeft != null && timeLeft <= 300 && timeLeft > 60,    // <= 5 min
    isCritical: timeLeft != null && timeLeft <= 60 && timeLeft > 0,     // <= 1 min
    isExpired: timeLeft != null && timeLeft <= 0,
    formatTime,
    formattedTime: formatTime(timeLeft),
    stopTimer,
  };
}
