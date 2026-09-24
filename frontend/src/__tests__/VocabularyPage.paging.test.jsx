import '@testing-library/jest-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import VocabularyPage from '../pages/VocabularyPage';

/*
 * The word bank fetched every word the user had ever saved and filtered the array in
 * the browser, so the page got slower as their collection grew. It now asks the server
 * for one page with the filters applied, and the search box is debounced.
 */

vi.mock('../api/vocabApi', () => ({
  default: {
    getVocab: vi.fn(),
    getDueVocab: vi.fn(),
    getStats: vi.fn(),
    addVocab: vi.fn(),
    reviewVocab: vi.fn(),
    deleteVocab: vi.fn(),
  },
}));

import vocabApi from '../api/vocabApi';

const ok = (data) => ({ data: { success: true, data } });
const wordPage = (words, { totalElements = words.length, totalPages = 1 } = {}) =>
  ok({ content: words, totalElements, totalPages, number: 0, size: 12 });
const word = (id, w) => ({ vocabId: id, word: w, meaningVi: 'nghĩa', cefrLevel: 'B2', partOfSpeech: 'noun' });

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter><VocabularyPage /></MemoryRouter>
    </QueryClientProvider>
  );
}

const openWordBank = async () => {
  fireEvent.click(await screen.findByRole('button', { name: /Word Bank/ }));
};

describe('VocabularyPage word bank', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.useRealTimers();
    vocabApi.getDueVocab.mockResolvedValue(ok({ content: [], totalElements: 0, totalPages: 0 }));
    vocabApi.getStats.mockResolvedValue(ok({ masteredCount: 0, learningCount: 0, dueTodayCount: 0 }));
    vocabApi.getVocab.mockResolvedValue(wordPage([word(1, 'ubiquitous')]));
  });

  it('asks the server for one page instead of the whole collection', async () => {
    renderPage();
    await openWordBank();

    expect(await screen.findByText('ubiquitous')).toBeInTheDocument();
    expect(vocabApi.getVocab).toHaveBeenCalledWith(
      expect.objectContaining({ page: 0, size: 12, cefr: 'ALL', skill: 'ALL' })
    );
  });

  it('sends the filters to the server rather than filtering the array', async () => {
    renderPage();
    await openWordBank();
    await screen.findByText('ubiquitous');

    const [cefrSelect] = screen.getAllByRole('combobox');
    fireEvent.change(cefrSelect, { target: { value: 'C1' } });

    await waitFor(() => expect(vocabApi.getVocab).toHaveBeenLastCalledWith(
      expect.objectContaining({ cefr: 'C1', page: 0 })
    ));
  });

  it('debounces the search box into a single request', async () => {
    renderPage();
    await openWordBank();
    await screen.findByText('ubiquitous');
    const callsBefore = vocabApi.getVocab.mock.calls.length;

    const search = screen.getByPlaceholderText(/Search/i);
    fireEvent.change(search, { target: { value: 'u' } });
    fireEvent.change(search, { target: { value: 'ub' } });
    fireEvent.change(search, { target: { value: 'ubi' } });

    await waitFor(() => expect(vocabApi.getVocab).toHaveBeenLastCalledWith(
      expect.objectContaining({ q: 'ubi' })
    ));
    // One request for the settled term, not one per keystroke.
    expect(vocabApi.getVocab.mock.calls.length).toBeLessThanOrEqual(callsBefore + 2);
  });

  it('shows a retry when the page fails instead of an empty word bank', async () => {
    vocabApi.getVocab.mockRejectedValue(new Error('Service Unavailable'));

    renderPage();
    await openWordBank();

    expect(await screen.findByText('Service Unavailable')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument();
  });
});
