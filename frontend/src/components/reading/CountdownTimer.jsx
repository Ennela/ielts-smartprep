import { useEffect, useRef, useState } from 'react';
import { useReading } from '../../context/ReadingContext';

export default function CountdownTimer({ onTimeUp }) {
  const { timeRemaining, tickTimer, isSubmitted } = useReading();
  const [visible, setVisible] = useState(true);
  const intervalRef = useRef(null);

  useEffect(() => {
    if (isSubmitted || timeRemaining <= 0) {
      if (intervalRef.current) clearInterval(intervalRef.current);
      return;
    }

    intervalRef.current = setInterval(() => {
      tickTimer();
    }, 1000);

    return () => {
      if (intervalRef.current) clearInterval(intervalRef.current);
    };
  }, [isSubmitted, tickTimer, timeRemaining]);

  // Auto-submit when time is up
  useEffect(() => {
    if (timeRemaining <= 0 && !isSubmitted && onTimeUp) {
      onTimeUp();
    }
  }, [timeRemaining, isSubmitted, onTimeUp]);

  const minutes = Math.floor(timeRemaining / 60);
  const seconds = timeRemaining % 60;
  const isWarning = timeRemaining <= 120 && timeRemaining > 0; // 2 minutes warning
  const isExpired = timeRemaining <= 0;

  return (
    <div className={`countdown-timer ${isWarning ? 'warning' : ''} ${isExpired ? 'expired' : ''}`} id="countdown-timer">
      <div className="timer-controls">
        <button
          type="button"
          className="timer-toggle"
          onClick={() => setVisible(!visible)}
          title={visible ? 'Hide timer' : 'Show timer'}
          id="timer-toggle-btn"
        >
          {visible ? 'Hide' : 'Show'} timer
        </button>
      </div>
      {visible && (
        <div className={`timer-display ${isWarning ? 'pulse' : ''}`}>
          <svg className="timer-icon" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2">
            <circle cx="12" cy="13" r="8" />
            <path d="M12 9v4l2 2" />
            <path d="M9 1h6" />
          </svg>
          <span className="timer-text">
            {String(minutes).padStart(2, '0')}:{String(seconds).padStart(2, '0')}
          </span>
        </div>
      )}
    </div>
  );
}
