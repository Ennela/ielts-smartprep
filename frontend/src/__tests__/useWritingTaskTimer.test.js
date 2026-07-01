import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import useWritingTaskTimer from '../hooks/useWritingTaskTimer';

describe('useWritingTaskTimer', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('should initialize with default values', () => {
    const { result } = renderHook(() =>
      useWritingTaskTimer({
        activeTask: 1,
        suggestedTask1: 1200,
        suggestedTask2: 2400,
        enabled: true,
      })
    );

    expect(result.current.task1Elapsed).toBe(0);
    expect(result.current.task2Elapsed).toBe(0);
    expect(result.current.isTask1OverSuggested).toBe(false);
    expect(result.current.isTask2OverSuggested).toBe(false);
  });

  it('should accumulate time for task 1 when active', () => {
    const { result } = renderHook(() =>
      useWritingTaskTimer({
        activeTask: 1,
        suggestedTask1: 1200,
        suggestedTask2: 2400,
        enabled: true,
      })
    );

    act(() => {
      vi.advanceTimersByTime(5000); // 5 seconds
    });

    expect(result.current.task1Elapsed).toBeGreaterThanOrEqual(4);
    expect(result.current.task2Elapsed).toBe(0);
  });

  it('should switch accumulation when active task changes', () => {
    let activeTask = 1;
    const { result, rerender } = renderHook(() =>
      useWritingTaskTimer({
        activeTask,
        suggestedTask1: 1200,
        suggestedTask2: 2400,
        enabled: true,
      })
    );

    // Run task 1 for 3 seconds
    act(() => {
      vi.advanceTimersByTime(3000);
    });

    const task1Time = result.current.task1Elapsed;
    expect(task1Time).toBeGreaterThanOrEqual(2);

    // Switch to task 2
    activeTask = 2;
    rerender();

    act(() => {
      vi.advanceTimersByTime(5000);
    });

    expect(result.current.task2Elapsed).toBeGreaterThanOrEqual(4);
    // Task 1 should stay frozen
    expect(result.current.task1Elapsed).toBe(task1Time);
  });

  it('should set isTask1OverSuggested when elapsed exceeds suggestion', () => {
    const { result } = renderHook(() =>
      useWritingTaskTimer({
        activeTask: 1,
        suggestedTask1: 3, // 3 seconds for fast testing
        suggestedTask2: 2400,
        enabled: true,
      })
    );

    act(() => {
      vi.advanceTimersByTime(4000); // 4 seconds > 3 second suggestion
    });

    expect(result.current.isTask1OverSuggested).toBe(true);
  });

  it('should not accumulate when disabled', () => {
    const { result } = renderHook(() =>
      useWritingTaskTimer({
        activeTask: 1,
        suggestedTask1: 1200,
        suggestedTask2: 2400,
        enabled: false,
      })
    );

    act(() => {
      vi.advanceTimersByTime(5000);
    });

    expect(result.current.task1Elapsed).toBe(0);
    expect(result.current.task2Elapsed).toBe(0);
  });

  it('should return correct suggested remaining times', () => {
    const { result } = renderHook(() =>
      useWritingTaskTimer({
        activeTask: 1,
        suggestedTask1: 1200,
        suggestedTask2: 2400,
        enabled: true,
      })
    );

    act(() => {
      vi.advanceTimersByTime(5000); // 5 seconds on task 1
    });

    // Suggested remaining for task 1 should be less than 1200
    const remaining = result.current.suggestedTask1Remaining;
    expect(remaining).toBeLessThan(1200);
    expect(remaining).toBeGreaterThan(1190);

    // Task 2 hasn't started, so remaining should be full 2400
    expect(result.current.suggestedTask2Remaining).toBe(2400);
  });

  it('should return final times via getFinalTimes()', () => {
    const { result } = renderHook(() =>
      useWritingTaskTimer({
        activeTask: 1,
        suggestedTask1: 1200,
        suggestedTask2: 2400,
        enabled: true,
      })
    );

    act(() => {
      vi.advanceTimersByTime(3000); // 3 seconds on task 1
    });

    const finalTimes = result.current.getFinalTimes();
    expect(finalTimes.task1).toBeGreaterThanOrEqual(2);
    expect(finalTimes.task2).toBe(0);
  });

  it('should provide live time spent values', () => {
    const { result } = renderHook(() =>
      useWritingTaskTimer({
        activeTask: 1,
        suggestedTask1: 1200,
        suggestedTask2: 2400,
        enabled: true,
      })
    );

    act(() => {
      vi.advanceTimersByTime(10000); // 10 seconds
    });

    const live = result.current.getLiveTimeSpent();
    expect(live.task1).toBeGreaterThanOrEqual(9);
    expect(live.task2).toBe(0);
  });
});
