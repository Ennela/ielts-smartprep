import { useEffect, useCallback, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useReading } from '../context/ReadingContext';
import { useToast } from '../context/ToastContext';
import readingApi from '../api/readingApi';
import PassageViewer from '../components/reading/PassageViewer';
import QuestionPanel from '../components/reading/QuestionPanel';
import CountdownTimer from '../components/reading/CountdownTimer';

// Dynamic chimes generator using Web Audio API to prevent audio asset loading failures.
const playAlertSound = (freq = 440, duration = 0.5) => {
  try {
    const audioCtx = new (window.AudioContext || window.webkitAudioContext)();
    const oscillator = audioCtx.createOscillator();
    const gainNode = audioCtx.createGain();

    oscillator.type = 'sine';
    oscillator.frequency.value = freq;
    gainNode.gain.setValueAtTime(0.15, audioCtx.currentTime);
    gainNode.gain.exponentialRampToValueAtTime(0.001, audioCtx.currentTime + duration);

    oscillator.connect(gainNode);
    gainNode.connect(audioCtx.destination);

    oscillator.start();
    oscillator.stop(audioCtx.currentTime + duration);
  } catch (e) {
    console.error("Audio error", e);
  }
};

export default function ReadingExamPage() {
  const { quizId } = useParams();
  const navigate = useNavigate();
  const { quiz, answers, loading, error, isSubmitted, timeRemaining, setQuiz, setLoading, setError, submitStart, setResult } = useReading();
  const { warning: triggerWarningToast } = useToast();
  
  const warningPlayedRef = useRef({ fiveMin: false, oneMin: false });

  // Fallback direct submission helper
  const handleSubmitForce = async (qId, currentAnswers) => {
    submitStart();
    try {
      let finalAnswers = currentAnswers;
      if (!finalAnswers || Object.keys(finalAnswers).length === 0) {
        try {
          const draft = localStorage.getItem(`reading_quiz_draft_${qId}`);
          if (draft) finalAnswers = JSON.parse(draft);
        } catch {}
      }
      const res = await readingApi.submitQuiz(qId, finalAnswers || {});
      setResult(res.data.data);
      navigate(`/reading/result/${qId}`, { replace: true });
    } catch (err) {
      setError(err.response?.data?.message || 'Submission failed');
    }
  };

  useEffect(() => {
    const fetchQuiz = async () => {
      setLoading(true);
      try {
        const res = await readingApi.getQuiz(quizId);
        const quizData = res.data.data;
        if (quizData.submitted) {
          navigate(`/reading/result/${quizId}`, { replace: true });
          return;
        }

        // Server authoritative timer calculation
        const now = new Date();
        const created = new Date(quizData.createdAt);
        const elapsed = Math.floor((now.getTime() - created.getTime()) / 1000);
        const remaining = Math.max(0, quizData.timeLimitSeconds - elapsed);

        if (remaining <= 0) {
          // Auto-submit immediately if expired on load
          handleSubmitForce(quizData.quizId, {});
          return;
        }

        // Override timeLimitSeconds with computed remaining time
        quizData.timeLimitSeconds = remaining;
        setQuiz(quizData);
      } catch (err) {
        setError(err.response?.data?.message || 'Unable to load test');
      }
    };
    fetchQuiz();
  }, [quizId]);

  // Handle manual/automatic standard submission
  const handleSubmit = useCallback(async () => {
    if (isSubmitted) return;
    submitStart();
    try {
      const res = await readingApi.submitQuiz(quizId, answers);
      setResult(res.data.data);
      navigate(`/reading/result/${quizId}`, { replace: true });
    } catch (err) {
      setError(err.response?.data?.message || 'Submission failed');
    }
  }, [quizId, answers, isSubmitted, submitStart, setResult, setError, navigate]);

  // Audio-visual warnings at 5 minutes and 1 minute
  useEffect(() => {
    if (!quiz || isSubmitted) return;

    if (timeRemaining === 300 && !warningPlayedRef.current.fiveMin) {
      warningPlayedRef.current.fiveMin = true;
      triggerWarningToast("5 minutes remaining! Please review and finalize your answers.");
      playAlertSound(523.25, 0.15); // C5 chime
      setTimeout(() => playAlertSound(659.25, 0.3), 150); // E5 chime
    }

    if (timeRemaining === 60 && !warningPlayedRef.current.oneMin) {
      warningPlayedRef.current.oneMin = true;
      triggerWarningToast("1 minute remaining! Your exam will be submitted automatically.");
      playAlertSound(880, 0.12);
      setTimeout(() => playAlertSound(880, 0.12), 150);
      setTimeout(() => playAlertSound(880, 0.25), 300);
    }
  }, [timeRemaining, quiz, isSubmitted]);

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

  return (
    <div className="reading-exam-page" id="reading-exam-page">

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
          <CountdownTimer onTimeUp={handleSubmit} />
          <button className="btn-exam-help">
            <span className="material-symbols-outlined" style={{ fontSize: 18 }}>help_outline</span>
            Help
          </button>
          <button
            className="btn btn-primary btn-submit-exam"
            onClick={handleSubmit}
            disabled={isSubmitted || loading}
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
          <QuestionPanel questions={quiz.questions} />
        </div>
      </div>

      {/* ── Bottom Action Bar ── */}
      <div className="exam-action-bar">
        <div className="exam-action-bar-left">
          <span className="material-symbols-outlined" style={{ fontSize: 18, color: 'var(--secondary)' }}>check_circle</span>
          <span>Answered <strong>{answeredCount}</strong> / {totalQuestions} questions</span>
        </div>
        <div className="exam-action-bar-right">
          <button className="btn btn-outline" onClick={() => navigate('/reading')}>
            Exit
          </button>
          <button
            className="btn btn-primary btn-submit-exam"
            onClick={handleSubmit}
            disabled={isSubmitted || loading}
          >
            {loading ? 'Submitting...' : 'Complete & Submit'}
          </button>
        </div>
      </div>
    </div>
  );
}
