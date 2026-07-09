import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import WritingEditorPage from '../pages/WritingEditorPage';

// Mock the API modules
vi.mock('../api/writingApi', () => ({
  default: {
    getPromptById: vi.fn(),
    gradeEssay: vi.fn(),
  },
}));

vi.mock('../api/attemptApi', () => ({
  default: {
    startAttempt: vi.fn(),
    getAttempt: vi.fn(),
    completeAttempt: vi.fn(),
  },
}));

vi.mock('../context/AuthContext', () => ({
  useAuth: () => ({
    user: { userId: 1, username: 'student' },
    isAuthenticated: true,
    isAdmin: false,
  }),
}));

vi.mock('../context/ToastContext', () => ({
  useToast: () => ({
    success: () => {},
    error: () => {},
    info: () => {},
    warning: () => {},
  }),
}));

import writingApi from '../api/writingApi';
import attemptApi from '../api/attemptApi';

const mockPrompt = {
  promptId: 42,
  essayType: 'OPINION',
  promptText: 'Some people think that schools should teach children practical skills. Discuss.',
  imageUrl: null,
};

const mockTask1Prompt = {
  promptId: 43,
  essayType: 'LINE_GRAPH',
  promptText: 'Describe the graph below showing population growth.',
  imageUrl: 'http://example.com/chart.png',
};

function renderWithRouter(promptId = '42') {
  return render(
    <MemoryRouter initialEntries={[`/writing/editor/${promptId}`]}>
      <Routes>
        <Route path="/writing/editor/:promptId" element={<WritingEditorPage />} />
        <Route path="/writing" element={<div>Writing List</div>} />
        <Route path="/writing/result/:submissionId" element={<div>Result Page</div>} />
      </Routes>
    </MemoryRouter>
  );
}

describe('WritingEditorPage - Timer Integration', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    sessionStorage.clear();
  });

  it('should create an attempt and display countdown timer on mount', async () => {
    const deadline = new Date(Date.now() + 3600_000).toISOString(); // 60 min from now

    writingApi.getPromptById.mockResolvedValue({
      data: { data: mockPrompt },
    });

    attemptApi.startAttempt.mockResolvedValue({
      data: {
        data: {
          attemptId: 101,
          deadline,
          status: 'IN_PROGRESS',
          suggestedTask1Duration: 1200,
          suggestedTask2Duration: 2400,
        },
      },
    });

    renderWithRouter('42');

    // Wait for prompt to load
    await waitFor(() => {
      expect(writingApi.getPromptById).toHaveBeenCalledWith('42');
    });

    // Wait for attempt to be started
    await waitFor(() => {
      expect(attemptApi.startAttempt).toHaveBeenCalledWith({
        skillType: 'WRITING',
        examReferenceIds: JSON.stringify([42]),
      });
    });

    // Timer pill should render
    await waitFor(() => {
      const timer = screen.getByTitle('Time remaining for this writing session');
      expect(timer).toBeInTheDocument();
      // Should display approximately 59:XX or 60:00
      expect(timer.textContent).toMatch(/\d{2}:\d{2}/);
    });
  });

  it('should display suggested time for Task 2 prompt', async () => {
    const deadline = new Date(Date.now() + 3600_000).toISOString();

    writingApi.getPromptById.mockResolvedValue({
      data: { data: mockPrompt }, // OPINION = Task 2
    });

    attemptApi.startAttempt.mockResolvedValue({
      data: {
        data: {
          attemptId: 102,
          deadline,
          status: 'IN_PROGRESS',
          suggestedTask1Duration: 1200,
          suggestedTask2Duration: 2400,
        },
      },
    });

    renderWithRouter('42');

    await waitFor(() => {
      expect(screen.getByText(/Suggested: ~40 minutes/)).toBeInTheDocument();
    });
  });

  it('should display suggested time for Task 1 prompt', async () => {
    const deadline = new Date(Date.now() + 3600_000).toISOString();

    writingApi.getPromptById.mockResolvedValue({
      data: { data: mockTask1Prompt }, // LINE_GRAPH = Task 1
    });

    attemptApi.startAttempt.mockResolvedValue({
      data: {
        data: {
          attemptId: 103,
          deadline,
          status: 'IN_PROGRESS',
          suggestedTask1Duration: 1200,
          suggestedTask2Duration: 2400,
        },
      },
    });

    renderWithRouter('43');

    await waitFor(() => {
      const indicator = document.getElementById('writing-suggested-time');
      expect(indicator).not.toBeNull();
      expect(indicator.textContent).toMatch(/~20 minutes for Task 1/);
    });
  });

  it('should resume existing attempt from sessionStorage', async () => {
    const deadline = new Date(Date.now() + 1800_000).toISOString(); // 30 min remaining

    // Pre-store attempt ID in session storage
    sessionStorage.setItem('writing_single_attemptId_42', '200');

    writingApi.getPromptById.mockResolvedValue({
      data: { data: mockPrompt },
    });

    attemptApi.getAttempt.mockResolvedValue({
      data: {
        data: {
          attemptId: 200,
          deadline,
          status: 'IN_PROGRESS',
          suggestedTask1Duration: 1200,
          suggestedTask2Duration: 2400,
        },
      },
    });

    renderWithRouter('42');

    await waitFor(() => {
      // Should call getAttempt to resume, not startAttempt
      expect(attemptApi.getAttempt).toHaveBeenCalledWith('200');
      expect(attemptApi.startAttempt).not.toHaveBeenCalled();
    });

    // Timer should be visible
    await waitFor(() => {
      const timer = screen.getByTitle('Time remaining for this writing session');
      expect(timer).toBeInTheDocument();
    });
  });

  it('should create new attempt if stored attempt is not IN_PROGRESS', async () => {
    const deadline = new Date(Date.now() + 3600_000).toISOString();

    sessionStorage.setItem('writing_single_attemptId_42', '300');

    writingApi.getPromptById.mockResolvedValue({
      data: { data: mockPrompt },
    });

    // Stored attempt is completed
    attemptApi.getAttempt.mockResolvedValue({
      data: {
        data: {
          attemptId: 300,
          deadline: new Date(Date.now() - 3600_000).toISOString(),
          status: 'SUBMITTED',
        },
      },
    });

    attemptApi.startAttempt.mockResolvedValue({
      data: {
        data: {
          attemptId: 301,
          deadline,
          status: 'IN_PROGRESS',
          suggestedTask1Duration: 1200,
          suggestedTask2Duration: 2400,
        },
      },
    });

    renderWithRouter('42');

    await waitFor(() => {
      // Should have tried getAttempt first, then startAttempt
      expect(attemptApi.getAttempt).toHaveBeenCalledWith('300');
      expect(attemptApi.startAttempt).toHaveBeenCalled();
    });
  });

  it('should still render page if attempt API fails (graceful degradation)', async () => {
    writingApi.getPromptById.mockResolvedValue({
      data: { data: mockPrompt },
    });

    attemptApi.startAttempt.mockRejectedValue(new Error('Network error'));

    renderWithRouter('42');

    // Page should render even without timer
    await waitFor(() => {
      expect(screen.getByPlaceholderText(/Start writing your essay/)).toBeInTheDocument();
    });

    // Timer should NOT be visible
    expect(screen.queryByTitle('Time remaining for this writing session')).not.toBeInTheDocument();
  });
});
