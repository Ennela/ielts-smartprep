import { useState, useEffect, useCallback, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import readingApi from '../api/readingApi';
import attemptApi from '../api/attemptApi';
import { ReadingContext } from '../context/ReadingContext';
import PassageViewer from '../components/reading/PassageViewer';
import QuestionPanel from '../components/reading/QuestionPanel';
import useExamTimer from '../hooks/useExamTimer';
import useExamWarnings from '../hooks/useExamWarnings';
import { useToast } from '../context/ToastContext';

const SESSION_KEY = 'reading_full_attemptId';

export default function ReadingFullExamPage() {
  const navigate = useNavigate();
  const { warning: triggerWarningToast } = useToast();
  const [quizzes, setQuizzes] = useState([]);
  const [activeIdx, setActiveIdx] = useState(0);
  const [answers, setAnswers] = useState({});
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  // Server-authoritative attempt state
  const [attemptId, setAttemptId] = useState(null);
  const [deadline, setDeadline] = useState(null);
  const submittingRef = useRef(false);

  // Auto-submit handler
  const handleAutoSubmit = useCallback(() => {
    if (submittingRef.current) return;
    submittingRef.current = true;
    setSubmitting(true);

    const quizIds = quizzes.map(q => q.quizId);
    const storedAttemptId = attemptId || sessionStorage.getItem(SESSION_KEY);

    readingApi.submitFullQuiz(quizIds, answers, storedAttemptId, true)
      .then(res => {
        sessionStorage.removeItem(SESSION_KEY);
        const quizIdsKey = quizIds.join(',');
        try { localStorage.removeItem(`reading_full_draft_${quizIdsKey}`); } catch (_e) { /* ignore */ }
        navigate('/reading/full-result', { state: { result: res.data.data }, replace: true });
      })
      .catch(() => {
        setError('Auto-submit failed. Please try submitting manually.');
        setSubmitting(false);
        submittingRef.current = false;
      });
  }, [quizzes, answers, attemptId, navigate]);

  // Server-authoritative timer
  const { timeLeft, isWarning, isCritical, formattedTime, stopTimer } = useExamTimer({
    deadline,
    onTimeUp: handleAutoSubmit,
    enabled: !loading && !submitting && !error && quizzes.length > 0,
  });

  // Centralized 5min/1min audio-visual warnings
  useExamWarnings({
    timeLeft,
    enabled: !loading && !submitting && !error && quizzes.length > 0,
    showWarning: triggerWarningToast,
  });

  // Fetch quizzes and start/resume attempt
  useEffect(() => {
    const query = new URLSearchParams(window.location.search);
    const quizIds = query.get('quizIds')?.split(',').map(Number) || [];

    if (quizIds.length === 0) {
      setError('No tests selected');
      setLoading(false);
      return;
    }

    const init = async () => {
      try {
        // Fetch quiz data
        const responses = await Promise.all(quizIds.map(id => readingApi.getQuiz(id)));
        const data = responses.map(r => r.data.data);
        setQuizzes(data);

        // Restore draft answers
        const quizIdsKey = quizIds.join(',');
        try {
          const savedDraft = localStorage.getItem(`reading_full_draft_${quizIdsKey}`);
          if (savedDraft) setAnswers(JSON.parse(savedDraft));
        } catch (_e) { console.error("Failed to load full draft"); }

        // Start or resume server-authoritative attempt
        const storedAttemptId = sessionStorage.getItem(SESSION_KEY);
        let attempt;

        if (storedAttemptId) {
          // Try to resume existing attempt
          try {
            const res = await attemptApi.getAttempt(storedAttemptId);
            attempt = res.data.data;
            if (attempt.status !== 'IN_PROGRESS') {
              // Attempt already completed, start fresh
              attempt = null;
            }
          } catch (_e) {
            // Attempt not found or expired, start fresh
            attempt = null;
          }
        }

        if (!attempt) {
          const res = await attemptApi.startAttempt({
            skillType: 'READING',
            examReferenceIds: JSON.stringify(quizIds),
          });
          attempt = res.data.data;
        }

        setAttemptId(attempt.attemptId);
        setDeadline(attempt.deadline);
        sessionStorage.setItem(SESSION_KEY, String(attempt.attemptId));
      } catch (_err) {
        setError('Failed to load full test passages.');
      } finally {
        setLoading(false);
      }
    };

    init();
  }, []);

  const handleSetAnswer = useCallback((questionId, answer) => {
    setAnswers(prev => {
      const newAnswers = { ...prev, [questionId]: answer };
      const query = new URLSearchParams(window.location.search);
      const quizIdsKey = query.get('quizIds') || '';
      try {
        localStorage.setItem(`reading_full_draft_${quizIdsKey}`, JSON.stringify(newAnswers));
      } catch (_e) { console.error("Failed to save draft"); }
      return newAnswers;
    });
  }, []);

  const handleSubmit = async () => {
    if (submitting || submittingRef.current) return;
    submittingRef.current = true;
    setSubmitting(true);
    stopTimer();

    try {
      const quizIds = quizzes.map(q => q.quizId);
      const res = await readingApi.submitFullQuiz(quizIds, answers, attemptId, false);
      sessionStorage.removeItem(SESSION_KEY);
      const quizIdsKey = quizIds.join(',');
      try { localStorage.removeItem(`reading_full_draft_${quizIdsKey}`); } catch (_e) { /* ignore */ }
      navigate('/reading/full-result', { state: { result: res.data.data }, replace: true });
    } catch (err) {
      setError(err.response?.data?.message || 'Submission failed');
      setSubmitting(false);
      submittingRef.current = false;
    }
  };

  if (loading) return <div className="loading-screen"><span className="spinner" style={{ width: 24, height: 24 }} />Loading mock test...</div>;

  if (error) {
    return (
      <div className="loading-screen">
        <div>
          <p style={{ color: 'var(--error)' }}>{error}</p>
          <button className="btn btn-primary" onClick={() => navigate('/reading')} style={{ marginTop: 16 }}>
            Go Back
          </button>
        </div>
      </div>
    );
  }

  if (quizzes.length === 0) return null;

  const activeQuiz = quizzes[activeIdx];
  const totalQuestions = quizzes.reduce((sum, q) => sum + (q.questions?.length || 0), 0);
  const answeredCount = Object.keys(answers).length;

  // Timer color logic
  const timerColor = isCritical ? 'var(--error)' : isWarning ? 'var(--error)' : 'var(--on-surface)';
  const timerBg = isCritical ? 'rgba(186,26,26,0.12)' : 'var(--surface-container-high)';

  return (
    <div className="reading-exam-page" id="reading-exam-page" style={{ height: '100vh', display: 'flex', flexDirection: 'column' }}>
      {/* ── Exam Header ── */}
      <header className="exam-topbar" style={{ flexShrink: 0 }}>
        <div className="exam-topbar-left">
          <span className="exam-logo">SmartPrep</span>
          <div className="exam-divider-v" />
          <span className="exam-topic-badge">Academic Reading</span>
          <span className="exam-diff-badge">Full Mock Test</span>
        </div>

        {/* Center Tabs to toggle Passages */}
        <div className="exam-topbar-center" style={{ display: 'flex', gap: 8, margin: '0 auto' }}>
          {quizzes.map((q, idx) => (
            <button
              key={q.quizId}
              onClick={() => setActiveIdx(idx)}
              className={`btn ${activeIdx === idx ? 'btn-primary' : 'btn-outline'}`}
              style={{ padding: '6px 16px', borderRadius: 'var(--radius-md)', fontSize: '0.875rem' }}
            >
              Passage {idx + 1}
            </button>
          ))}
        </div>

        <div className="exam-topbar-right">
          <div className="exam-timer" style={{
            display: 'flex', alignItems: 'center', gap: 8,
            padding: '6px 12px', background: timerBg,
            borderRadius: 'var(--radius-md)', color: timerColor,
            fontWeight: 700, fontSize: '1.1rem',
            border: isCritical ? '1px solid var(--error)' : 'none',
            animation: isCritical ? 'pulse 1s ease-in-out infinite' : 'none',
          }}>
            <span className="material-symbols-outlined" style={{ fontSize: 20 }}>timer</span>
            {formattedTime}
          </div>
          <button
            className="btn btn-primary btn-submit-exam"
            onClick={handleSubmit}
            disabled={submitting}
            id="submit-exam-btn"
          >
            {submitting ? 'Submitting...' : 'Submit Exam'}
          </button>
        </div>
      </header>

      {/* ── Split Screen passage content ── */}
      <div className="exam-split" style={{ flex: 1, overflow: 'hidden' }}>
        <div className="exam-left" style={{ height: '100%', overflowY: 'auto' }}>
          <PassageViewer passage={activeQuiz.passageText} />
        </div>
        <div className="exam-right" style={{ height: '100%', overflowY: 'auto' }}>
          <ReadingContext.Provider value={{ answers, setAnswer: handleSetAnswer, isSubmitted: false }}>
            <QuestionPanel questions={activeQuiz.questions} />
          </ReadingContext.Provider>
        </div>
      </div>

      {/* ── Bottom Action Bar ── */}
      <div className="exam-action-bar" style={{ flexShrink: 0 }}>
        <div className="exam-action-bar-left">
          <span className="material-symbols-outlined" style={{ fontSize: 18, color: 'var(--secondary)' }}>check_circle</span>
          <span>Answered <strong>{answeredCount}</strong> / {totalQuestions} questions</span>
        </div>
        <div className="exam-action-bar-right">
          <button className="btn btn-outline" onClick={() => navigate('/reading')}>
            Exit Practice
          </button>
          <button
            className="btn btn-primary btn-submit-exam"
            onClick={handleSubmit}
            disabled={submitting}
          >
            {submitting ? 'Submitting...' : 'Complete & Submit'}
          </button>
        </div>
      </div>
    </div>
  );
}
