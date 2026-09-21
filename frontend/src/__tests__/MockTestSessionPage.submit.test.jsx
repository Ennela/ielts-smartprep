import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { lazy, Suspense } from 'react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { MockTestProvider } from '../context/MockTestContext';
import MockTestSessionPage from '../pages/MockTestSessionPage';

/*
 * "Complete & Submit Exam" landed on the lobby instead of the result page. submitExam()
 * clears activeSession and sets latestSubmissionId; the redirect effect then reset
 * latestSubmissionId, which re-ran the load-on-mount effect with neither a session nor
 * a submission id: GET /sessions/current → 404 → navigate('/mock-tests'), which arrived
 * before the code-split result page had finished loading and replaced it.
 */

vi.mock('../api/mockTestApi', () => ({
  default: {
    getSession: vi.fn(),
    getCurrentSession: vi.fn(),
    saveProgress: vi.fn(),
    nextSection: vi.fn(),
    submitExam: vi.fn(),
  },
}));
vi.mock('../context/ToastContext', () => ({
  useToast: () => ({ success: () => {}, error: () => {}, info: () => {}, warning: () => {} }),
}));

import mockTestApi from '../api/mockTestApi';

const ok = (data) => ({ data: { success: true, data } });

const session = {
  sessionId: 4,
  status: 'IN_PROGRESS',
  currentSection: 'WRITING',
  timeRemainingSeconds: 3000,
  progressJson: null,
  listeningParts: [],
  readingQuizzes: [],
  writingPrompts: [
    { promptId: 1, essayType: 'LINE_GRAPH', promptText: 'Describe the graph.' },
    { promptId: 2, essayType: 'OPINION', promptText: 'Discuss both views.' },
  ],
};

// The real result page is code-split; make this one load slower than the 404 above.
const ResultPage = lazy(() => new Promise((resolve) => {
  setTimeout(() => resolve({ default: () => <div>Result page</div> }), 30);
}));

describe('MockTestSessionPage submit redirect', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    mockTestApi.getSession
      .mockResolvedValueOnce(ok(session))
      .mockRejectedValue({ response: { status: 404 } });
    mockTestApi.getCurrentSession.mockRejectedValue({ response: { status: 404 } });
    mockTestApi.saveProgress.mockResolvedValue(ok({}));
    mockTestApi.submitExam.mockResolvedValue(ok({ submissionId: 8, status: 'GRADING' }));
  });

  it('goes to the result page, not the lobby, after the exam is submitted', async () => {
    render(
      <MemoryRouter initialEntries={['/mock-tests/take/4']}>
        <Suspense fallback={null}>
          <Routes>
            <Route path="/mock-tests" element={<div>Lobby</div>} />
            <Route path="/mock-tests/result/:submissionId" element={<ResultPage />} />
            <Route path="/mock-tests/take/:sessionId" element={
              <MockTestProvider><MockTestSessionPage /></MockTestProvider>
            } />
          </Routes>
        </Suspense>
      </MemoryRouter>
    );

    fireEvent.click(await screen.findByText('Complete & Submit Exam'));

    expect(await screen.findByText('Result page')).toBeInTheDocument();
    expect(screen.queryByText('Lobby')).not.toBeInTheDocument();
    expect(mockTestApi.submitExam).toHaveBeenCalledTimes(1);
    // The session was loaded once on mount (by the id in the route) and never
    // re-requested after the submit.
    expect(mockTestApi.getSession).toHaveBeenCalledTimes(1);
    expect(mockTestApi.getCurrentSession).not.toHaveBeenCalled();
  });
});
