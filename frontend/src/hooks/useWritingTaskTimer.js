import { useState, useEffect, useRef, useCallback } from 'react';

/**
 * Per-task time tracking hook for Writing exam.
 * Accumulates actual seconds spent on each task based on which tab is active.
 * Uses Date.now() diffs for accuracy instead of interval counting.
 *
 * @param {Object} options
 * @param {number} options.activeTask - Currently active task (1 or 2)
 * @param {boolean} [options.enabled=true] - Whether tracking should be active
 * @param {number} [options.suggestedTask1=1200] - Suggested seconds for Task 1 (20 min)
 * @param {number} [options.suggestedTask2=2400] - Suggested seconds for Task 2 (40 min)
 *
 * @returns {Object} Per-task time tracking state
 */
export default function useWritingTaskTimer({
  activeTask,
  enabled = true,
  suggestedTask1 = 1200,
  suggestedTask2 = 2400,
}) {
  const [task1TimeSpent, setTask1TimeSpent] = useState(0);
  const [task2TimeSpent, setTask2TimeSpent] = useState(0);
  const [, setTick] = useState(0);

  // Track when the current task tab became active
  const taskStartRef = useRef(Date.now());
  const prevTaskRef = useRef(activeTask);

  // Accumulate time when switching tasks
  useEffect(() => {
    if (!enabled) return;

    const now = Date.now();
    const elapsed = Math.floor((now - taskStartRef.current) / 1000);

    if (prevTaskRef.current === 1) {
      setTask1TimeSpent(prev => prev + elapsed);
    } else if (prevTaskRef.current === 2) {
      setTask2TimeSpent(prev => prev + elapsed);
    }

    taskStartRef.current = now;
    prevTaskRef.current = activeTask;
  }, [activeTask, enabled]);

  // Periodic update (every second) for live display — forces re-render so getLiveTimeSpent recalculates
  useEffect(() => {
    if (!enabled) return;

    const interval = setInterval(() => {
      // Force a re-render so Date.now()-based live values refresh.
      setTick(prev => prev + 1);
    }, 1000);

    return () => clearInterval(interval);
  }, [enabled]);

  // Get live time spent (base + current session)
  const getLiveTimeSpent = useCallback((task) => {
    const elapsed = Math.floor((Date.now() - taskStartRef.current) / 1000);
    const liveTask1 = task1TimeSpent + (activeTask === 1 ? elapsed : 0);
    const liveTask2 = task2TimeSpent + (activeTask === 2 ? elapsed : 0);

    if (task === 1) return liveTask1;
    if (task === 2) return liveTask2;
    return {
      task1: liveTask1,
      task2: liveTask2,
      timeSpentTask1: liveTask1,
      timeSpentTask2: liveTask2,
    };
  }, [task1TimeSpent, task2TimeSpent, activeTask]);

  // Get final accumulated times (call before submit)
  const getFinalTimes = useCallback(() => {
    const now = Date.now();
    const elapsed = Math.floor((now - taskStartRef.current) / 1000);

    let t1 = task1TimeSpent;
    let t2 = task2TimeSpent;

    if (activeTask === 1) t1 += elapsed;
    else if (activeTask === 2) t2 += elapsed;

    return { timeSpentTask1: t1, timeSpentTask2: t2, task1: t1, task2: t2 };
  }, [task1TimeSpent, task2TimeSpent, activeTask]);

  // Calculate suggestion remaining
  const liveT1 = getLiveTimeSpent(1);
  const liveT2 = getLiveTimeSpent(2);

  return {
    task1TimeSpent: liveT1,
    task2TimeSpent: liveT2,
    task1Elapsed: liveT1,
    task2Elapsed: liveT2,
    task1SuggestionRemaining: Math.max(0, suggestedTask1 - liveT1),
    task2SuggestionRemaining: Math.max(0, suggestedTask2 - liveT2),
    suggestedTask1Remaining: Math.max(0, suggestedTask1 - liveT1),
    suggestedTask2Remaining: Math.max(0, suggestedTask2 - liveT2),
    isTask1OverSuggested: liveT1 > suggestedTask1,
    isTask2OverSuggested: liveT2 > suggestedTask2,
    getLiveTimeSpent,
    getFinalTimes,
  };
}
