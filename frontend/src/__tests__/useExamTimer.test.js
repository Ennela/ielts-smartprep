import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import useExamTimer from '../hooks/useExamTimer';

describe('useExamTimer', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('should calculate remaining seconds from server deadline', () => {
    // Deadline is 120 seconds from now
    const now = Date.now();
    vi.setSystemTime(now);
    const deadline = new Date(now + 120_000).toISOString();

    const { result } = renderHook(() =>
      useExamTimer({ deadline, onTimeUp: vi.fn() })
    );

    // Should be approximately 120 seconds (±1 for rounding)
    expect(result.current.timeLeft).toBeGreaterThanOrEqual(119);
    expect(result.current.timeLeft).toBeLessThanOrEqual(120);
  });

  it('should format time as mm:ss', () => {
    const now = Date.now();
    vi.setSystemTime(now);
    const deadline = new Date(now + 125_000).toISOString(); // 2:05

    const { result } = renderHook(() =>
      useExamTimer({ deadline, onTimeUp: vi.fn() })
    );

    expect(result.current.formatTime(125)).toBe('02:05');
    expect(result.current.formatTime(0)).toBe('00:00');
    expect(result.current.formatTime(3661)).toBe('61:01');
    expect(result.current.formatTime(null)).toBe('--:--');
  });

  it('should set isWarning when timeLeft <= 300s and > 60s', () => {
    const now = Date.now();
    vi.setSystemTime(now);
    const deadline = new Date(now + 200_000).toISOString(); // 200s

    const { result } = renderHook(() =>
      useExamTimer({ deadline, onTimeUp: vi.fn() })
    );

    expect(result.current.isWarning).toBe(true);
    expect(result.current.isCritical).toBe(false);
  });

  it('should set isCritical when timeLeft <= 60s and > 0', () => {
    const now = Date.now();
    vi.setSystemTime(now);
    const deadline = new Date(now + 30_000).toISOString(); // 30s

    const { result } = renderHook(() =>
      useExamTimer({ deadline, onTimeUp: vi.fn() })
    );

    expect(result.current.isCritical).toBe(true);
    expect(result.current.isWarning).toBe(false);
  });

  it('should call onTimeUp when countdown reaches 0', () => {
    const now = Date.now();
    vi.setSystemTime(now);
    const onTimeUp = vi.fn();
    const deadline = new Date(now + 2_000).toISOString(); // 2s

    renderHook(() =>
      useExamTimer({ deadline, onTimeUp })
    );

    // Advance time past deadline
    act(() => {
      vi.setSystemTime(now + 3_000);
      vi.advanceTimersByTime(1_000);
    });

    expect(onTimeUp).toHaveBeenCalledTimes(1);
  });

  it('should not call onTimeUp more than once', () => {
    const now = Date.now();
    vi.setSystemTime(now);
    const onTimeUp = vi.fn();
    const deadline = new Date(now + 1_000).toISOString();

    renderHook(() =>
      useExamTimer({ deadline, onTimeUp })
    );

    // Advance well past deadline
    act(() => {
      vi.setSystemTime(now + 5_000);
      vi.advanceTimersByTime(3_000);
    });

    expect(onTimeUp).toHaveBeenCalledTimes(1);
  });

  it('should set isExpired when time is up', () => {
    const now = Date.now();
    vi.setSystemTime(now);
    const deadline = new Date(now + 1_000).toISOString();

    const { result } = renderHook(() =>
      useExamTimer({ deadline, onTimeUp: vi.fn() })
    );

    act(() => {
      vi.setSystemTime(now + 2_000);
      vi.advanceTimersByTime(2_000);
    });

    expect(result.current.isExpired).toBe(true);
    expect(result.current.timeLeft).toBe(0);
  });

  it('should not run when enabled=false', () => {
    const now = Date.now();
    vi.setSystemTime(now);
    const onTimeUp = vi.fn();
    const deadline = new Date(now + 1_000).toISOString();

    const { result: _result } = renderHook(() =>
      useExamTimer({ deadline, onTimeUp, enabled: false })
    );

    act(() => {
      vi.setSystemTime(now + 5_000);
      vi.advanceTimersByTime(5_000);
    });

    // onTimeUp should NOT be called when disabled
    expect(onTimeUp).not.toHaveBeenCalled();
  });

  it('should return 0 when deadline is null', () => {
    const { result } = renderHook(() =>
      useExamTimer({ deadline: null, onTimeUp: vi.fn() })
    );

    expect(result.current.timeLeft).toBe(0);
    expect(result.current.formattedTime).toBe('--:--');
  });

  it('should stop the timer when stopTimer is called', () => {
    const now = Date.now();
    vi.setSystemTime(now);
    const onTimeUp = vi.fn();
    const deadline = new Date(now + 5_000).toISOString();

    const { result } = renderHook(() =>
      useExamTimer({ deadline, onTimeUp })
    );

    // Stop the timer
    act(() => {
      result.current.stopTimer();
    });

    // Advance past deadline — onTimeUp should NOT fire because timer was stopped
    act(() => {
      vi.setSystemTime(now + 10_000);
      vi.advanceTimersByTime(10_000);
    });

    expect(onTimeUp).not.toHaveBeenCalled();
  });

  it('should handle already-expired deadline', () => {
    const now = Date.now();
    vi.setSystemTime(now);
    const onTimeUp = vi.fn();
    // Deadline was 10 seconds ago
    const deadline = new Date(now - 10_000).toISOString();

    renderHook(() =>
      useExamTimer({ deadline, onTimeUp })
    );

    expect(onTimeUp).toHaveBeenCalledTimes(1);
  });
});
