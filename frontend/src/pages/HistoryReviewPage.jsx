import { useEffect, useState, useMemo } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import statsApi from '../api/statsApi';

export default function HistoryReviewPage() {
  const { historyId } = useParams();
  const navigate = useNavigate();
  const [detail, setDetail] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [explaining, setExplaining] = useState({}); // { [answerId]: true/false }

  useEffect(() => {
    const fetchDetail = async () => {
      try {
        const res = await statsApi.getHistoryDetail(historyId);
        setDetail(res.data.data);
      } catch (err) {
        setError(err.message || 'Unable to load review details');
      } finally {
        setLoading(false);
      }
    };
    fetchDetail();
  }, [historyId]);

  const handleExplain = async (answerId) => {
    setExplaining(prev => ({ ...prev, [answerId]: true }));
    try {
      const res = await statsApi.explainAnswer(historyId, answerId);
      const updatedAnswer = res.data.data;
      setDetail(prev => ({
        ...prev,
        answers: prev.answers.map(a =>
          a.answerId === answerId ? { ...a, explanation: updatedAnswer.explanation } : a
        )
      }));
    } catch (err) {
      console.error('Failed to generate explanation:', err);
    } finally {
      setExplaining(prev => ({ ...prev, [answerId]: false }));
    }
  };

  if (loading) {
    return (
      <div className="loading-screen">
        <div className="review-loading-spinner">
          <div className="spinner-ring" />
          <p>Loading review...</p>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="loading-screen">
        <div>
          <p style={{ color: 'var(--error)' }}>{error}</p>
          <button className="btn btn-primary" onClick={() => navigate(-1)} style={{ marginTop: 16 }}>
            Go Back
          </button>
        </div>
      </div>
    );
  }

  if (!detail || !detail.answers || detail.answers.length === 0) {
    return (
      <div className="loading-screen">
        <div style={{ textAlign: 'center' }}>
          <svg viewBox="0 0 24 24" width="64" height="64" fill="none" stroke="var(--text-secondary)" strokeWidth="1.5">
            <path d="M9 12h6m-3-3v6m-7 4h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
          </svg>
          <p style={{ color: 'var(--text-secondary)', marginTop: '1rem' }}>
            No detailed answers recorded for this test.
          </p>
          <p style={{ color: 'var(--text-secondary)', fontSize: '0.875rem' }}>
            Detailed review is available for tests submitted after this feature was enabled.
          </p>
          <button className="btn btn-primary" onClick={() => navigate(-1)} style={{ marginTop: '1.5rem' }}>
            Go Back
          </button>
        </div>
      </div>
    );
  }

  const percentage = detail.totalQuestions > 0
    ? Math.round((detail.correctCount / detail.totalQuestions) * 100)
    : 0;

  const skillLabel = detail.skillType === 'READING' ? 'Reading' : detail.skillType === 'LISTENING' ? 'Listening' : detail.skillType;

  return (
    <div className="history-review-page" id="history-review-page">
      <div className="review-content">
        {/* Header */}
        <div className="review-header">
          <button className="btn-back" onClick={() => navigate(-1)} id="review-back-btn">
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M19 12H5M12 19l-7-7 7-7" />
            </svg>
            Back
          </button>
          <div className="review-header-title">
            <h1>{skillLabel} Test Review</h1>
            <p className="subtitle">
              {formatDate(detail.recordedAt)} · {detail.totalQuestions} questions
            </p>
          </div>
        </div>

        {/* Score Summary Card */}
        <div className="review-score-card" id="review-score-card">
          <div className="review-score-ring-wrap">
            <svg className="score-ring" viewBox="0 0 120 120">
              <circle className="score-ring-bg" cx="60" cy="60" r="50" />
              <circle
                className="score-ring-fill"
                cx="60" cy="60" r="50"
                strokeDasharray={`${(percentage / 100) * 314} 314`}
              />
            </svg>
            <div className="score-ring-text">
              <span className="score-band">{detail.score}</span>
              <span className="score-label">Band</span>
            </div>
          </div>
          <div className="review-score-stats">
            <div className="review-stat">
              <span className="review-stat-value">{detail.correctCount}</span>
              <span className="review-stat-label">Correct</span>
            </div>
            <div className="review-stat">
              <span className="review-stat-value">{detail.totalQuestions}</span>
              <span className="review-stat-label">Total</span>
            </div>
            <div className="review-stat">
              <span className="review-stat-value">{percentage}%</span>
              <span className="review-stat-label">Accuracy</span>
            </div>
            <div className="review-stat">
              <span className="review-stat-value skill-badge">{skillLabel}</span>
              <span className="review-stat-label">Skill</span>
            </div>
          </div>
        </div>

        {/* Quick Nav */}
        <QuickNav answers={detail.answers} />

        {/* Answer Cards */}
        <div className="review-answers-section">
          <h2 className="review-section-title">
            <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
            </svg>
            Detailed Answers
          </h2>

          {detail.answers.map((answer) => (
            <AnswerCard
              key={answer.answerId}
              answer={answer}
              onExplain={handleExplain}
              isExplaining={explaining[answer.answerId]}
            />
          ))}
        </div>

        {/* Bottom Actions */}
        <div className="review-bottom-actions">
          <button className="btn btn-primary" onClick={() => navigate(-1)} id="review-done-btn">
            ← Back to History
          </button>
        </div>
      </div>
    </div>
  );
}

