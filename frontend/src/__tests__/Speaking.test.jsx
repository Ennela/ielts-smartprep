import '@testing-library/jest-dom';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ConfirmProvider } from '../context/ConfirmContext';
import SpeakingPracticePage from '../pages/SpeakingPracticePage';
import SpeakingResultPage from '../pages/SpeakingResultPage';

/*
 * Speaking practice: Part 2 gives a minute to prepare and then records; a stopped
 * recording can be played back and is sent with its length; the result page shows
 * the four criteria and plays the learner's own recording.
 */

vi.mock('../api/speakingApi', () => ({
  default: { getPrompts: vi.fn(), grade: vi.fn(), getSubmission: vi.fn(), getRecording: vi.fn() },
}));
import speakingApi from '../api/speakingApi';

const cueCard = {
  promptId: 8, part: 2, topic: 'A memorable journey', questions: ['Describe a journey you remember well.'],
  cuePoints: ['where you went', 'who you went with'], prepSeconds: 60, maxSpeakSeconds: 120,
};
const ok = (data) => Promise.resolve({ data: { success: true, data } });

class FakeRecorder {
  static isTypeSupported() { return true; }
  constructor(stream, options) { this.mimeType = options.mimeType; this.state = 'inactive'; }
  start() { this.state = 'recording'; }
  stop() {
    this.state = 'inactive';
    this.ondataavailable?.({ data: new Blob(['abc'], { type: 'audio/webm' }) });
    this.onstop?.();
  }
}

function renderAt(path) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <ConfirmProvider>
        <MemoryRouter initialEntries={[path]}>
          <Routes>
            <Route path="/speaking/practice/:promptId" element={<SpeakingPracticePage />} />
            <Route path="/speaking/result/:submissionId" element={<SpeakingResultPage />} />
          </Routes>
        </MemoryRouter>
      </ConfirmProvider>
    </QueryClientProvider>,
  );
}

describe('SpeakingPracticePage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.useFakeTimers({ shouldAdvanceTime: true });
    window.MediaRecorder = FakeRecorder;
    Object.defineProperty(navigator, 'mediaDevices', {
      configurable: true,
      value: { getUserMedia: vi.fn(() => Promise.resolve({ getTracks: () => [{ stop: vi.fn() }] })) },
    });
    URL.createObjectURL = vi.fn(() => 'blob:answer');
    URL.revokeObjectURL = vi.fn();
    speakingApi.getPrompts.mockImplementation(() => ok([cueCard]));
  });
  afterEach(() => {
    vi.useRealTimers();
    delete window.MediaRecorder;
  });

  it('prepares for a minute, records, and sends the answer with its length', async () => {
    speakingApi.grade.mockImplementation(() => ok({ submissionId: 31 }));
    speakingApi.getSubmission.mockImplementation(() => new Promise(() => {}));
    renderAt('/speaking/practice/8');

    expect(await screen.findByText('Describe a journey you remember well.')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Start preparation' }));
    expect(screen.getByText('Preparation')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Start speaking now' }));
    expect(await screen.findByRole('button', { name: 'Stop recording' })).toBeInTheDocument();

    await act(async () => { vi.advanceTimersByTime(12000); });
    fireEvent.click(screen.getByRole('button', { name: 'Stop recording' }));

    expect(await screen.findByLabelText('Your recording')).toHaveAttribute('src', 'blob:answer');
    fireEvent.click(screen.getByRole('button', { name: 'Submit for grading' }));

    await waitFor(() => expect(speakingApi.grade).toHaveBeenCalledTimes(1));
    const [promptId, blob, seconds] = speakingApi.grade.mock.calls[0];
    expect(promptId).toBe(8);
    expect(blob).toBeInstanceOf(Blob);
    expect(seconds).toBeGreaterThanOrEqual(11);
  });

  it('explains a blocked microphone instead of failing silently', async () => {
    navigator.mediaDevices.getUserMedia.mockImplementation(() => Promise.reject(Object.assign(new Error('no'), { name: 'NotAllowedError' })));
    speakingApi.getPrompts.mockImplementation(() => ok([{ ...cueCard, part: 1, prepSeconds: 0 }]));
    renderAt('/speaking/practice/8');

    fireEvent.click(await screen.findByRole('button', { name: 'Start recording' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/Microphone access was blocked/);
  });
});

describe('SpeakingResultPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    URL.createObjectURL = vi.fn(() => 'blob:mine');
    URL.revokeObjectURL = vi.fn();
  });

  it('shows the four criteria, the feedback and the learner\'s recording', async () => {
    speakingApi.getSubmission.mockImplementation(() => ok({
      submissionId: 31, prompt: cueCard, durationSeconds: 95, transcript: 'I went to Da Lat.',
      overallBand: 6.5, fluencyBand: 6.5, lexicalBand: 6, grammarBand: 6, pronunciationBand: 7,
      summary: 'A clear answer.', strengths: ['Good range'], improvements: ['Link ideas'],
      criteriaComments: { pronunciation: 'Clear sounds.' }, submittedAt: '2026-10-05T09:00:00',
    }));
    speakingApi.getRecording.mockImplementation(() => Promise.resolve({ data: new Blob(['x']) }));
    renderAt('/speaking/result/31');

    expect(await screen.findByText('Overall band')).toBeInTheDocument();
    expect(screen.getByText('Overall band').previousSibling).toHaveTextContent('6.5');
    expect(screen.getByText('Pronunciation')).toBeInTheDocument();
    expect(screen.getByText('Clear sounds.')).toBeInTheDocument();
    expect(screen.getByText('Link ideas')).toBeInTheDocument();
    expect(screen.getByText('I went to Da Lat.')).toBeInTheDocument();
    expect(await screen.findByLabelText('Your recording')).toHaveAttribute('src', 'blob:mine');
  });
});
