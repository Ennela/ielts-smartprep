import { useState, useEffect, useCallback, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import readingApi from '../api/readingApi';
import { ReadingContext } from '../context/ReadingContext';
import PassageViewer from '../components/reading/PassageViewer';
import QuestionPanel from '../components/reading/QuestionPanel';

export default function ReadingFullExamPage() {
  const navigate = useNavigate();
  const [quizzes, setQuizzes] = useState([]);
  const [activeIdx, setActiveIdx] = useState(0);
  const [answers, setAnswers] = useState({});
  const [timeLeft, setTimeLeft] = useState(3600); // 60 minutes
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  const timerRef = useRef(null);

  useEffect(() => {
    const query = new URLSearchParams(window.location.search);
    const quizIds = query.get('quizIds')?.split(',').map(Number) || [];

    if (quizIds.length === 0) {
      setError('No tests selected');
      setLoading(false);
      return;
    }

    const fetchQuizzes = async () => {
      try {
        const responses = await Promise.all(quizIds.map(id => readingApi.getQuiz(id)));
        const data = responses.map(r => r.data.data);
        setQuizzes(data);
      } catch (err) {
        setError('Failed to load full test passages.');
      } finally {
        setLoading(false);
      }
    };

    fetchQuizzes();
  }, []);

  // Tick countdown timer
  useEffect(() => {
    if (loading || submitting || error || quizzes.length === 0) return;

    timerRef.current = setInterval(() => {
      setTimeLeft(prev => {
        if (prev <= 1) {
          clearInterval(timerRef.current);
          handleAutoSubmit();
          return 0;
        }
        return prev - 1;
      });
    }, 1000);

    return () => clearInterval(timerRef.current);
  }, [loading, submitting, error, quizzes]);

  const handleSetAnswer = useCallback((questionId, answer) => {
    setAnswers(prev => ({ ...prev, [questionId]: answer }));
  }, []);

  const handleSubmit = async () => {
    if (submitting) return;
    setSubmitting(true);
    clearInterval(timerRef.current);

    try {
      const quizIds = quizzes.map(q => q.quizId);
      const res = await readingApi.submitFullQuiz(quizIds, answers);
      navigate('/reading/full-result', { state: { result: res.data.data }, replace: true });
    } catch (err) {
      setError(err.response?.data?.message || 'Submission failed');
      setSubmitting(false);
    }
  };

  const handleAutoSubmit = () => {
    handleSubmit();
  };

  const formatTime = (seconds) => {
    const m = Math.floor(seconds / 60).toString().padStart(2, '0');
    const s = (seconds % 60).toString().padStart(2, '0');
    return `${m}:${s}`;
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
            padding: '6px 12px', background: 'var(--surface-container-high)',
            borderRadius: 'var(--radius-md)', color: timeLeft < 300 ? 'var(--error)' : 'var(--on-surface)',
            fontWeight: 700, fontSize: '1.1rem'
          }}>
            <span className="material-symbols-outlined" style={{ fontSize: 20 }}>timer</span>
            {formatTime(timeLeft)}
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
