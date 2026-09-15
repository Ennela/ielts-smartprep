import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import WritingFullExamPage from '../pages/WritingFullExamPage';

/*
 * WritingPromptListPage opens the full test with ?task1Id=&task2Id= (both the assembled
 * and the AI-generated path). The exam page read ?task1=&task2= instead, so every
 * attempt to start a full Writing test ended on "Missing task prompts".
 */

vi.mock('../api/writingApi', () => ({
  default: { getPromptById: vi.fn(), submitFullWriting: vi.fn() },
}));
vi.mock('../api/attemptApi', () => ({
  default: { startAttempt: vi.fn(), getAttempt: vi.fn(), completeAttempt: vi.fn() },
}));
vi.mock('../context/ToastContext', () => ({
  useToast: () => ({ success: () => {}, error: () => {}, info: () => {}, warning: () => {} }),
}));

import writingApi from '../api/writingApi';
import attemptApi from '../api/attemptApi';

const ok = (data) => ({ data: { success: true, data } });

describe('WritingFullExamPage entry', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    sessionStorage.clear();
    writingApi.getPromptById.mockImplementation((id) => Promise.resolve(ok(
      Number(id) === 90
        ? { promptId: 90, essayType: 'LINE_GRAPH', promptText: 'Describe the graph of city populations.' }
        : { promptId: 13, essayType: 'OPINION', promptText: 'Crime rates are higher in cities. Discuss.' }
    )));
    attemptApi.startAttempt.mockResolvedValue(ok({
      attemptId: 501, skillType: 'WRITING', status: 'IN_PROGRESS',
      deadline: new Date(Date.now() + 3600_000).toISOString(),
    }));
  });

  it('loads both prompts from the task1Id/task2Id query the list page sends', async () => {
    render(
      <MemoryRouter initialEntries={['/writing/full-exam?task1Id=90&task2Id=13']}>
        <Routes>
          <Route path="/writing/full-exam" element={<WritingFullExamPage />} />
        </Routes>
      </MemoryRouter>
    );

    expect(await screen.findByText(/Describe the graph of city populations/)).toBeInTheDocument();
    expect(screen.queryByText('Missing task prompts')).not.toBeInTheDocument();
    expect(writingApi.getPromptById).toHaveBeenCalledWith('90');
    expect(writingApi.getPromptById).toHaveBeenCalledWith('13');
    expect(attemptApi.startAttempt).toHaveBeenCalledWith(expect.objectContaining({
      skillType: 'WRITING',
      examReferenceIds: JSON.stringify([90, 13]),
    }));
  });

  it('still reports missing prompts when neither id is present', async () => {
    render(
      <MemoryRouter initialEntries={['/writing/full-exam']}>
        <Routes>
          <Route path="/writing/full-exam" element={<WritingFullExamPage />} />
        </Routes>
      </MemoryRouter>
    );

    expect(await screen.findByText('Missing task prompts')).toBeInTheDocument();
    expect(writingApi.getPromptById).not.toHaveBeenCalled();
  });
});
