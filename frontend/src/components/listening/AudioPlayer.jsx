import { useState, useRef, useEffect, useCallback } from 'react';

export default function AudioPlayer({ src, mode = 'practice' }) {
  const audioRef = useRef(null);
  const [isPlaying, setIsPlaying] = useState(false);
  const [currentTime, setCurrentTime] = useState(0);
  const [duration, setDuration] = useState(0);
  const [volume, setVolume] = useState(0.8);
  const isMockTest = mode === 'mock-test';

  useEffect(() => {
    const audio = audioRef.current;
    if (!audio) return;
    audio.volume = volume;

    const onLoadedMetadata = () => setDuration(audio.duration || 0);
    const onTimeUpdate = () => setCurrentTime(audio.currentTime || 0);
    const onEnded = () => setIsPlaying(false);

    audio.addEventListener('loadedmetadata', onLoadedMetadata);
    audio.addEventListener('timeupdate', onTimeUpdate);
    audio.addEventListener('ended', onEnded);

    return () => {
      audio.removeEventListener('loadedmetadata', onLoadedMetadata);
      audio.removeEventListener('timeupdate', onTimeUpdate);
      audio.removeEventListener('ended', onEnded);
    };
  }, [src]);

  const togglePlay = useCallback(() => {
    const audio = audioRef.current;
    if (!audio) return;
    if (isPlaying) {
      audio.pause();
    } else {
      audio.play().catch(() => {});
    }
    setIsPlaying(!isPlaying);
  }, [isPlaying]);

  const handleSeek = useCallback((e) => {
    if (isMockTest) return; // Seeking disabled in mock test mode
    const audio = audioRef.current;
    if (!audio || !duration) return;
    const rect = e.currentTarget.getBoundingClientRect();
    const pct = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width));
    audio.currentTime = pct * duration;
  }, [isMockTest, duration]);

  const handleVolume = useCallback((e) => {
    const val = parseFloat(e.target.value);
    setVolume(val);
    if (audioRef.current) audioRef.current.volume = val;
  }, []);

  const formatTime = (t) => {
    if (!t || isNaN(t)) return '0:00';
    const m = Math.floor(t / 60);
    const s = Math.floor(t % 60);
    return `${m}:${s.toString().padStart(2, '0')}`;
  };

  const progress = duration ? (currentTime / duration) * 100 : 0;

  return (
    <div className="audio-player">
      <audio ref={audioRef} src={src} preload="metadata" />
      <button className="audio-play-btn" onClick={togglePlay} id="audio-play-btn">
        {isPlaying ? (
          <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
            <rect x="6" y="4" width="4" height="16" rx="1" />
            <rect x="14" y="4" width="4" height="16" rx="1" />
          </svg>
        ) : (
          <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
            <polygon points="5,3 19,12 5,21" />
          </svg>
        )}
      </button>

      <span className="audio-time">{formatTime(currentTime)}</span>

      <div
        className={`audio-seekbar ${isMockTest ? 'audio-seekbar-locked' : ''}`}
        onClick={handleSeek}
        title={isMockTest ? 'Seeking disabled in mock test mode' : 'Click to seek'}
      >
        <div className="audio-seekbar-fill" style={{ width: `${progress}%` }} />
        {!isMockTest && (
          <div className="audio-seekbar-thumb" style={{ left: `${progress}%` }} />
        )}
      </div>

      <span className="audio-time">{formatTime(duration)}</span>

      <div className="audio-volume">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
          <path d="M3 9v6h4l5 5V4L7 9H3z" />
          {volume > 0.5 && <path d="M16.5 12c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02z" />}
          {volume > 0 && <path d="M14 3.23v2.06c2.89.86 5 3.54 5 6.71s-2.11 5.85-5 6.71v2.06c4.01-.91 7-4.49 7-8.77s-2.99-7.86-7-8.77z" opacity={volume > 0.5 ? 1 : 0.4} />}
        </svg>
        <input
          type="range" min="0" max="1" step="0.05"
          value={volume} onChange={handleVolume}
          className="audio-volume-slider"
        />
      </div>

      {isMockTest && (
        <span className="audio-mock-badge">MOCK TEST</span>
      )}
    </div>
  );
}
