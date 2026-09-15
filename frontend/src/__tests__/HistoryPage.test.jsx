import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import HistoryPage from '../pages/HistoryPage';

/*
 * The four history endpoints return a Spring Page, so the rows live under `content`.
 * This page used to read the body as a bare array; with a Page object that threw inside
 * `.map` and the whole page fell into its error state.
 */

vi.mock('../api/readingApi', () => ({ default: { getHistory: vi.fn() } }));
vi.mock('../api/listeningApi', () => ({ default: { getHistory: vi.fn() } }));
vi.mock('../api/writingApi', () => ({ default: { getHistory: vi.fn() } }));
vi.mock('../api/mockTestApi', () => ({ default: { getHistory: vi.fn() } }));

import readingApi from '../api/readingApi';
import listeningApi from '../api/listeningApi';
import writingApi from '../api/writingApi';
import mockTestApi from '../api/mockTestApi';

const page = (content) => ({
  data: { success: true, data: { content, totalElements: content.length, totalPages: 1, number: 0, size: 100, first: true, last: true } },
});

function renderPage() {
  return render(
    <MemoryRouter>
      <HistoryPage />
    </MemoryRouter>
  );
}

describe('HistoryPage with paged history endpoints', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders one row per item from the content of each page', async () => {
    readingApi.getHistory.mockResolvedValue(page([
      { quizId: 11, historyId: 1, topic: 'HEALTH', bandScore: 6.5, correctAnswers: 9, totalQuestions: 13, submittedAt: '2026-09-01T10:00:00' },
    ]));
    listeningApi.getHistory.mockResolvedValue(page([
      { testId: 21, historyId: 2, testMode: 'PRACTICE', score: 7.0, correctAnswers: 8, totalQuestions: 10, submittedAt: '2026-09-02T10:00:00' },
    ]));
    writingApi.getHistory.mockResolvedValue(page([
      { submissionId: 31, essayType: 'OPINION', overallBand: 6.0, submittedAt: '2026-09-03T10:00:00' },
    ]));
    mockTestApi.getHistory.mockResolvedValue(page([
      { submissionId: 41, title: 'Cambridge 19 Test 1', status: 'COMPLETED', overallBand: 6.5, submittedAt: '2026-09-04T10:00:00' },
    ]));

    renderPage();

    expect(await screen.findByText('Cambridge 19 Test 1')).toBeInTheDocument();
    expect(screen.getByText('HEALTH')).toBeInTheDocument();
    expect(screen.getByText('Listening Section Practice')).toBeInTheDocument();
    expect(screen.getByText('Task 2 Essay: Opinion')).toBeInTheDocument();
    expect(screen.queryByText('Failed to fetch test history records.')).not.toBeInTheDocument();
    expect(screen.getByText(/Showing 1 to 4 of 4 entries/)).toBeInTheDocument();
  });

  it('asks every endpoint for the largest page the server allows', async () => {
    readingApi.getHistory.mockResolvedValue(page([]));
    listeningApi.getHistory.mockResolvedValue(page([]));
    writingApi.getHistory.mockResolvedValue(page([]));
    mockTestApi.getHistory.mockResolvedValue(page([]));

    renderPage();

    expect(await screen.findByText('No attempts recorded yet')).toBeInTheDocument();
    expect(readingApi.getHistory).toHaveBeenCalledWith(0, 100);
    expect(listeningApi.getHistory).toHaveBeenCalledWith(0, 100);
    expect(writingApi.getHistory).toHaveBeenCalledWith(0, 100);
    expect(mockTestApi.getHistory).toHaveBeenCalledWith(0, 100);
  });

  it('still shows the rows from the endpoints that answered when one of them fails', async () => {
    readingApi.getHistory.mockRejectedValue(new Error('boom'));
    listeningApi.getHistory.mockResolvedValue(page([]));
    writingApi.getHistory.mockResolvedValue(page([]));
    mockTestApi.getHistory.mockResolvedValue(page([
      { submissionId: 41, title: 'Cambridge 19 Test 2', status: 'GRADING', overallBand: null, submittedAt: '2026-09-04T10:00:00' },
    ]));
    vi.spyOn(console, 'error').mockImplementation(() => {});

    renderPage();

    expect(await screen.findByText('Cambridge 19 Test 2')).toBeInTheDocument();
    expect(screen.getByText('Grading...')).toBeInTheDocument();
  });
});
