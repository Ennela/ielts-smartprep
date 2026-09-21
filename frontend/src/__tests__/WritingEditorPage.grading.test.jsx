import '@testing-library/jest-dom';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import WritingEditorPage from '../pages/WritingEditorPage';

/*
 * Grading is synchronous and slow. When the response is lost (gateway timeout, dropped
 * connection) the backend has usually still graded and saved the essay; letting the
 * candidate click "Submit for Grading" again produced two submissions and two Gemini
 * bills. The page now looks in the history for the backend's copy before it re-enables
 * the button.
 */

vi.mock('../api/writingApi', () => ({
  default: { getPromptById: vi.fn(), gradeEssay: vi.fn(), getHistory: vi.fn() },
}));
vi.mock('../api/attemptApi', () => ({
  default: { startAttempt: vi.fn(), getAttempt: vi.fn(), completeAttempt: vi.fn() },
}));
vi.mock('../context/AuthContext', () => ({
  useAuth: () => ({ user: { userId: 1, username: 'student' }, isAuthenticated: true, isAdmin: false }),
}));
vi.mock('../context/ToastContext', () => ({
  useToast: () => ({ success: () => {}, error: () => {}, info: () => {}, warning: () => {} }),
}));
// Speed the recovery poll up: 3 tries, no delay between them.
vi.mock('../utils/gradingRecovery', async (importOriginal) => {
  const mod = await importOriginal();
  return {
    ...mod,
    findNewSubmission: (fetchPage, idField, beforeId, matches) =>
      mod.findNewSubmission(fetchPage, idField, beforeId, matches, { tries: 3, intervalMs: 0 }),
  };
});

import writingApi from '../api/writingApi';
import attemptApi from '../api/attemptApi';

const ok = (data) => ({ data: { success: true, data } });
const page = (rows) => ok({ content: rows, totalElements: rows.length, totalPages: 1 });
const prompt = { promptId: 42, essayType: 'OPINION', promptText: 'Discuss.', imageUrl: null };
const essay = Array.from({ length: 260 }, (_, i) => `word${i}`).join(' ');

function renderEditor() {
  return render(
    <MemoryRouter initialEntries={['/writing/editor/42']}>
      <Routes>
        <Route path="/writing/editor/:promptId" element={<WritingEditorPage />} />
        <Route path="/writing/result/:submissionId" element={<div>Result Page</div>} />
      </Routes>
    </MemoryRouter>
  );
}

async function typeEssayAndSubmit() {
  renderEditor();
  const textarea = await screen.findByRole('textbox');
  fireEvent.change(textarea, { target: { value: essay } });
  // Word count is debounced 300 ms before the button unlocks.
  const button = screen.getByRole('button', { name: /Submit for Grading/ });
  await waitFor(() => expect(button).not.toBeDisabled(), { timeout: 2000 });
  fireEvent.click(button);
  return button;
}

describe('WritingEditorPage grading recovery', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    sessionStorage.clear();
    localStorage.clear();
    writingApi.getPromptById.mockResolvedValue(ok(prompt));
    attemptApi.startAttempt.mockResolvedValue(ok({ attemptId: 5, deadline: new Date(Date.now() + 3600_000).toISOString() }));
    attemptApi.completeAttempt.mockResolvedValue(ok({}));
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('opens the submission the backend saved when the grade response was lost', async () => {
    writingApi.getHistory
      .mockResolvedValueOnce(page([{ submissionId: 70, promptId: 41 }]))   // before submitting
      .mockResolvedValueOnce(page([{ submissionId: 70, promptId: 41 }]))   // still grading
      .mockResolvedValueOnce(page([{ submissionId: 71, promptId: 42 }]));  // the backend's copy
    writingApi.gradeEssay.mockRejectedValue(new Error('Network Error'));

    await typeEssayAndSubmit();

    expect(await screen.findByText('Result Page')).toBeInTheDocument();
    expect(writingApi.gradeEssay).toHaveBeenCalledTimes(1);
  });

  it('keeps the button locked while it checks and warns before allowing a resubmit', async () => {
    writingApi.getHistory.mockResolvedValue(page([{ submissionId: 70, promptId: 41 }]));
    writingApi.gradeEssay.mockRejectedValue(Object.assign(new Error('Gateway Timeout'), { status: 504 }));

    const button = await typeEssayAndSubmit();

    expect(await screen.findByText(/checking whether your essay was saved/)).toBeInTheDocument();
    expect(button).toBeDisabled();

    expect(await screen.findByText(/Check your history before submitting again/)).toBeInTheDocument();
    expect(button).not.toBeDisabled();
    expect(screen.queryByText('Result Page')).not.toBeInTheDocument();
  });

  it('re-enables the button at once for a rejection the server actually sent', async () => {
    writingApi.getHistory.mockResolvedValue(page([]));
    writingApi.gradeEssay.mockRejectedValue(Object.assign(new Error('Too many requests'), { status: 429 }));

    const button = await typeEssayAndSubmit();

    expect(await screen.findByText('Too many requests')).toBeInTheDocument();
    expect(button).not.toBeDisabled();
    // Only the snapshot before submitting; no recovery polling for a 4xx.
    expect(writingApi.getHistory).toHaveBeenCalledTimes(1);
  });

  it('shows how long the grade has been running', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    writingApi.getHistory.mockResolvedValue(page([]));
    writingApi.gradeEssay.mockReturnValue(new Promise(() => {}));

    const button = await typeEssayAndSubmit();

    await act(async () => { await vi.advanceTimersByTimeAsync(65_000); });
    expect(button).toHaveTextContent('AI is grading... 1:05');
  });
});
