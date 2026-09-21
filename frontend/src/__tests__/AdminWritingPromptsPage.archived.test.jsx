import '@testing-library/jest-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import AdminWritingPromptsPage from '../pages/AdminWritingPromptsPage';

/*
 * The archive dialog promised "the prompt can be restored", the backend had the restore
 * endpoint and adminApi wrapped it, but no page ever called it and no list could show
 * archived rows. The Archived view lists them and offers Restore.
 */

vi.mock('../api/adminApi', () => ({
  default: {
    listWritingPrompts: vi.fn(),
    restoreWritingPrompt: vi.fn(),
    deleteWritingPrompt: vi.fn(),
  },
}));

import adminApi from '../api/adminApi';

const page = (rows) => ({ data: { success: true, data: { content: rows, totalPages: 1, totalElements: rows.length } } });
const live = { promptId: 1, essayType: 'OPINION', promptText: 'A live prompt', createdAt: '2026-09-01T10:00:00' };
const gone = { promptId: 2, essayType: 'OPINION', promptText: 'An archived prompt', createdAt: '2026-08-01T10:00:00' };

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter><AdminWritingPromptsPage /></MemoryRouter>
    </QueryClientProvider>
  );
}

describe('AdminWritingPromptsPage archived view', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    adminApi.listWritingPrompts.mockImplementation((_type, _page, _size, _sort, archived) =>
      Promise.resolve(page(archived ? [gone] : [live])));
    adminApi.restoreWritingPrompt.mockResolvedValue({ data: { success: true } });
  });

  it('lists archived prompts and restores one back into the active list', async () => {
    renderPage();
    expect(await screen.findByText('A live prompt')).toBeInTheDocument();
    expect(screen.queryByText('Restore')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Archived' }));

    expect(await screen.findByText('An archived prompt')).toBeInTheDocument();
    expect(adminApi.listWritingPrompts).toHaveBeenLastCalledWith(null, 0, 20, 'createdAt,desc', true);
    // Archived rows can only be restored, not edited or archived again.
    expect(screen.queryByText('Edit')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Restore' }));

    await waitFor(() => expect(adminApi.restoreWritingPrompt).toHaveBeenCalledWith(2));
    expect(await screen.findByText('Prompt restored.')).toBeInTheDocument();
  });
});
