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

  // Fetch quiz on mount
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
        setError(err.response?.data?.message || 'Failed to load quiz');
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
      setError(err.response?.data?.message || 'Failed to submit quiz');
    }
  }, [quizId, answers, isSubmitted, submitStart, setResult, setError, navigate]);

  if (loading && !quiz) {
    return <div className="loading-screen">Loading quiz...</div>;
  }

  if (error) {
    return (
      <div className="loading-screen">
        <div>
          <p style={{ color: 'var(--color-error)' }}>{error}</p>
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
      {/* Top Bar */}
      <div className="exam-topbar">
        <div className="exam-topbar-left">
          <span className="exam-topic-badge">{quiz.topic}</span>
          <span className="exam-diff-badge">{quiz.difficulty.replace('_', ' ')}</span>
        </div>
        <CountdownTimer onTimeUp={handleSubmit} />
        <div className="exam-topbar-right">
          <span className="exam-progress">{answeredCount}/{totalQuestions} answered</span>
          <button
            className="btn btn-primary btn-submit-exam"
            onClick={handleSubmit}
            disabled={isSubmitted || loading}
            id="submit-exam-btn"
          >
            {loading ? 'Submitting...' : 'Submit'}
          </button>
        </div>
      </div>

      {/* Split Screen */}
      <div className="exam-split">
        <div className="exam-left">
          <PassageViewer passage={quiz.passageText} />
        </div>
        <div className="exam-divider" />
        <div className="exam-right">
          <QuestionPanel questions={quiz.questions} />
        </div>
      </div>
    </div>
  );
}
