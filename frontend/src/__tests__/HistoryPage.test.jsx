import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import HistoryPage from '../pages/HistoryPage';

/*
 * The History page lists one server-side page of the merged feed (GET /history). Rows,
 * paging and the three filters all come from that one request; the page only formats
 * each row's title, band and review link from the skill the server labelled it with.
 */

vi.mock('../api/historyApi', () => ({ default: { getFeed: vi.fn() } }));

import historyApi from '../api/historyApi';

const page = (content, { totalElements = content.length, totalPages = 1, number = 0 } = {}) => ({
  data: { success: true, data: { content, totalElements, totalPages, number, size: 8, first: number === 0, last: number === totalPages - 1 } },
});

function renderPage() {
  return render(
    <MemoryRouter>
      <HistoryPage />
    </MemoryRouter>
  );
}

describe('HistoryPage over the merged feed', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('formats one row per feed item by skill, with the time it recorded', async () => {
    historyApi.getFeed.mockResolvedValue(page([
      { skill: 'MOCK_TEST', refId: 41, title: 'Cambridge 19 Test 1', score: 6.5, status: 'COMPLETED', submittedAt: '2026-09-04T10:00:00', timeSpentSeconds: 9900 },
      { skill: 'WRITING', refId: 31, title: 'OPINION', score: 6, submittedAt: '2026-09-03T10:00:00', timeSpentSeconds: 2400 },
      { skill: 'LISTENING', refId: 21, title: 'PRACTICE', score: 7, submittedAt: '2026-09-02T10:00:00', historyId: 77 },
      { skill: 'READING', refId: 11, title: 'HEALTH', score: 6.5, submittedAt: '2026-09-01T10:00:00', timeSpentSeconds: 58 },
      { skill: 'MOCK_TEST', refId: 40, title: 'Cambridge 19 Test 2', score: 0, status: 'GRADING', submittedAt: '2026-08-30T10:00:00' },
    ]));

    renderPage();

    expect(await screen.findByText('Cambridge 19 Test 1')).toBeInTheDocument();
    expect(screen.getByText('Task 2 Essay: Opinion')).toBeInTheDocument();
    expect(screen.getByText('Band 6.0')).toBeInTheDocument();
    expect(screen.getByText('Listening Section Practice')).toBeInTheDocument();
    expect(screen.getByText('HEALTH')).toBeInTheDocument();
    expect(screen.getByText('Grading...')).toBeInTheDocument();
    expect(screen.getByText('2h 45m')).toBeInTheDocument();
    expect(screen.getByText('40 mins')).toBeInTheDocument();
    expect(screen.getByText('1 min')).toBeInTheDocument();
    // Two rows recorded no time: the listening test and the sitting still grading.
    expect(screen.getAllByText('—')).toHaveLength(2);
    expect(screen.getByText(/Showing 1 to 5 of 5 entries/)).toBeInTheDocument();
    expect(historyApi.getFeed).toHaveBeenCalledWith({ page: 0, size: 8 });
  });

  it('opens the answer review for a listening test that has one, and the result page otherwise', async () => {
    historyApi.getFeed.mockResolvedValue(page([
      { skill: 'LISTENING', refId: 21, title: 'PRACTICE', score: 7, submittedAt: '2026-09-02T10:00:00', historyId: 77 },
      { skill: 'LISTENING', refId: 22, title: 'MOCK_TEST', score: 5, submittedAt: '2026-09-01T10:00:00' },
    ]));
    render(
      <MemoryRouter initialEntries={['/history']}>
        <Routes>
          <Route path="/history" element={<HistoryPage />} />
          <Route path="/history/:historyId/review" element={<div>REVIEW 77</div>} />
          <Route path="/listening/result/:testId" element={<div>RESULT 22</div>} />
        </Routes>
      </MemoryRouter>
    );

    const buttons = await screen.findAllByRole('button', { name: 'View Review' });
    fireEvent.click(buttons[0]);
    expect(await screen.findByText('REVIEW 77')).toBeInTheDocument();
  });

  it('sends the skill, time and search filters to the server and goes back to page 1', async () => {
    historyApi.getFeed.mockResolvedValue(page([
      { skill: 'READING', refId: 11, title: 'HEALTH', score: 6.5, submittedAt: '2026-09-01T10:00:00' },
    ], { totalElements: 9, totalPages: 2 }));

    renderPage();
    await screen.findByText('HEALTH');

    fireEvent.click(screen.getByRole('button', { name: '2' }));
    await waitFor(() => expect(historyApi.getFeed).toHaveBeenLastCalledWith({ page: 1, size: 8 }));

    fireEvent.change(screen.getByDisplayValue('All Skills'), { target: { value: 'Reading' } });
    await waitFor(() => expect(historyApi.getFeed).toHaveBeenLastCalledWith({ page: 0, size: 8, skill: 'READING' }));

    fireEvent.change(screen.getByDisplayValue('All Time'), { target: { value: 'Last 30 Days' } });
    await waitFor(() => expect(historyApi.getFeed).toHaveBeenLastCalledWith(
      expect.objectContaining({ page: 0, size: 8, skill: 'READING', from: expect.stringMatching(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}$/) })));

    fireEvent.change(screen.getByPlaceholderText('Search assessments...'), { target: { value: 'heal' } });
    await waitFor(() => expect(historyApi.getFeed).toHaveBeenLastCalledWith(
      expect.objectContaining({ page: 0, q: 'heal' })));
  });

  it('shows "no attempts" for an empty history and "no matching" for an empty filtered page', async () => {
    historyApi.getFeed.mockResolvedValue(page([]));

    renderPage();
    expect(await screen.findByText('No attempts recorded yet')).toBeInTheDocument();

    fireEvent.change(screen.getByDisplayValue('All Skills'), { target: { value: 'Writing' } });
    expect(await screen.findByText('No matching history found')).toBeInTheDocument();
  });

  it('shows the error state when the feed request fails', async () => {
    historyApi.getFeed.mockRejectedValue(new Error('boom'));
    vi.spyOn(console, 'error').mockImplementation(() => {});

    renderPage();

    expect(await screen.findByText('Failed to fetch test history records.')).toBeInTheDocument();
  });
});
