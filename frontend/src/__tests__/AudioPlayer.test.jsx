import '@testing-library/jest-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import AudioPlayer from '../components/listening/AudioPlayer';

/*
 * A source that cannot be fetched (404, CSP, storage down) used to leave the player at
 * 0:00 with no message, so the candidate sat an exam without audio and nobody knew.
 */

describe('AudioPlayer load failures', () => {
  let load;
  let play;

  beforeEach(() => {
    load = vi.spyOn(HTMLMediaElement.prototype, 'load').mockImplementation(() => {});
    play = vi.spyOn(HTMLMediaElement.prototype, 'play').mockResolvedValue();
  });

  it('reports a media error and reloads the source on request', () => {
    const { container } = render(<AudioPlayer src="/api/v1/listening/audio/3.mp3" />);
    const audio = container.querySelector('audio');

    expect(screen.queryByRole('alert')).not.toBeInTheDocument();

    fireEvent(audio, new Event('error'));

    expect(screen.getByRole('alert')).toHaveTextContent('Audio could not be loaded.');

    fireEvent.click(screen.getByText('Reload audio'));

    expect(load).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('does not spend the mock test single listen on a play() that failed', async () => {
    play.mockRejectedValueOnce(Object.assign(new Error('no source'), { name: 'NotSupportedError' }));
    render(<AudioPlayer src="/api/v1/listening/audio/3.mp3" mode="mock-test" />);
    const button = screen.getByRole('button', { name: '' });

    fireEvent.click(button);

    expect(await screen.findByRole('alert')).toBeInTheDocument();
    // The lock icon means the one permitted listen is gone; the play button must still be live.
    expect(button).not.toBeDisabled();
  });

  it('ignores the AbortError a pause() right after play() produces', async () => {
    play.mockRejectedValueOnce(Object.assign(new Error('interrupted'), { name: 'AbortError' }));
    render(<AudioPlayer src="/api/v1/listening/audio/3.mp3" />);

    fireEvent.click(screen.getByRole('button', { name: '' }));
    await Promise.resolve();

    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });
});
