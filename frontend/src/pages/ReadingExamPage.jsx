import { useState, useEffect, useCallback, useRef } from 'react';
import { useParams, useNavigate, useSearchParams } from 'react-router-dom';
import { useReading } from '../context/ReadingContext';
import { useToast } from '../context/ToastContext';
import { useAuth } from '../context/AuthContext';
import readingApi from '../api/readingApi';
import attemptApi from '../api/attemptApi';
import adminApi from '../api/adminApi';
import PassageViewer from '../components/reading/PassageViewer';
import useExamWarnings from '../hooks/useExamWarnings';
import QuestionPanel from '../components/reading/QuestionPanel';
import useExamTimer from '../hooks/useExamTimer';

const SESSION_KEY_PREFIX = 'reading_single_attemptId_';



export default function ReadingExamPage() {
  const { quizId } = useParams();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { user } = useAuth();
  const { quiz, answers, loading, error, isSubmitted, setQuiz, setLoading, setError, submitStart, setResult } = useReading();
  const { warning: triggerWarningToast } = useToast();

  const isPreviewParam = searchParams.get('preview') === 'true';
  const isPreview = isPreviewParam && user?.role === 'ADMIN';

  // Server-authoritative attempt state
  const [attemptId, setAttemptId] = useState(null);
  const [deadline, setDeadline] = useState(null);
  const submittingRef = useRef(false);

  const answersRef = useRef(answers);

  // Keep answers ref current for auto-submit
  useEffect(() => { answersRef.current = answers; }, [answers]);

  // Auto-submit handler (called by useExamTimer when deadline is reached)
  const handleAutoSubmit = useCallback(() => {
    if (isPreview || submittingRef.current || isSubmitted) return;
    submittingRef.current = true;
    submitStart();

    const sessionKey = SESSION_KEY_PREFIX + quizId;
    const storedAttemptId = attemptId || sessionStorage.getItem(sessionKey);

    // Gather current answers (prefer ref for freshest state)
    let finalAnswers = answersRef.current;
    if (!finalAnswers || Object.keys(finalAnswers).length === 0) {
      try {
        const draft = localStorage.getItem(`reading_quiz_draft_${quizId}`);
        if (draft) finalAnswers = JSON.parse(draft);
      } catch { /* ignore */ }
    }

    readingApi.submitQuiz(quizId, finalAnswers || {}, storedAttemptId ? Number(storedAttemptId) : null, true)
      .then(res => {
        sessionStorage.removeItem(sessionKey);
        try { localStorage.removeItem(`reading_quiz_draft_${quizId}`); } catch (_e) { /* ignore */ }
        setResult(res.data.data);
        navigate(`/reading/result/${quizId}`, { replace: true });
      })
      .catch(() => {
        setError('Auto-submit failed. Please try submitting manually.');
        submittingRef.current = false;
      });
  }, [attemptId, quizId, isSubmitted, submitStart, setResult, setError, navigate, isPreview]);

  // Server-authoritative countdown timer
  const { timeLeft, isWarning, isCritical, formattedTime, stopTimer } = useExamTimer({
    deadline,
    onTimeUp: handleAutoSubmit,
    enabled: !loading && !isSubmitted && !!quiz && !!deadline && !isPreview,
  });

  // Centralized 5min/1min audio-visual warnings
  useExamWarnings({
    timeLeft,
    enabled: !loading && !isSubmitted && !!quiz && !!deadline && !isPreview,
    showWarning: triggerWarningToast,
  });

  // Load quiz + start/resume server attempt
  useEffect(() => {
    const sessionKey = SESSION_KEY_PREFIX + quizId;

    const fetchQuiz = async () => {
      setLoading(true);
      try {
        let quizData;
        if (isPreview) {
          const res = await adminApi.getReadingQuizPreview(quizId);
          quizData = res.data.data;
        } else {
          const res = await readingApi.getQuiz(quizId);
          quizData = res.data.data;
          if (quizData.submitted) {
            navigate(`/reading/result/${quizId}`, { replace: true });
            return;
          }
        }

        setQuiz(quizData);

        if (isPreview) {
          // Skip attempts in preview mode
          setAttemptId(null);
          setDeadline(null);
        } else {
          // Start or resume server-authoritative attempt
          const storedAttemptId = sessionStorage.getItem(sessionKey);
          let attempt;

          if (storedAttemptId) {
            try {
              const attemptRes = await attemptApi.getAttempt(storedAttemptId);
              attempt = attemptRes.data.data;
              if (attempt.status !== 'IN_PROGRESS') attempt = null;
            } catch (_e) {
              attempt = null;
            }
          }

          if (!attempt) {
            const attemptRes = await attemptApi.startAttempt({
              skillType: 'READING',
              examReferenceIds: JSON.stringify([Number(quizId)]),
            });
            attempt = attemptRes.data.data;
          }

          setAttemptId(attempt.attemptId);
          setDeadline(attempt.deadline);
          sessionStorage.setItem(sessionKey, String(attempt.attemptId));
        }
      } catch (err) {
        setError(err.response?.data?.message || 'Unable to load test');
      }
    };
    fetchQuiz();
  }, [quizId, isPreview]);



  // Handle manual submission
  const handleSubmit = useCallback(async () => {
    if (isSubmitted || submittingRef.current) return;
    submittingRef.current = true;
    submitStart();
    stopTimer();

    const sessionKey = SESSION_KEY_PREFIX + quizId;

    try {
      const storedAttemptId = attemptId || sessionStorage.getItem(sessionKey);
      const res = await readingApi.submitQuiz(quizId, answers, storedAttemptId ? Number(storedAttemptId) : null, false);

      sessionStorage.removeItem(sessionKey);
      try { localStorage.removeItem(`reading_quiz_draft_${quizId}`); } catch (_e) { /* ignore */ }
      setResult(res.data.data);
      navigate(`/reading/result/${quizId}`, { replace: true });
    } catch (err) {
      setError(err.response?.data?.message || 'Submission failed');
      submittingRef.current = false;
    }
  }, [quizId, answers, isSubmitted, attemptId, submitStart, stopTimer, setResult, setError, navigate]);

  if (loading && !quiz) return <div className="loading-screen">Loading test...</div>;

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

  if (!quiz) return null;

  const answeredCount = Object.keys(answers).length;
  const totalQuestions = quiz.questions?.length || 5;

  // Timer visual states (matching ReadingFullExamPage style)
  const timerColor = isCritical ? 'var(--error)' : isWarning ? 'var(--error)' : 'var(--on-surface)';
  const timerBg = isCritical ? 'rgba(186,26,26,0.12)' : 'var(--surface-container-high)';

  return (
    <div className="reading-exam-page" id="reading-exam-page">
      {isPreview && (
        <div style={{
          background: '#fff9c4',
          color: '#5d4037',
          padding: '8px 24px',
          textAlign: 'center',
          fontWeight: 600,
          fontSize: '0.9rem',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          gap: '8px',
          borderBottom: '1px solid #fbc02d',
          zIndex: 1100,
          position: 'relative'
        }}>
          <span className="material-symbols-outlined" style={{ fontSize: 18, color: '#f57c00' }}>warning</span>
          <span>⚠️ PREVIEW MODE — Bạn đang xem với tư cách Admin. Bài làm sẽ không được lưu.</span>
        </div>
      )}

      {/* ── Exam Header ── */}
      <header className="exam-topbar">
        {/* Left: Logo + divider + badges */}
        <div className="exam-topbar-left">
          <span className="exam-logo">SmartPrep</span>
          <div className="exam-divider-v" />
          <span className="exam-topic-badge">{quiz.topic}</span>
          <span className="exam-diff-badge">{quiz.difficulty?.replace('_', ' ')}</span>
        </div>

        {/* Center: Exam title */}
        <div className="exam-topbar-center">
          <h1>Academic Reading</h1>
          <p>Passage 1 of 1</p>
        </div>

        {/* Right: Timer + help + submit */}
        <div className="exam-topbar-right">
          {/* Server-authoritative countdown timer */}
          {deadline && !isPreview && (
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
          )}
          <button className="btn-exam-help">
            <span className="material-symbols-outlined" style={{ fontSize: 18 }}>help_outline</span>
            Help
          </button>
          <button
            className="btn btn-primary btn-submit-exam"
            onClick={handleSubmit}
            disabled={isPreview || isSubmitted || loading}
            title={isPreview ? "Không thể nộp ở chế độ preview" : undefined}
            id="submit-exam-btn"
          >
            {loading ? 'Submitting...' : 'Submit'}
          </button>
        </div>
      </header>

      {/* ── Split Screen ── */}
      <div className="exam-split">
        <div className="exam-left">
          <PassageViewer passage={quiz.passageText} />
        </div>
        <div className="exam-right">
          <QuestionPanel questions={quiz.questions} showCorrectAnswers={isPreview} />
        </div>
      </div>

      {/* ── Bottom Action Bar ── */}
      <div className="exam-action-bar">
        <div className="exam-action-bar-left">
          <span className="material-symbols-outlined" style={{ fontSize: 18, color: 'var(--secondary)' }}>check_circle</span>
          <span>Answered <strong>{answeredCount}</strong> / {totalQuestions} questions</span>
        </div>
        <div className="exam-action-bar-right">
          <button
            className="btn btn-outline"
            onClick={() => navigate(isPreview ? '/admin/reading-quizzes' : '/reading')}
          >
            {isPreview ? '← Quay lại Admin' : 'Exit'}
          </button>
          <button
            className="btn btn-primary btn-submit-exam"
            onClick={handleSubmit}
            disabled={isPreview || isSubmitted || loading}
            title={isPreview ? "Không thể nộp ở chế độ preview" : undefined}
          >
            {loading ? 'Submitting...' : 'Complete & Submit'}
          </button>
        </div>
      </div>
    </div>
  );
}
