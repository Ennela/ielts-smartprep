import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useReading } from '../context/ReadingContext';
import readingApi from '../api/readingApi';

export default function ReadingResultPage() {
  const { quizId } = useParams();
  const navigate = useNavigate();
  const { result, setResult } = useReading();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    // If result is not in context (e.g. page refresh), fetch from quiz endpoint
    if (!result || String(result.quizId) !== String(quizId)) {
      const fetchResult = async () => {
        setLoading(true);
        try {
          // Re-fetch quiz, if submitted it returns full result
          const res = await readingApi.getQuiz(quizId);
          const quizData = res.data.data;
          if (!quizData.submitted) {
            navigate(`/reading/exam/${quizId}`, { replace: true });
            return;
          }
          // Need to re-submit to get results (or we load from existing result)
          // For now, we use the context result or show a simplified view
          setResult(quizData);
        } catch (err) {
          setError(err.response?.data?.message || 'Failed to load results');
        } finally {
          setLoading(false);
        }
      };
      fetchResult();
    }
  }, [quizId]);

  if (loading) {
    return <div className="loading-screen">Loading results...</div>;
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

  if (!result) return null;

  const percentage = result.totalQuestions > 0
    ? Math.round((result.correctAnswers / result.totalQuestions) * 100)
    : 0;

  return (
    <div className="reading-result-page" id="reading-result-page">
      <div className="result-content">
        {/* Score Card */}
        <div className="result-score-card" id="result-score-card">
          <div className="score-ring-container">
            <svg className="score-ring" viewBox="0 0 120 120">
              <circle className="score-ring-bg" cx="60" cy="60" r="50" />
              <circle
                className="score-ring-fill"
                cx="60" cy="60" r="50"
                strokeDasharray={`${(percentage / 100) * 314} 314`}
              />
            </svg>
            <div className="score-ring-text">
              <span className="score-band">{result.bandScore}</span>
              <span className="score-label">Band</span>
            </div>
          </div>
          <div className="score-details">
            <h1>Test Complete!</h1>
            <div className="score-stats">
              <div className="score-stat">
                <span className="stat-value">{result.correctAnswers}</span>
                <span className="stat-label">Correct</span>
              </div>
              <div className="score-stat">
                <span className="stat-value">{result.totalQuestions}</span>
                <span className="stat-label">Total</span>
              </div>
              <div className="score-stat">
                <span className="stat-value">{percentage}%</span>
                <span className="stat-label">Accuracy</span>
              </div>
            </div>
            <div className="score-meta">
              <span className="meta-badge">{result.topic}</span>
              <span className="meta-badge">{result.difficulty?.replace('_', ' ')}</span>
            </div>
          </div>
        </div>

        {/* Passage Review */}
        {result.passageText && (
          <details className="result-passage-toggle">
            <summary>View Passage</summary>
            <div className="result-passage">
              {result.passageText.split('\n').filter(p => p.trim()).map((para, idx) => (
                <p key={idx}>{para}</p>
              ))}
            </div>
          </details>
        )}

        {/* Question Results */}
        <div className="result-questions">
          <h2>Answer Review</h2>
          {result.questions?.map((q, idx) => (
            <div
              key={q.questionId || idx}
              className={`result-question-item ${q.correct ? 'correct' : 'incorrect'}`}
              id={`result-q-${idx + 1}`}
            >
              <div className="rq-header">
                <span className="rq-number">Q{q.orderIndex || idx + 1}</span>
                <span className={`rq-status ${q.correct ? 'correct' : 'incorrect'}`}>
                  {q.correct ? 'Correct' : 'Incorrect'}
                </span>
                <span className="rq-type-badge">{q.questionType}</span>
              </div>
              <p className="rq-text">{q.questionText}</p>

              {/* Show options for MCQ */}
              {q.questionType === 'MCQ' && (
                <div className="rq-options">
                  {[
                    { key: 'A', text: q.optionA },
                    { key: 'B', text: q.optionB },
                    { key: 'C', text: q.optionC },
                    { key: 'D', text: q.optionD },
                  ].filter(o => o.text).map(opt => (
                    <div
                      key={opt.key}
                      className={`rq-option
                        ${opt.key === q.correctAnswer ? 'correct-answer' : ''}
                        ${opt.key === q.userAnswer && !q.correct ? 'wrong-answer' : ''}
                      `}
                    >
                      <span className="rq-option-key">{opt.key}</span>
                      <span>{opt.text}</span>
                    </div>
                  ))}
                </div>
              )}

              {/* Show answers for TFNG */}
              {q.questionType === 'TFNG' && (
                <div className="rq-tfng-answer">
                  <span>Your answer: <strong className={q.correct ? 'text-success' : 'text-error'}>{q.userAnswer || '(no answer)'}</strong></span>
                  {!q.correct && <span>Correct answer: <strong className="text-success">{q.correctAnswer}</strong></span>}
                </div>
              )}

              {/* Explanation */}
              <div className="rq-explanation">
                <strong>Explanation:</strong> {q.explanation}
              </div>
            </div>
          ))}
        </div>

        {/* Action Buttons */}
        <div className="result-actions">
          <button className="btn btn-primary" onClick={() => navigate('/reading')} id="new-test-btn">
            Take Another Test
          </button>
          <button className="btn btn-outline" onClick={() => navigate('/reading/history')} id="view-all-history-btn">
            View All History
          </button>
        </div>
      </div>
    </div>
  );
}
