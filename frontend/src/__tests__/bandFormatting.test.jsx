import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import WritingHistoryPage from '../pages/WritingHistoryPage';
import ReadingHistoryPage from '../pages/ReadingHistoryPage';
import ReadingConfigPage from '../pages/ReadingConfigPage';
import WritingFullResultPage from '../pages/WritingFullResultPage';

/*
 * A band is a decimal with one place. BigDecimal 7.0 arrives as the JSON number 7, and
 * rendering it raw showed "Band 7" beside a "Band 6.5" on the same page. A missing
 * template time limit is a dash, not "0 mins".
 */

vi.mock('../api/writingApi', () => ({ default: { getHistory: vi.fn(), getFullHistory: vi.fn() } }));
vi.mock('../api/readingApi', () => ({ default: { getHistory: vi.fn(), getTemplates: vi.fn(), startTemplateQuiz: vi.fn() } }));

import writingApi from '../api/writingApi';
import readingApi from '../api/readingApi';

const page = (content) => ({ data: { success: true, data: { content, totalElements: content.length, totalPages: 1, number: 0, size: 10 } } });

function renderWithProviders(ui) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>{ui}</MemoryRouter>
    </QueryClientProvider>
  );
}

describe('band formatting', () => {
  beforeEach(() => vi.clearAllMocks());

  it('WritingHistoryPage shows whole-number bands with one decimal on both tabs', async () => {
    writingApi.getHistory.mockResolvedValue(page([
      { submissionId: 1, essayType: 'OPINION', promptTextPreview: 'p', wordCount: 300, overallBand: 7, submittedAt: '2026-09-01T10:00:00' },
    ]));
    writingApi.getFullHistory.mockResolvedValue(page([
      { id: 5, overallWritingBand: 6, submittedAt: '2026-09-02T10:00:00',
        task1Result: { essayType: 'LINE_GRAPH', overallBand: 5 }, task2Result: { essayType: 'OPINION', overallBand: 6.5 } },
    ]));

    renderWithProviders(<WritingHistoryPage />);

    expect(await screen.findByText('Band 7.0')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Full Mock Tests' }));
    expect(await screen.findByText('Band 6.0')).toBeInTheDocument();
    expect(screen.getByText(/Task 1: Line Graph \(Band 5\.0\)/)).toBeInTheDocument();
    expect(screen.getByText(/Task 2: Opinion \(Band 6\.5\)/)).toBeInTheDocument();
  });

  it('WritingFullResultPage shows whole-number task bands with one decimal', () => {
    const result = {
      id: 1, overallWritingBand: 8.5, submittedAt: '2026-09-15T14:48:41',
      task1Result: { submissionId: 20, essayType: 'DIAGRAM', overallBand: 8, promptText: 'p1', essayText: 'e1', errors: [] },
      task2Result: { submissionId: 21, essayType: 'ADVANTAGES_DISADVANTAGES', overallBand: 9, promptText: 'p2', essayText: 'e2', errors: [] },
    };
    render(
      <MemoryRouter initialEntries={[{ pathname: '/writing/full-result', state: { result } }]}>
        <WritingFullResultPage />
      </MemoryRouter>
    );

    expect(screen.getByText(/Task 1: Academic Report \(Band 8\.0\)/)).toBeInTheDocument();
    expect(screen.getByText(/Task 2: Essay Writing \(Band 9\.0\)/)).toBeInTheDocument();
    expect(screen.getByText('8.0')).toBeInTheDocument();
    expect(screen.getByText('9.0')).toBeInTheDocument();
  });

  it('ReadingHistoryPage shows the band with one decimal', async () => {
    readingApi.getHistory.mockResolvedValue(page([
      { quizId: 11, topic: 'HEALTH', difficulty: 'PASSAGE_1', bandScore: 2, correctAnswers: 1, totalQuestions: 13, submittedAt: '2026-09-01T10:00:00' },
    ]));

    renderWithProviders(<ReadingHistoryPage />);

    expect(await screen.findByText('2.0')).toBeInTheDocument();
  });

  it('ReadingConfigPage shows a dash, not "0 mins", for a template without a time limit', async () => {
    readingApi.getTemplates.mockResolvedValue(page([
      { quizId: 72, topic: 'TECHNOLOGY', difficulty: 'PASSAGE_1', passageText: 'p', timeLimitSeconds: null, totalQuestions: 13 },
      { quizId: 73, topic: 'HEALTH', difficulty: 'PASSAGE_2', passageText: 'p', timeLimitSeconds: 1200, totalQuestions: 13 },
    ]));

    renderWithProviders(<ReadingConfigPage />);
    fireEvent.click(screen.getByRole('button', { name: 'Curated Tests' }));

    expect(await screen.findByText('20 mins')).toBeInTheDocument();
    expect(screen.queryByText('0 mins')).not.toBeInTheDocument();
    expect(screen.getByText('—')).toBeInTheDocument();
  });
});
