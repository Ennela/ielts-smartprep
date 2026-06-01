import { useEffect, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useReading } from '../context/ReadingContext';
import readingApi from '../api/readingApi';
import PassageViewer from '../components/reading/PassageViewer';
import QuestionPanel from '../components/reading/QuestionPanel';
import CountdownTimer from '../components/reading/CountdownTimer';

export default function ReadingExamPage() {
  const { quizId } = useParams();
  const navigate = useNavigate();
  const { quiz, answers, loading, error, isSubmitted, setQuiz, setLoading, setError, submitStart, setResult } = useReading();

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
        setQuiz(quizData);
      } catch (err) {
        setError(err.response?.data?.message || 'Unable to load test');
      }
    };
    fetchQuiz();
  }, [quizId]);

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
