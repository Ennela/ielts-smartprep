import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import speakingApi from '../../api/speakingApi';
import useAudioRecorder, { recordingSupported } from '../../hooks/useAudioRecorder';
import styles from '../../styles/Speaking.module.css';

const MIN_SECONDS = 3;
const clock = (seconds) => `${Math.floor(seconds / 60)}:${String(Math.max(0, seconds) % 60).padStart(2, '0')}`;

/**
 * Part 1 and Part 3, as in the test: one question at a time, answered straight away. Each
 * question shows only when the learner moves on to it, and recording starts with it. Every
 * answer can be heard and recorded again; the set is graded together at the end.
 * stage: 'intro' | 'question' | 'review' | 'grading'
 */
export default function QuestionsPractice({ prompt }) {
  const navigate = useNavigate();
  const total = prompt.questions.length;
  const recorder = useAudioRecorder(prompt.maxSpeakSeconds);
  const [stage, setStage] = useState('intro');
  const [current, setCurrent] = useState(0);
  const [answers, setAnswers] = useState(() => Array(total).fill(null));
  // Re-recording one answer from the review goes back to the review afterwards.
  const [fromReview, setFromReview] = useState(false);
  const [gradeError, setGradeError] = useState(null);
  const [gradingSeconds, setGradingSeconds] = useState(0);

  const takeUrl = useMemo(() => (recorder.blob ? URL.createObjectURL(recorder.blob) : null), [recorder.blob]);
  useEffect(() => () => { if (takeUrl) URL.revokeObjectURL(takeUrl); }, [takeUrl]);
  const answerUrls = useMemo(() => answers.map((a) => (a ? URL.createObjectURL(a.blob) : null)), [answers]);
  useEffect(() => () => answerUrls.forEach((url) => url && URL.revokeObjectURL(url)), [answerUrls]);

  useEffect(() => {
    if (stage !== 'grading') return undefined;
    setGradingSeconds(0);
    const timer = setInterval(() => setGradingSeconds((s) => s + 1), 1000);
    return () => clearInterval(timer);
  }, [stage]);

  // Leaving with answers recorded loses them; the browser asks first.
  const busy = recorder.status === 'recording' || stage === 'grading' || answers.some(Boolean);
  useEffect(() => {
    if (!busy) return undefined;
    const onBeforeUnload = (event) => { event.preventDefault(); event.returnValue = ''; };
    window.addEventListener('beforeunload', onBeforeUnload);
    return () => window.removeEventListener('beforeunload', onBeforeUnload);
  }, [busy]);

  const ask = (index, returnToReview) => {
    recorder.reset();
    setCurrent(index);
    setFromReview(returnToReview);
    setStage('question');
    recorder.start();
  };

  const keepTake = () => {
    const next = answers.map((a, i) => (i === current ? { blob: recorder.blob, duration: recorder.duration } : a));
    setAnswers(next);
    recorder.reset();
    const unanswered = next.findIndex((a) => !a);
    if (fromReview || unanswered === -1) setStage('review');
    else ask(unanswered, false);
  };

  const submit = async () => {
    setStage('grading');
    setGradeError(null);
    try {
      const res = await speakingApi.grade(prompt.promptId, answers);
      navigate(`/speaking/result/${res.data.data.submissionId}`, { replace: true });
    } catch (err) {
      setGradeError(err.response?.data?.message || err.message || 'Grading failed. Your answers are still here; try again.');
      setStage('review');
    }
  };

  const tooShort = recorder.status === 'stopped' && recorder.duration < MIN_SECONDS;
  const isLastStep = fromReview || answers.every((a, i) => a || i === current);
  const announcement = stage === 'question' && recorder.status === 'recording'
    ? `Question ${current + 1}. ${prompt.questions[current]} Recording.`
    : recorder.status === 'stopped' ? 'Recording stopped.' : '';

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

        {stage === 'intro' && (
          <>
            <p>
              The examiner will ask you {total} questions, one at a time. Each question appears when you are
              ready, and recording starts with it: answer straight away, as you would in the test.
            </p>
            <p className={styles.hint}>
              Up to {prompt.maxSpeakSeconds} seconds per answer. You can listen to each answer and record it
              again before submitting.
            </p>
            {!recordingSupported() && (
              <div className="error-msg" role="alert">This browser cannot record audio. Use a recent Chrome, Edge, Firefox or Safari.</div>
            )}
            <div className={styles.actions}>
              <button type="button" className="btn btn-primary" onClick={() => ask(0, false)} disabled={!recordingSupported()}>
                Start the questions
              </button>
            </div>
          </>
        )}

        {stage === 'question' && (
          <>
            <div className={styles.questionStep}>
              <span className={styles.clockLabel}>Question {current + 1} of {total}</span>
              <p className={styles.questionText}>{prompt.questions[current]}</p>
            </div>

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
                <button type="button" className="btn btn-primary" onClick={recorder.stop}>Stop answer</button>
              </div>
            )}

            {recorder.status === 'error' && (
              <div className="error-msg" role="alert">
                {recorder.error}{' '}
                <button type="button" className="btn btn-sm btn-outline" onClick={() => ask(current, fromReview)}>Try again</button>
              </div>
            )}

            {recorder.status === 'stopped' && (
              <>
                <audio className={styles.player} controls src={takeUrl} aria-label={`Your answer to question ${current + 1}`} />
                {tooShort && <div className="error-msg" role="alert">That answer is under {MIN_SECONDS} seconds. Record it again.</div>}
                <div className={styles.actions}>
                  <button type="button" className="btn btn-outline" onClick={() => ask(current, fromReview)}>Record again</button>
                  <button type="button" className="btn btn-primary" onClick={keepTake} disabled={tooShort}>
                    {isLastStep ? 'Review answers' : 'Next question'}
                  </button>
                </div>
              </>
            )}
          </>
        )}

        {(stage === 'review' || stage === 'grading') && (
          <>
            <ol className={styles.answerList}>
              {prompt.questions.map((question, i) => (
                <li key={question} className={styles.answerItem}>
                  <p className={styles.answerQuestion}>{question}</p>
                  {answers[i] && (
                    <>
                      <audio className={styles.player} controls src={answerUrls[i]} aria-label={`Your answer to question ${i + 1}`} />
                      <div className={styles.answerMeta}>
                        <span className={styles.hint}>{clock(Math.round(answers[i].duration))}</span>
                        {stage === 'review' && (
                          <button type="button" className="btn btn-sm btn-outline" onClick={() => ask(i, true)}>Record again</button>
                        )}
                      </div>
                    </>
                  )}
                </li>
              ))}
            </ol>
            {gradeError && <div className="error-msg" role="alert">{gradeError}</div>}
            {stage === 'review' ? (
              <div className={styles.actions}>
                <button type="button" className="btn btn-primary" onClick={submit}>Submit for grading</button>
              </div>
            ) : (
              <div className={styles.statusLine} role="status">
                <span className="spinner" aria-hidden="true" />
                The examiner is listening to your answers… {gradingSeconds}s (usually under a minute)
              </div>
            )}
          </>
        )}
      </section>
    </div>
  );
}
