import '@testing-library/jest-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import AdminMockTestsPage from '../pages/AdminMockTestsPage';
import AdminWritingPromptsPage from '../pages/AdminWritingPromptsPage';
import AdminReadingQuizzesPage from '../pages/AdminReadingQuizzesPage';
import AdminListeningListPage from '../pages/AdminListeningListPage';

/*
 * eslint's no-undef is off in this repo, so a reference to an undeclared identifier
 * only surfaces at runtime — one did, as a blank /admin/reading-quizzes. Each admin
 * list must at least render and switch to its Archived view.
 */

const page = (rows = []) => Promise.resolve({ data: { success: true, data: { content: rows, totalPages: 1, totalElements: rows.length } } });
const list = () => Promise.resolve({ data: { success: true, data: [] } });

vi.mock('../api/adminApi', () => ({
  default: {
    listMockTests: vi.fn(() => page()),
    listWritingPrompts: vi.fn(() => page()),
    listReadingQuizzes: vi.fn(() => page()),
    listListeningParts: vi.fn(() => page()),
    getListeningStats: vi.fn(() => Promise.resolve({ data: { data: { total: 0, ready: 0, pending: 0, failed: 0 } } })),
  },
}));
vi.mock('../api/listeningApi', () => ({ default: { getAllParts: vi.fn(() => list()) } }));
vi.mock('../context/ToastContext', () => ({
  useToast: () => ({ success: () => {}, error: () => {}, info: () => {}, warning: () => {} }),
}));

import adminApi from '../api/adminApi';

function renderPage(Page) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter><Page /></MemoryRouter>
    </QueryClientProvider>
  );
}

describe.each([
  ['AdminMockTestsPage', AdminMockTestsPage, 'listMockTests'],
  ['AdminWritingPromptsPage', AdminWritingPromptsPage, 'listWritingPrompts'],
  ['AdminReadingQuizzesPage', AdminReadingQuizzesPage, 'listReadingQuizzes'],
  ['AdminListeningListPage', AdminListeningListPage, 'listListeningParts'],
])('%s', (_name, Page, listFn) => {
  beforeEach(() => vi.clearAllMocks());

  it('renders and asks for archived rows when the Archived view is selected', async () => {
    renderPage(Page);

    const archived = await screen.findByRole('button', { name: 'Archived' });
    fireEvent.click(archived);

    await waitFor(() => {
      const calls = adminApi[listFn].mock.calls;
      expect(calls[calls.length - 1].at(-1)).toBe(true);
    });
  });
});
