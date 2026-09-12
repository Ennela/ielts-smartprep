import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import HistoryReviewPage from '../pages/HistoryReviewPage';
import DashboardPage from '../pages/DashboardPage';

/*
 * score_history rows written for a full mock test -- and the ones V48 backfilled for
 * sittings that predate V47 -- carry a mockTestSubmissionId. The backfilled ones have no
 * user_answers, so the answer review would be empty; both pages send the user to the
 * mock test report instead, where the full review lives.
 */

vi.mock('../api/statsApi', () => ({
  default: {
    getHistoryDetail: vi.fn(),
    explainAnswer: vi.fn(),
    getOverview: vi.fn(),
    getScoreTrend: vi.fn(),
    getHistory: vi.fn(),
  },
}));
vi.mock('../api/analyticsApi', () => ({ default: { getWeakness: vi.fn() } }));
vi.mock('../context/AuthContext', () => ({
  useAuth: () => ({ user: { userId: 1, username: 'student', displayName: 'Student' } }),
}));
// recharts needs a measured container; the chart is not what these tests look at.
vi.mock('../components/analytics/ScoreTrendChart', () => ({ default: () => <div data-testid="trend-chart" /> }));

import statsApi from '../api/statsApi';
import analyticsApi from '../api/analyticsApi';

const ok = (data) => ({ data: { success: true, data } });

function renderWithRoutes(initialPath, element) {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <Routes>
        <Route path="/history/:historyId/review" element={element} />
        <Route path="/dashboard" element={element} />
        <Route path="/mock-tests/result/:submissionId" element={<div>MOCK TEST REPORT PAGE</div>} />
      </Routes>
    </MemoryRouter>
  );
}

describe('HistoryReviewPage for a mock test row without answers', () => {
  beforeEach(() => vi.clearAllMocks());

  it('points at the mock test report instead of the generic "no answers" message', async () => {
    statsApi.getHistoryDetail.mockResolvedValue(ok({
      historyId: 42, skillType: 'LISTENING', score: 7.0, recordedAt: '2026-08-01T09:30:00',
      totalQuestions: 0, correctCount: 0, answers: [], mockTestSubmissionId: 7,
    }));

    renderWithRoutes('/history/42/review', <HistoryReviewPage />);

    expect(await screen.findByText(/part of a full mock test/i)).toBeInTheDocument();
    expect(screen.queryByText('No detailed answers recorded for this test.')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /view mock test report/i }));
    expect(await screen.findByText('MOCK TEST REPORT PAGE')).toBeInTheDocument();
  });

  it('keeps the generic message for a practice row that predates answer recording', async () => {
    statsApi.getHistoryDetail.mockResolvedValue(ok({
      historyId: 43, skillType: 'READING', score: 6.0, recordedAt: '2026-08-01T09:30:00',
      totalQuestions: 0, correctCount: 0, answers: [], mockTestSubmissionId: null,
    }));

    renderWithRoutes('/history/43/review', <HistoryReviewPage />);

    expect(await screen.findByText('No detailed answers recorded for this test.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /view mock test report/i })).not.toBeInTheDocument();
  });
});

describe('Dashboard Recent Activity links', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    statsApi.getOverview.mockResolvedValue(ok({
      totalTests: 2, targetMetCount: 0, targetBand: 7.0, currentEstimate: 6.5,
      skills: [{ skill: 'READING', currentAvg: 6.5, targetScore: 7.0, progressPercent: 90, gap: 0.5, status: 'IN_PROGRESS', totalTests: 2 }],
    }));
    statsApi.getScoreTrend.mockResolvedValue(ok({ skill: 'READING', period: 'MONTHLY', dataPoints: [] }));
    analyticsApi.getWeakness.mockResolvedValue(ok({ accuracies: {} }));
    statsApi.getHistory.mockResolvedValue(ok({
      items: [
        { historyId: 42, skillType: 'LISTENING', score: 7.0, recordedAt: '2026-08-01T09:30:00', mockTestSubmissionId: 7 },
        { historyId: 43, skillType: 'READING', score: 6.0, recordedAt: '2026-07-01T09:30:00', mockTestSubmissionId: null },
      ],
      page: 0, size: 5, totalItems: 2, totalPages: 1,
    }));
  });

  it('opens the mock test report for a row that belongs to a full mock test', async () => {
    renderWithRoutes('/dashboard', <DashboardPage />);

    const reportLink = await screen.findByRole('button', { name: /view report/i });
    fireEvent.click(reportLink);
    expect(await screen.findByText('MOCK TEST REPORT PAGE')).toBeInTheDocument();
  });

  it('still opens the answer review for a practice row', async () => {
    renderWithRoutes('/dashboard', <DashboardPage />);

    const reviewLinks = await screen.findAllByRole('button', { name: /review answers/i });
    expect(reviewLinks).toHaveLength(1);
  });
});
