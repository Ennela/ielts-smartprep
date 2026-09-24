import '@testing-library/jest-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import VocabularyPage from '../pages/VocabularyPage';

/*
 * The explanation opens over the vocabulary page rather than navigating away, so the
 * learner never loses their place. These tests also cover the records saved before the
 * feature existed, which have no hasInsight flag and no explanation at all.
 */

vi.mock('../api/vocabApi', () => ({
  default: {
    getVocab: vi.fn(),
    getDueVocab: vi.fn(),
    getStats: vi.fn(),
    addVocab: vi.fn(),
    reviewVocab: vi.fn(),
    deleteVocab: vi.fn(),
    getInsight: vi.fn(),
    generateInsight: vi.fn(),
  },
}));

import vocabApi from '../api/vocabApi';

const ok = (data) => ({ data: { success: true, data } });
const page = (words) => ok({ content: words, totalElements: words.length, totalPages: 1, number: 0, size: 12 });

/** A record saved before this feature: no hasInsight, no explanation. */
const legacyWord = { vocabId: 1, word: 'ubiquitous', meaningVi: 'phổ biến', cefrLevel: 'C1', partOfSpeech: 'adjective' };

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <VocabularyPage />
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe('VocabularyPage explanations', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vocabApi.getStats.mockResolvedValue(ok({ masteredCount: 0, learningCount: 0, dueTodayCount: 0 }));
    vocabApi.getDueVocab.mockResolvedValue(ok({ content: [], totalElements: 0, totalPages: 0 }));
    vocabApi.getVocab.mockResolvedValue(page([legacyWord]));
    vocabApi.getInsight.mockResolvedValue(ok({ vocabId: 1, word: 'ubiquitous', status: 'NOT_GENERATED' }));
  });

  it('renders a record saved before explanations existed', async () => {
    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: /Word Bank/ }));

    expect(await screen.findByText('ubiquitous')).toBeInTheDocument();
    expect(screen.getByText('phổ biến')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Giải thích chi tiết/ })).toBeInTheDocument();
  });

  it('opens the explanation over the word bank without navigating away', async () => {
    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: /Word Bank/ }));
    fireEvent.click(await screen.findByRole('button', { name: /Giải thích chi tiết/ }));

    expect(await screen.findByRole('dialog')).toHaveAccessibleName(/ubiquitous/);
    await waitFor(() => expect(vocabApi.getInsight).toHaveBeenCalledWith(1));
    // The word bank is still behind the panel, with its page intact.
    expect(screen.getByPlaceholderText(/Search/i)).toBeInTheDocument();
  });

  it('opens the explanation from a review card without advancing the session', async () => {
    vocabApi.getDueVocab.mockResolvedValue(
      ok({ content: [legacyWord], totalElements: 1, totalPages: 1 })
    );

    renderPage();
    expect(await screen.findByText('Card 1 of 1')).toBeInTheDocument();

    fireEvent.click(await screen.findByRole('button', { name: /Giải thích chi tiết/ }));

    expect(await screen.findByRole('dialog')).toBeInTheDocument();
    expect(screen.getByText('Card 1 of 1')).toBeInTheDocument();
    expect(vocabApi.reviewVocab).not.toHaveBeenCalled();
  });
});