/* ============================================================ */
/* Quick Nav — scrollable mini-map of correct/incorrect answers */
/* ============================================================ */
function QuickNav({ answers }) {
  return (
    <div className="review-quick-nav">
      <span className="quick-nav-label">Questions:</span>
      <div className="quick-nav-items">
        {answers.map((a) => (
          <a
            key={a.answerId}
            href={`#review-answer-${a.answerId}`}
            className={`quick-nav-dot ${a.isCorrect ? 'correct' : 'incorrect'}`}
            title={`Q${a.questionNo}: ${a.isCorrect ? 'Correct' : 'Incorrect'}`}
          >
            {a.questionNo}
          </a>
        ))}
      </div>
      <div className="quick-nav-legend">
        <span className="legend-item"><span className="legend-dot correct" /> Correct</span>
        <span className="legend-item"><span className="legend-dot incorrect" /> Incorrect</span>
      </div>
    </div>
  );
}

/* ============================================================ */
/* AnswerCard — individual answer review card                   */
/* ============================================================ */
function AnswerCard({ answer, onExplain, isExplaining }) {
  const typeLabel = formatQuestionType(answer.questionType);
  const parsedOptions = useMemo(() => {
    if (!answer.optionsJson) return null;
    try {
      return JSON.parse(answer.optionsJson);
    } catch {
      return null;
    }
  }, [answer.optionsJson]);

  return (
    <div
      className={`review-answer-card ${answer.isCorrect ? 'correct' : 'incorrect'}`}
      id={`review-answer-${answer.answerId}`}
    >
      {/* Card Header */}
      <div className="rac-header">
        <div className="rac-left">
          <span className="rac-number">Q{answer.questionNo}</span>
          <span className="rac-type-badge">{typeLabel}</span>
        </div>
        <span className={`rac-status ${answer.isCorrect ? 'correct' : 'incorrect'}`}>
          {answer.isCorrect ? (
            <>
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="3">
                <path d="M5 13l4 4L19 7" />
              </svg>
              Correct
            </>
          ) : (
            <>
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="3">
                <path d="M6 18L18 6M6 6l12 12" />
              </svg>
              Incorrect
            </>
          )}
        </span>
      </div>

      {/* Question Text */}
      <p className="rac-question-text">{answer.questionText}</p>

      {/* MCQ Options */}
      {answer.questionType === 'MCQ' && parsedOptions && (
        <div className="rac-options">
          {parsedOptions.map((opt) => {
            const isCorrectOpt = opt.label === answer.correctAnswer;
            const isUserOpt = opt.label === answer.userAnswer;
            const isWrongSelection = isUserOpt && !answer.isCorrect;
            return (
              <div
                key={opt.label}
                className={`rac-option
                  ${isCorrectOpt ? 'correct-opt' : ''}
                  ${isWrongSelection ? 'wrong-opt' : ''}
                  ${isUserOpt && answer.isCorrect ? 'correct-opt user-selected' : ''}
                `}
              >
                <span className="rac-opt-label">{opt.label}.</span>
                <span className="rac-opt-content">{opt.content}</span>
                {isCorrectOpt && (
                  <svg className="rac-opt-icon correct" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="3">
                    <path d="M5 13l4 4L19 7" />
                  </svg>
                )}
                {isWrongSelection && (
                  <svg className="rac-opt-icon wrong" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="3">
                    <path d="M6 18L18 6M6 6l12 12" />
                  </svg>
                )}
              </div>
            );
          })}
        </div>
      )}

      {/* Non-MCQ Answer Display */}
      {answer.questionType !== 'MCQ' && (
        <div className="rac-text-answer">
          <div className="rac-answer-row">
            <span className="rac-answer-label">Your answer:</span>
            <span className={`rac-answer-value ${answer.isCorrect ? 'correct' : 'incorrect'}`}>
              {answer.userAnswer || '(not answered)'}
            </span>
          </div>
          {!answer.isCorrect && (
            <div className="rac-answer-row">
              <span className="rac-answer-label">Correct answer:</span>
              <span className="rac-answer-value correct">{answer.correctAnswer}</span>
            </div>
          )}
        </div>
      )}

      {/* Explanation */}
      {answer.explanation ? (
        <div className="rac-explanation">
          <div className="rac-explanation-header">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z" />
            </svg>
            Explanation
          </div>
          <p className="rac-explanation-text">{answer.explanation}</p>
        </div>
      ) : (
        !answer.isCorrect && (
          <button
            className="btn btn-explain"
            onClick={() => onExplain(answer.answerId)}
            disabled={isExplaining}
            id={`explain-btn-${answer.answerId}`}
          >
            {isExplaining ? (
              <>
                <span className="btn-spinner" />
                Generating explanation...
              </>
            ) : (
              <>
                <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2">
                  <path d="M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z" />
                </svg>
                Explain with AI
              </>
            )}
          </button>
        )
      )}
    </div>
  );
}

/* ============================================================ */
/* Helpers                                                       */
/* ============================================================ */
function formatQuestionType(type) {
  const labels = {
    MCQ: 'MCQ',
    TFNG: 'T/F/NG',
    YNNG: 'Y/N/NG',
    FILL_BLANK: 'Fill Blank',
    SENTENCE_COMPLETION: 'Sentence Completion',
    SUMMARY_COMPLETION: 'Summary Completion',
    MATCHING_HEADINGS: 'Matching Headings',
    MATCHING_INFORMATION: 'Matching Info',
    MATCHING_FEATURES: 'Matching Features',
    MATCHING_SENTENCE_ENDINGS: 'Matching Endings',
  };
  return labels[type] || type;
}

function formatDate(dateStr) {
  if (!dateStr) return '';
  const d = new Date(dateStr);
  return d.toLocaleDateString('en-US', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' });
}
