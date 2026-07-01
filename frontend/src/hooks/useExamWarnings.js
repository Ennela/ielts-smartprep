import { useEffect, useRef } from 'react';

/**
 * Generates a Web Audio API chime sound (no external asset required).
 * @param {number} freq - Frequency in Hz
 * @param {number} duration - Duration in seconds
 */
export function playAlertSound(freq = 440, duration = 0.5) {
  try {
    const audioCtx = new (window.AudioContext || window.webkitAudioContext)();
    const oscillator = audioCtx.createOscillator();
    const gainNode = audioCtx.createGain();

    oscillator.type = 'sine';
    oscillator.frequency.value = freq;
    gainNode.gain.setValueAtTime(0.15, audioCtx.currentTime);
    gainNode.gain.exponentialRampToValueAtTime(0.001, audioCtx.currentTime + duration);

    oscillator.connect(gainNode);
    gainNode.connect(audioCtx.destination);

    oscillator.start();
    oscillator.stop(audioCtx.currentTime + duration);
  } catch (e) {
    console.error('[playAlertSound] Audio error:', e);
  }
}

/**
 * Reusable hook for 5-minute and 1-minute exam warnings.
 * Fires audio chimes and optional toast notifications at threshold crossings.
 *
 * @param {Object} options
 * @param {number|null} options.timeLeft - Current remaining seconds from useExamTimer
 * @param {boolean} options.enabled - Whether warnings should be active
 * @param {Function} [options.showWarning] - Optional toast/notification callback (e.g., from useToast)
 */
export default function useExamWarnings({ timeLeft, enabled = true, showWarning }) {
  const warningPlayedRef = useRef({ fiveMin: false, oneMin: false });

  // Reset when timer restarts (e.g., new exam)
  useEffect(() => {
    if (!enabled) {
      warningPlayedRef.current = { fiveMin: false, oneMin: false };
    }
  }, [enabled]);

  useEffect(() => {
    if (!enabled || timeLeft == null) return;

    // 5-minute warning (fire once when timeLeft crosses into [295, 300])
    if (timeLeft <= 300 && timeLeft > 295 && !warningPlayedRef.current.fiveMin) {
      warningPlayedRef.current.fiveMin = true;
      showWarning?.('5 minutes remaining! Please review and finalize your answers.');
      playAlertSound(523.25, 0.15); // C5 chime
      setTimeout(() => playAlertSound(659.25, 0.3), 150); // E5 chime
    }

    // 1-minute warning (fire once when timeLeft crosses into [55, 60])
    if (timeLeft <= 60 && timeLeft > 55 && !warningPlayedRef.current.oneMin) {
      warningPlayedRef.current.oneMin = true;
      showWarning?.('1 minute remaining! Your exam will be submitted automatically.');
      playAlertSound(880, 0.12);
      setTimeout(() => playAlertSound(880, 0.12), 150);
      setTimeout(() => playAlertSound(880, 0.25), 300);
    }
  }, [timeLeft, enabled, showWarning]);
}
