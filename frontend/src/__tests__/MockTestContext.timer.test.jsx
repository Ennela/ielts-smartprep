import '@testing-library/jest-dom';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, act } from '@testing-library/react';
import { useEffect } from 'react';
import { MockTestProvider, useMockTest } from '../context/MockTestContext';

/*
 * The section clock that decides whether an autosave or a section advance is late lives
 * on the server. The client countdown must follow it: re-anchor on every server response,
 * catch up after the tab was hidden (timers throttled) and never trust the localStorage
 * backup's copy of the clock.
 */

vi.mock('../api/mockTestApi', () => ({
  default: {
    getCurrentSession: vi.fn(),
    saveProgress: vi.fn(),
    nextSection: vi.fn(),
    submitExam: vi.fn(),
  },
}));

import mockTestApi from '../api/mockTestApi';

const ok = (data) => ({ data: { success: true, data } });

const session = (overrides = {}) => ({
  sessionId: 4,
  status: 'IN_PROGRESS',
  currentSection: 'LISTENING',
  timeRemainingSeconds: 3000,
  progressJson: JSON.stringify({ q1: 'server' }),
  startedAt: '2026-09-20T10:00:00',
  lastSyncedAt: '2026-09-20T10:00:00',
  ...overrides,
});

function Probe() {
  const { loadActiveSession, timeRemaining, answers } = useMockTest();
  useEffect(() => { loadActiveSession(); }, []);
  return (
    <div>
      <span data-testid="remaining">{timeRemaining}</span>
      <span data-testid="answers">{JSON.stringify(answers)}</span>
    </div>
  );
}

const remaining = () => Number(screen.getByTestId('remaining').textContent);

describe('MockTestContext section timer', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-09-20T10:05:00'));
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  async function mount(initial = session()) {
    mockTestApi.getCurrentSession.mockResolvedValue(ok(initial));
    render(<MockTestProvider><Probe /></MockTestProvider>);
    await act(async () => { await Promise.resolve(); });
    expect(remaining()).toBe(initial.timeRemainingSeconds);
  }

  it('re-anchors on the timeRemainingSeconds the autosave response carries', async () => {
    await mount();
    // The server has been counting a slower clock than ours: it says 100 s left.
    mockTestApi.saveProgress.mockResolvedValue(ok(session({ timeRemainingSeconds: 100 })));

    await act(async () => { await vi.advanceTimersByTimeAsync(30_000); });

    expect(mockTestApi.saveProgress).toHaveBeenCalledTimes(1);
    expect(remaining()).toBe(100);
    await act(async () => { await vi.advanceTimersByTimeAsync(10_000); });
    expect(remaining()).toBe(90);
  });

  it('catches up with the wall clock when the tab becomes visible again', async () => {
    await mount();
    mockTestApi.getCurrentSession.mockResolvedValue(ok(session({ timeRemainingSeconds: 2380 })));

    // Five minutes pass with no interval firing at all (a hidden tab), then we return.
    vi.setSystemTime(new Date('2026-09-20T10:10:00'));
    await act(async () => {
      document.dispatchEvent(new Event('visibilitychange'));
      await Promise.resolve();
    });

    expect(remaining()).toBe(2380);
    expect(mockTestApi.getCurrentSession).toHaveBeenCalledTimes(2);
  });

  it('advances the section as soon as the deadline has passed, even after a long hidden stretch', async () => {
    await mount();
    mockTestApi.getCurrentSession.mockRejectedValue({ response: { status: 404 } });
    mockTestApi.nextSection.mockResolvedValue(ok(session({ currentSection: 'READING', timeRemainingSeconds: 3600 })));

    vi.setSystemTime(new Date('2026-09-20T11:00:00'));
    await act(async () => {
      document.dispatchEvent(new Event('visibilitychange'));
      await Promise.resolve();
    });

    expect(mockTestApi.nextSection).toHaveBeenCalledTimes(1);
    expect(remaining()).toBe(3600);
  });

  it('restores answers from the local backup but never its clock', async () => {
    localStorage.setItem('mock_session_4', JSON.stringify({
      answers: { q2: 'local' },
      timeRemaining: 5,
      timestamp: new Date('2026-09-20T10:04:00').getTime(),
    }));

    await mount();

    expect(remaining()).toBe(3000);
    expect(JSON.parse(screen.getByTestId('answers').textContent)).toEqual({ q1: 'server', q2: 'local' });
  });
});
