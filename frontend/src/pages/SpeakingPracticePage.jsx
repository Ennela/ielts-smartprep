import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import speakingApi from '../api/speakingApi';
import useAudioRecorder, { recordingSupported } from '../hooks/useAudioRecorder';
import { useConfirm } from '../context/ConfirmContext';
import styles from '../styles/Speaking.module.css';

const MIN_SECONDS = 5;
const clock = (seconds) => `${Math.floor(seconds / 60)}:${String(Math.max(0, seconds) % 60).padStart(2, '0')}`;

/**
 * One Speaking task: read it, prepare (Part 2 only), record, listen back, submit.
 * phase: 'intro' | 'prep' | 'record' (the recorder's own status says where it is) | 'grading'
 */
export default function SpeakingPracticePage() {
  const { promptId } = useParams();
  const navigate = useNavigate();
  const confirm = useConfirm();

  const promptQuery = useQuery({
    queryKey: ['speaking', 'prompts', 'all'],
    queryFn: () => speakingApi.getPrompts(),
    select: (res) => (res.data?.data || []).find((p) => String(p.promptId) === String(promptId)) || null,
  });
  const prompt = promptQuery.data;

  const recorder = useAudioRecorder(prompt?.maxSpeakSeconds || 120);
  const [phase, setPhase] = useState('intro');
  const [prepLeft, setPrepLeft] = useState(0);
  const [gradeError, setGradeError] = useState(null);
  const [gradingSeconds, setGradingSeconds] = useState(0);

  const audioUrl = useMemo(() => (recorder.blob ? URL.createObjectURL(recorder.blob) : null), [recorder.blob]);
  useEffect(() => () => { if (audioUrl) URL.revokeObjectURL(audioUrl); }, [audioUrl]);

  // Part 2 preparation countdown; recording starts by itself when it runs out.
  useEffect(() => {
    if (phase !== 'prep') return undefined;
    if (prepLeft <= 0) {
      setPhase('record');
      recorder.start();
      return undefined;
    }
    const timer = setTimeout(() => setPrepLeft((s) => s - 1), 1000);
    return () => clearTimeout(timer);
  }, [phase, prepLeft, recorder.start]);

  useEffect(() => {
    if (phase !== 'grading') return undefined;
    setGradingSeconds(0);
    const timer = setInterval(() => setGradingSeconds((s) => s + 1), 1000);
    return () => clearInterval(timer);
  }, [phase]);

  // Leaving mid-answer loses the recording; the browser asks first.
  const busy = recorder.status === 'recording' || phase === 'grading' || phase === 'prep';
  useEffect(() => {
    if (!busy) return undefined;
    const onBeforeUnload = (event) => { event.preventDefault(); event.returnValue = ''; };
    window.addEventListener('beforeunload', onBeforeUnload);
    return () => window.removeEventListener('beforeunload', onBeforeUnload);
  }, [busy]);

  const begin = () => {
    setGradeError(null);
    if (prompt.prepSeconds > 0) {
      setPrepLeft(prompt.prepSeconds);
      setPhase('prep');
    } else {
      setPhase('record');
      recorder.start();
    }
  };

  const startNow = () => {
    setPrepLeft(0);
    setPhase('record');
    recorder.start();
  };

  const recordAgain = async () => {
    const ok = await confirm({
      title: 'Record again?',
      message: 'This recording will be discarded.',
      confirmLabel: 'Record again',
    });
    if (!ok) return;
    recorder.reset();
    setGradeError(null);
    setPhase('intro');
  };

  const submit = async () => {
    setPhase('grading');
    setGradeError(null);
    try {
      const res = await speakingApi.grade(prompt.promptId, recorder.blob, recorder.duration);
      navigate(`/speaking/result/${res.data.data.submissionId}`, { replace: true });
    } catch (err) {
      setGradeError(err.response?.data?.message || err.message || 'Grading failed. Your recording is still here; try again.');
      setPhase('record');
    }
  };

  if (promptQuery.isLoading) {
    return <div className="loading-spinner"><div className="spinner" /></div>;
  }
  if (!prompt) {
    return (
      <div className={styles.page}>
        <div className="empty-state">
          <p>This question could not be found.</p>
          <Link className="btn btn-primary" to="/speaking">Back to Speaking</Link>
        </div>
      </div>
    );
  }

  const tooShort = recorder.status === 'stopped' && recorder.duration < MIN_SECONDS;
  const announcement = phase === 'prep'
    ? 'Preparation time has started.'
    : recorder.status === 'recording' ? 'Recording.' : recorder.status === 'stopped' ? 'Recording stopped.' : '';

  return (
    <div className={styles.page}>
      <Link to="/speaking" className="btn-back">
        <span aria-hidden="true" className="material-symbols-outlined" style={{ fontSize: 20 }}>arrow_back</span>
        Speaking
      </Link>
      <header className={styles.header}>
        <h1 className={styles.title}>{prompt.topic}</h1>
      </header>

      <p className="sr-only" aria-live="polite">{announcement}</p>

      <section className={styles.stage}>
        <span className={styles.partBadge}>Part {prompt.part}</span>

        {prompt.part === 2 ? (
          <div className={styles.cueCard}>
            <p className={styles.cueTask}>{prompt.questions[0]}</p>
            {prompt.cuePoints.length > 0 && (
              <>
                <p className={styles.cueLabel}>You should say:</p>
                <ul className={styles.questionList}>
                  {prompt.cuePoints.map((point) => <li key={point}>{point}</li>)}
                </ul>
              </>
            )}
          </div>
        ) : (
          <ol className={styles.questionList}>
            {prompt.questions.map((q) => <li key={q}>{q}</li>)}
          </ol>
        )}

        {phase === 'intro' && (
          <>
            <p className={styles.hint}>
              {prompt.part === 2
                ? 'You will have one minute to prepare. Recording starts when it ends, or earlier if you choose.'
                : 'Answer the questions in order, as you would to an examiner. Recording stops by itself after '
                  + `${prompt.maxSpeakSeconds / 60} minutes.`}
            </p>
            {!recordingSupported() && (
              <div className="error-msg" role="alert">This browser cannot record audio. Use a recent Chrome, Edge, Firefox or Safari.</div>
            )}
            <div className={styles.actions}>
              <button type="button" className="btn btn-primary" onClick={begin} disabled={!recordingSupported()}>
                {prompt.part === 2 ? 'Start preparation' : 'Start recording'}
              </button>
            </div>
          </>
        )}

        {phase === 'prep' && (
          <div className={styles.controls}>
            <div>
              <div className={styles.clock} aria-hidden="true">{clock(prepLeft)}</div>
              <div className={styles.clockLabel}>Preparation</div>
            </div>
            <div className={styles.progress} aria-hidden="true">
              <div className={styles.progressFill} style={{ width: `${(1 - prepLeft / prompt.prepSeconds) * 100}%` }} />
            </div>
            <button type="button" className="btn btn-primary" onClick={startNow}>Start speaking now</button>
          </div>
        )}

        {phase === 'record' && (
          <>
            {recorder.status === 'requesting' && <p className={styles.hint}>Waiting for microphone permission…</p>}

            {recorder.status === 'recording' && (
              <div className={styles.controls}>
                <div>
                  <div className={styles.statusLine}><span className={styles.recDot} aria-hidden="true" /> Recording</div>
                  <div className={styles.clock} aria-hidden="true">{clock(recorder.elapsed)}</div>
                  <div className={styles.clockLabel}>of {clock(prompt.maxSpeakSeconds)}</div>
                </div>
                <div className={styles.progress} aria-hidden="true">
                  <div className={styles.progressFill} style={{ width: `${Math.min(100, (recorder.elapsed / prompt.maxSpeakSeconds) * 100)}%` }} />
                </div>
                <button type="button" className="btn btn-primary" onClick={recorder.stop}>Stop recording</button>
              </div>
            )}

            {recorder.status === 'error' && (
              <div className="error-msg" role="alert">
                {recorder.error}{' '}
                <button type="button" className="btn btn-sm btn-outline" onClick={() => { recorder.reset(); setPhase('intro'); }}>Try again</button>
              </div>
            )}

            {recorder.status === 'stopped' && (
              <>
                <audio className={styles.player} controls src={audioUrl} aria-label="Your recording" />
                <p className={styles.hint}>Length {clock(Math.round(recorder.duration))}. Listen back, then submit it for grading or record again.</p>
                {tooShort && <div className="error-msg" role="alert">That recording is under {MIN_SECONDS} seconds, too short to grade. Record again.</div>}
                {gradeError && <div className="error-msg" role="alert">{gradeError}</div>}
                <div className={styles.actions}>
                  <button type="button" className="btn btn-outline" onClick={recordAgain}>Record again</button>
                  <button type="button" className="btn btn-primary" onClick={submit} disabled={tooShort}>Submit for grading</button>
                </div>
              </>
            )}
          </>
        )}

        {phase === 'grading' && (
          <div className={styles.statusLine} role="status">
            <span className="spinner" aria-hidden="true" />
            The examiner is listening to your answer… {gradingSeconds}s (usually under a minute)
          </div>
        )}
      </section>
    </div>
  );
}
