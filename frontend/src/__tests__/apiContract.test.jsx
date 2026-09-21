import '@testing-library/jest-dom';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, act } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { MockTestProvider } from '../context/MockTestContext';
import MockTestSessionPage from '../pages/MockTestSessionPage';
import MockTestResultPage from '../pages/MockTestResultPage';
import WritingFullResultPage from '../pages/WritingFullResultPage';

/*
 * Three places where the client ignored an endpoint the backend built for it:
 *  - /mock-tests/take/:sessionId loaded sessions/current and threw the id away, so a
 *    session the server had retired (EXPIRED) never reached the page;
 *  - the result page polled the full report every 5 s instead of the status endpoint;
 *  - /writing/full-result depended on navigation state, so a new tab or bookmark was a
 *    dead end although GET /writing/full-submissions/{id} exists.
 */

vi.mock('../api/mockTestApi', () => ({
  default: {
    getSession: vi.fn(),
    getCurrentSession: vi.fn(),
    getSubmission: vi.fn(),
    getGradingStatus: vi.fn(),
    getAnalytics: vi.fn(),
    saveProgress: vi.fn(),
  },
}));
vi.mock('../api/writingApi', () => ({
  default: { getFullSubmission: vi.fn() },
}));
vi.mock('../context/ToastContext', () => ({
  useToast: () => ({ success: () => {}, error: () => {}, info: () => {}, warning: () => {} }),
}));

import mockTestApi from '../api/mockTestApi';
import writingApi from '../api/writingApi';

const ok = (data) => ({ data: { success: true, data } });

describe('MockTestSessionPage loads the session named in the route', () => {
  beforeEach(() => vi.clearAllMocks());

  it('tells the candidate when that session has expired instead of opening another one', async () => {
    mockTestApi.getSession.mockResolvedValue(ok({ sessionId: 9, status: 'EXPIRED', currentSection: 'READING', timeRemainingSeconds: 0 }));

    render(
      <MemoryRouter initialEntries={['/mock-tests/take/9']}>
        <Routes>
          <Route path="/mock-tests" element={<div>Lobby</div>} />
          <Route path="/mock-tests/take/:sessionId" element={<MockTestProvider><MockTestSessionPage /></MockTestProvider>} />
        </Routes>
      </MemoryRouter>
    );

    expect(await screen.findByText(/ran out, so it can no longer be continued/)).toBeInTheDocument();
    expect(mockTestApi.getSession).toHaveBeenCalledWith('9');
    expect(mockTestApi.getCurrentSession).not.toHaveBeenCalled();
    expect(screen.queryByText('Lobby')).not.toBeInTheDocument();
  });
});

describe('MockTestResultPage grading poll', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.useFakeTimers({ shouldAdvanceTime: true });
  });
  afterEach(() => vi.useRealTimers());

  it('polls the status endpoint and fetches the report once grading has moved on', async () => {
    mockTestApi.getSubmission
      .mockResolvedValueOnce(ok({ submissionId: 3, status: 'GRADING' }))
      .mockResolvedValueOnce(ok({ submissionId: 3, status: 'FAILED' }));
    mockTestApi.getGradingStatus
      .mockResolvedValueOnce(ok({ status: 'GRADING', overallBand: 0 }))
      .mockResolvedValueOnce(ok({ status: 'FAILED', overallBand: 0 }));

    render(
      <MemoryRouter initialEntries={['/mock-tests/result/3']}>
        <Routes>
          <Route path="/mock-tests/result/:submissionId" element={<MockTestResultPage />} />
        </Routes>
      </MemoryRouter>
    );

    expect(await screen.findByText('Evaluating Mock Test')).toBeInTheDocument();

    await act(async () => { await vi.advanceTimersByTimeAsync(10_500); });

    expect(await screen.findByText('Grading Interrupted')).toBeInTheDocument();
    expect(mockTestApi.getGradingStatus).toHaveBeenCalledTimes(2);
    // Once on mount, once after the status changed — not on every tick.
    expect(mockTestApi.getSubmission).toHaveBeenCalledTimes(2);
  });
});

describe('WritingFullResultPage by id', () => {
  beforeEach(() => vi.clearAllMocks());

  const sitting = {
    id: 12,
    overallWritingBand: 6.5,
    task1Result: { essayType: 'LINE_GRAPH', overallBand: 6, taskAchievementScore: 6, coherenceScore: 6, lexicalScore: 6, grammarScore: 6, feedback: 'ok', promptText: 'Describe the graph', essayText: 'e1' },
    task2Result: { essayType: 'OPINION', overallBand: 7, taskAchievementScore: 7, coherenceScore: 7, lexicalScore: 7, grammarScore: 7, feedback: 'ok', promptText: 'Discuss', essayText: 'e2' },
  };

  function renderAt(path) {
    return render(
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/writing/full-result/:id?" element={<WritingFullResultPage />} />
        </Routes>
      </MemoryRouter>
    );
  }

  it('fetches the sitting when opened without navigation state', async () => {
    writingApi.getFullSubmission.mockResolvedValue(ok(sitting));

    renderAt('/writing/full-result/12');

    expect(await screen.findByText('Describe the graph')).toBeInTheDocument();
    expect(writingApi.getFullSubmission).toHaveBeenCalledWith('12');
  });

  it('explains a missing sitting rather than pretending there was never one', async () => {
    writingApi.getFullSubmission.mockRejectedValue(Object.assign(new Error('Not found'), { status: 404 }));

    renderAt('/writing/full-result/99');

    expect(await screen.findByText('Not found')).toBeInTheDocument();
  });

  it('keeps the old message when there is neither state nor an id', async () => {
    renderAt('/writing/full-result');
    expect(await screen.findByText('No exam result found. Please start a new session.')).toBeInTheDocument();
    expect(writingApi.getFullSubmission).not.toHaveBeenCalled();
  });
});
