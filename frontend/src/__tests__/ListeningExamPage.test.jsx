import '@testing-library/jest-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import ListeningExamPage from '../pages/ListeningExamPage';

/*
 * The listening exam keeps a per-attempt draft of the answers, tells the candidate when
 * the parts or the attempt could not be loaded instead of rendering an empty page, refuses
 * to start on audio the server failed to generate, and sizes a practice attempt to the
 * part chosen rather than the full 32-minute exam.
 */

vi.mock('../api/listeningApi', () => ({
  default: { getPartById: vi.fn(), submitTest: vi.fn() },
}));
vi.mock('../api/attemptApi', () => ({
  default: { startAttempt: vi.fn(), getAttempt: vi.fn() },
}));
vi.mock('../api/adminApi', () => ({ default: {} }));
vi.mock('../context/AuthContext', () => ({
  useAuth: () => ({ user: { role: 'STUDENT' } }),
}));
vi.mock('../context/ToastContext', () => ({
  useToast: () => ({ success: () => {}, error: () => {}, info: () => {}, warning: () => {} }),
}));

import listeningApi from '../api/listeningApi';
import attemptApi from '../api/attemptApi';

const ok = (data) => ({ data: { success: true, data } });

const part = (overrides = {}) => ({
  partId: 3,
  partNumber: 1,
  title: 'Hotel booking',
  audioStatus: 'READY',
  audioUrl: '/api/v1/listening/audio/3.mp3',
  durationSeconds: 240,
  questions: [
    { questionId: 11, questionType: 'FILL_BLANK', questionText: 'Room number: ___', orderIndex: 1 },
    { questionId: 12, questionType: 'FILL_BLANK', questionText: 'Check-in: ___', orderIndex: 2 },
  ],
  ...overrides,
});

const attempt = { attemptId: 90, status: 'IN_PROGRESS', deadline: new Date(Date.now() + 600_000).toISOString() };

function renderExam(query = 'mode=practice&parts=3') {
  return render(
    <MemoryRouter initialEntries={[`/listening/exam?${query}`]}>
      <Routes>
        <Route path="/listening" element={<div>Practice page</div>} />
        <Route path="/listening/exam" element={<ListeningExamPage />} />
      </Routes>
    </MemoryRouter>
  );
}

describe('ListeningExamPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    sessionStorage.clear();
    listeningApi.getPartById.mockResolvedValue(ok(part()));
    attemptApi.startAttempt.mockResolvedValue(ok(attempt));
  });

  it('drafts answers per attempt and restores them when the attempt is resumed', async () => {
    renderExam();
    const [first] = await screen.findAllByRole('textbox');

    fireEvent.change(first, { target: { value: '204' } });

    expect(JSON.parse(localStorage.getItem('listening_draft_90'))).toEqual({ 11: '204' });

    // Reload: the attempt id is still in sessionStorage and the server says it is running.
    sessionStorage.setItem('listening_attemptId', '90');
    attemptApi.getAttempt.mockResolvedValue(ok(attempt));
    renderExam();

    await waitFor(() => expect(screen.getAllByDisplayValue('204')).toHaveLength(1));
    expect(attemptApi.startAttempt).toHaveBeenCalledTimes(1);
  });

  it('sizes a practice attempt to the audio length instead of the full exam', async () => {
    renderExam();
    await screen.findAllByRole('textbox');

    expect(attemptApi.startAttempt).toHaveBeenCalledWith({
      skillType: 'LISTENING',
      examReferenceIds: '[3]',
      durationOverride: 240 + 300,
    });
  });

  it('leaves the mock test on the server default duration', async () => {
    renderExam('mode=mock-test&parts=3');
    await screen.findAllByRole('textbox');

    expect(attemptApi.startAttempt.mock.calls[0][0]).not.toHaveProperty('durationOverride');
  });

  it('shows an error instead of a blank page when a part cannot be loaded', async () => {
    listeningApi.getPartById.mockRejectedValue(new Error('Request failed with status code 500'));

    renderExam();

    expect(await screen.findByText('Request failed with status code 500')).toBeInTheDocument();
    fireEvent.click(screen.getByText('Go Back'));
    expect(await screen.findByText('Practice page')).toBeInTheDocument();
  });

  it('shows an error when the attempt cannot be started rather than an untimed exam', async () => {
    attemptApi.startAttempt.mockRejectedValue(new Error('Service Unavailable'));

    renderExam();

    expect(await screen.findByText('Service Unavailable')).toBeInTheDocument();
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
  });

  it('refuses to start on a part whose audio generation failed', async () => {
    listeningApi.getPartById.mockResolvedValue(ok(part({ audioStatus: 'FAILED', audioUrl: null })));

    renderExam();

    expect(await screen.findByText(/audio for Part 1 could not be generated/)).toBeInTheDocument();
    expect(attemptApi.startAttempt).not.toHaveBeenCalled();
  });
});
