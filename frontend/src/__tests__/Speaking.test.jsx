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
 * recording can be played back and is sent with its length. Parts 1 and 3 ask one
 * question at a time and send one recording per question. The result page shows the
 * four criteria and plays the learner's own recordings.
 */

vi.mock('../api/speakingApi', () => ({
  default: {
    getPrompts: vi.fn(), grade: vi.fn(), getSubmission: vi.fn(), getRecording: vi.fn(), getAnswerRecording: vi.fn(),
  },
}));
import speakingApi from '../api/speakingApi';

const cueCard = {
  promptId: 8, part: 2, topic: 'A memorable journey', questions: ['Describe a journey you remember well.'],
  cuePoints: ['where you went', 'who you went with'], prepSeconds: 60, maxSpeakSeconds: 120,
};
const hometown = {
  promptId: 1, part: 1, topic: 'Hometown', questions: ['Where is your hometown?', 'Do you like it?'],
  cuePoints: [], prepSeconds: 0, maxSpeakSeconds: 40,
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
    const [promptId, recordings] = speakingApi.grade.mock.calls[0];
    expect(promptId).toBe(8);
    expect(recordings).toHaveLength(1);
    expect(recordings[0].blob).toBeInstanceOf(Blob);
    expect(recordings[0].duration).toBeGreaterThanOrEqual(11);
  });

  it('explains a blocked microphone instead of failing silently', async () => {
    navigator.mediaDevices.getUserMedia.mockImplementation(() => Promise.reject(Object.assign(new Error('no'), { name: 'NotAllowedError' })));
    speakingApi.getPrompts.mockImplementation(() => ok([hometown]));
    renderAt('/speaking/practice/1');

    fireEvent.click(await screen.findByRole('button', { name: 'Start the questions' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/Microphone access was blocked/);
  });

  const answerFor = async (seconds) => {
    expect(await screen.findByRole('button', { name: 'Stop answer' })).toBeInTheDocument();
    await act(async () => { vi.advanceTimersByTime(seconds * 1000); });
    fireEvent.click(screen.getByRole('button', { name: 'Stop answer' }));
  };

  it('asks Part 1 questions one at a time and sends one recording per question', async () => {
    speakingApi.getPrompts.mockImplementation(() => ok([hometown]));
    speakingApi.grade.mockImplementation(() => ok({ submissionId: 32 }));
    speakingApi.getSubmission.mockImplementation(() => new Promise(() => {}));
    renderAt('/speaking/practice/1');

    fireEvent.click(await screen.findByRole('button', { name: 'Start the questions' }));
    expect(screen.getByText('Question 1 of 2')).toBeInTheDocument();
    expect(screen.getByText('Where is your hometown?')).toBeInTheDocument();
    expect(screen.queryByText('Do you like it?')).not.toBeInTheDocument();

    await answerFor(8);
    expect(await screen.findByLabelText('Your answer to question 1')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Next question' }));

    expect(await screen.findByText('Question 2 of 2')).toBeInTheDocument();
    expect(screen.getByText('Do you like it?')).toBeInTheDocument();
    await answerFor(5);
    fireEvent.click(await screen.findByRole('button', { name: 'Review answers' }));

    // The review plays every answer; one can be recorded again without redoing the rest.
    expect(await screen.findByLabelText('Your answer to question 2')).toBeInTheDocument();
    fireEvent.click(screen.getAllByRole('button', { name: 'Record again' })[0]);
    expect(await screen.findByText('Question 1 of 2')).toBeInTheDocument();
    await answerFor(12);
    fireEvent.click(await screen.findByRole('button', { name: 'Review answers' }));

    fireEvent.click(await screen.findByRole('button', { name: 'Submit for grading' }));
    await waitFor(() => expect(speakingApi.grade).toHaveBeenCalledTimes(1));
    const [promptId, recordings] = speakingApi.grade.mock.calls[0];
    expect(promptId).toBe(1);
    expect(recordings).toHaveLength(2);
    expect(recordings[0].duration).toBeGreaterThanOrEqual(11);
    expect(recordings[1].duration).toBeLessThan(11);
  });

  it('will not keep an answer under three seconds', async () => {
    speakingApi.getPrompts.mockImplementation(() => ok([hometown]));
    renderAt('/speaking/practice/1');

    fireEvent.click(await screen.findByRole('button', { name: 'Start the questions' }));
    await answerFor(1);

    expect(await screen.findByRole('alert')).toHaveTextContent(/under 3 seconds/);
    expect(screen.getByRole('button', { name: 'Next question' })).toBeDisabled();
  });

  // The error axiosClient rejects with: its message, status, the server's response and,
  // when there is one, the text meant for the user.
  const failure = (status, data, userMessage) => Promise.reject(Object.assign(new Error(data?.message || 'Network Error'), {
    status, response: status ? { status, data } : undefined, userMessage,
  }));

  const recordCueCardAndSubmit = async () => {
    renderAt('/speaking/practice/8');
    fireEvent.click(await screen.findByRole('button', { name: 'Start preparation' }));
    fireEvent.click(screen.getByRole('button', { name: 'Start speaking now' }));
    expect(await screen.findByRole('button', { name: 'Stop recording' })).toBeInTheDocument();
    await act(async () => { vi.advanceTimersByTime(12000); });
    fireEvent.click(screen.getByRole('button', { name: 'Stop recording' }));
    fireEvent.click(await screen.findByRole('button', { name: 'Submit for grading' }));
  };

  it.each([
    ['a framework error', 415, { message: "Content-Type 'application/json' is not supported.", errorCode: 'UNSUPPORTED_MEDIA_TYPE' }, /Grading failed\. Try again; your recording is still here/],
    ['a server failure', 500, { message: 'Internal server error', errorCode: 'INTERNAL_SERVER_ERROR' }, /Grading failed\. Try again/],
    ['the AI being down', 503, { message: 'AI service is unavailable', errorCode: 'AI_SERVICE_ERROR' }, /Grading is not available right now/],
    ['no connection', undefined, undefined, /Could not reach the server/, 'Could not reach the server. Check your internet connection and try again.'],
  ])('turns %s into words the learner understands', async (_, status, data, expected, userMessage) => {
    speakingApi.grade.mockImplementation(() => failure(status, data, userMessage));
    await recordCueCardAndSubmit();

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent(expected);
    expect(alert).not.toHaveTextContent(/Content-Type|Internal server error|AI service|Network Error/);
    expect(screen.getByRole('button', { name: 'Submit for grading' })).toBeEnabled();
  });

  it('passes on what the grading checks say', async () => {
    speakingApi.grade.mockImplementation(() => failure(400, { message: 'The recording is too short to grade (at least 5 seconds)', errorCode: 'BAD_REQUEST' }, 'The recording is too short to grade (at least 5 seconds)'));
    await recordCueCardAndSubmit();
    expect(await screen.findByRole('alert')).toHaveTextContent('The recording is too short to grade (at least 5 seconds)');
  });

  it('keeps Part 1 answers and explains a failed grading in plain words', async () => {
    speakingApi.getPrompts.mockImplementation(() => ok([hometown]));
    speakingApi.grade.mockImplementation(() => failure(415, { message: "Content-Type 'application/json' is not supported.", errorCode: 'UNSUPPORTED_MEDIA_TYPE' }));
    renderAt('/speaking/practice/1');

    fireEvent.click(await screen.findByRole('button', { name: 'Start the questions' }));
    await answerFor(5);
    fireEvent.click(await screen.findByRole('button', { name: 'Next question' }));
    await answerFor(5);
    fireEvent.click(await screen.findByRole('button', { name: 'Review answers' }));
    fireEvent.click(await screen.findByRole('button', { name: 'Submit for grading' }));

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Grading failed. Try again; your answers are still here.');
    expect(alert).not.toHaveTextContent(/Content-Type/);
    expect(screen.getByLabelText('Your answer to question 2')).toBeInTheDocument();
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

  it('shows each Part 1 answer with its question, transcript, comment and recording', async () => {
    speakingApi.getSubmission.mockImplementation(() => ok({
      submissionId: 32, prompt: hometown, durationSeconds: 30, transcript: null,
      overallBand: 6, fluencyBand: 6, lexicalBand: 6, grammarBand: 6, pronunciationBand: 6,
      summary: 'Fine.', strengths: [], improvements: [], criteriaComments: {},
      answers: [
        { questionIndex: 0, question: 'Where is your hometown?', durationSeconds: 20, transcript: 'Hue.', comment: 'Say more about it.' },
        { questionIndex: 1, question: 'Do you like it?', durationSeconds: 10, transcript: 'Yes.', comment: '' },
      ],
      submittedAt: '2026-10-05T09:00:00',
    }));
    speakingApi.getAnswerRecording.mockImplementation(() => Promise.resolve({ data: new Blob(['x']) }));
    renderAt('/speaking/result/32');

    expect(await screen.findByText('Your answers')).toBeInTheDocument();
    expect(screen.getByText('Do you like it?')).toBeInTheDocument();
    expect(screen.getByText('Hue.')).toBeInTheDocument();
    expect(screen.getByText('Say more about it.')).toBeInTheDocument();
    expect(await screen.findByLabelText('Your answer to question 2')).toHaveAttribute('src', 'blob:mine');
    expect(speakingApi.getAnswerRecording).toHaveBeenCalledWith('32', 1);
    expect(speakingApi.getRecording).not.toHaveBeenCalled();
  });
});
