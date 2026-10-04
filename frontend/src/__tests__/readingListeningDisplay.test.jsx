import '@testing-library/jest-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import AudioPlayer from '../components/listening/AudioPlayer';
import PassageViewer from '../components/reading/PassageViewer';
import { renderTranscriptLine } from '../pages/ListeningResultPage';

describe('mock test listen-once rule', () => {
  beforeEach(() => {
    sessionStorage.clear();
    vi.spyOn(HTMLMediaElement.prototype, 'play').mockResolvedValue();
    vi.spyOn(HTMLMediaElement.prototype, 'pause').mockImplementation(() => {});
  });

  it('survives a reload, which it used to reset', async () => {
    const { unmount } = render(<AudioPlayer src="/part1.mp3" mode="mock-test" playedStorageKey="mock_played_9" />);
    fireEvent.click(screen.getByRole('button', { name: 'Play' }));
    await waitFor(() => expect(sessionStorage.getItem('mock_played_9')).toContain('/part1.mp3'));
    unmount();

    // A fresh page: same sitting, same recording.
    render(<AudioPlayer src="/part1.mp3" mode="mock-test" playedStorageKey="mock_played_9" />);
    expect(screen.getByRole('button', { name: 'Recording already played' })).toBeDisabled();
  });

  it('belongs to one sitting, so a later retake can play again', () => {
    sessionStorage.setItem('mock_played_9', JSON.stringify(['/part1.mp3']));

    render(<AudioPlayer src="/part1.mp3" mode="mock-test" playedStorageKey="mock_played_10" />);
    expect(screen.getByRole('button', { name: 'Play' })).not.toBeDisabled();
  });
});

describe('PassageViewer module badge', () => {
  it.each([
    ['ACADEMIC', 'IELTS Academic'],
    [undefined, 'IELTS Academic'],
    ['GENERAL', 'IELTS General Training'],
    ['GENERAL_TRAINING', 'IELTS General Training'],
  ])('%s shows %s', (moduleType, label) => {
    render(<PassageViewer passage="A. Some text." moduleType={moduleType} />);
    expect(screen.getByText(label)).toBeInTheDocument();
  });
});

describe('listening transcript answer markers', () => {
  it('highlights a marked answer instead of printing the tags', () => {
    render(<p>{renderTranscriptLine('The price is [ANS_3]forty[/ANS_3] pounds.')}</p>);

    const answer = screen.getByText('forty');
    expect(answer.tagName).toBe('MARK');
    expect(answer).toHaveAttribute('title', 'Answer to question 3');
    expect(document.body.textContent).not.toContain('[ANS_');
  });

  it('drops a stray tag without its partner', () => {
    render(<p>{renderTranscriptLine('Open at [ANS_1]nine and closed.')}</p>);
    expect(document.body.textContent).toBe('Open at nine and closed.');
  });

  it('leaves an ordinary line alone', () => {
    render(<p>{renderTranscriptLine('Nothing to see here.')}</p>);
    expect(document.body.textContent).toBe('Nothing to see here.');
  });
});
