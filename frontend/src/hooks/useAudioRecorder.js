import { useCallback, useEffect, useRef, useState } from 'react';

// Opus at 32 kbps: two minutes is about 0.5 MB, well inside the 5 MB upload limit.
const BITS_PER_SECOND = 32000;
const PREFERRED_TYPES = ['audio/webm;codecs=opus', 'audio/ogg;codecs=opus', 'audio/webm', 'audio/mp4'];

export const recordingSupported = () =>
  typeof window !== 'undefined' && typeof window.MediaRecorder !== 'undefined'
  && !!navigator.mediaDevices?.getUserMedia;

const pickMimeType = () => PREFERRED_TYPES.find(
  (type) => typeof window.MediaRecorder.isTypeSupported !== 'function' || window.MediaRecorder.isTypeSupported(type),
);

/**
 * Records from the microphone with MediaRecorder.
 *
 * status: 'idle' | 'requesting' | 'recording' | 'stopped' | 'error'. `elapsed` counts whole
 * seconds while recording; `maxSeconds` stops the recorder by itself. The blob and its
 * duration are available once status is 'stopped'.
 */
export default function useAudioRecorder(maxSeconds) {
  const [status, setStatus] = useState('idle');
  const [elapsed, setElapsed] = useState(0);
  const [blob, setBlob] = useState(null);
  const [duration, setDuration] = useState(0);
  const [error, setError] = useState(null);

  const recorderRef = useRef(null);
  const streamRef = useRef(null);
  const chunksRef = useRef([]);
  const startedAtRef = useRef(0);
  const timerRef = useRef(null);

  const releaseMic = () => {
    streamRef.current?.getTracks().forEach((track) => track.stop());
    streamRef.current = null;
  };

  const stop = useCallback(() => {
    clearInterval(timerRef.current);
    if (recorderRef.current && recorderRef.current.state !== 'inactive') {
      recorderRef.current.stop();
    }
  }, []);

  const start = useCallback(async () => {
    if (!recordingSupported()) {
      setError('This browser cannot record audio. Use a recent Chrome, Edge, Firefox or Safari.');
      setStatus('error');
      return;
    }
    setError(null);
    setBlob(null);
    setElapsed(0);
    setStatus('requesting');
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      streamRef.current = stream;
      const mimeType = pickMimeType();
      const recorder = new window.MediaRecorder(stream, {
        ...(mimeType ? { mimeType } : {}),
        audioBitsPerSecond: BITS_PER_SECOND,
      });
      chunksRef.current = [];
      recorder.ondataavailable = (event) => {
        if (event.data && event.data.size > 0) chunksRef.current.push(event.data);
      };
      recorder.onstop = () => {
        const seconds = (Date.now() - startedAtRef.current) / 1000;
        setDuration(seconds);
        setBlob(new Blob(chunksRef.current, { type: (recorder.mimeType || mimeType || 'audio/webm').split(';')[0] }));
        setStatus('stopped');
        releaseMic();
      };
      recorderRef.current = recorder;
      recorder.start(1000);
      startedAtRef.current = Date.now();
      setStatus('recording');
      timerRef.current = setInterval(() => {
        const seconds = Math.floor((Date.now() - startedAtRef.current) / 1000);
        setElapsed(seconds);
        if (maxSeconds && seconds >= maxSeconds) stop();
      }, 250);
    } catch (err) {
      releaseMic();
      setError(err?.name === 'NotAllowedError'
        ? 'Microphone access was blocked. Allow the microphone for this site in the browser bar, then try again.'
        : 'No microphone could be opened. Check that one is connected and not in use by another app.');
      setStatus('error');
    }
  }, [maxSeconds, stop]);

  const reset = useCallback(() => {
    stop();
    setBlob(null);
    setElapsed(0);
    setDuration(0);
    setError(null);
    setStatus('idle');
  }, [stop]);

  // Leaving the page mid-recording must not keep the microphone open.
  useEffect(() => () => {
    clearInterval(timerRef.current);
    if (recorderRef.current && recorderRef.current.state !== 'inactive') {
      recorderRef.current.onstop = null;
      recorderRef.current.stop();
    }
    releaseMic();
  }, []);

  return { status, elapsed, blob, duration, error, start, stop, reset };
}
