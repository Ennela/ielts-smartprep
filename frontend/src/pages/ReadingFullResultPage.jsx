import { useState, useMemo } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';

export default function ReadingFullResultPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const result = location.state?.result;

  const [activeTab, setActiveTab] = useState(0); // active passage tab (0, 1, or 2)

  if (!result) {
    return (
      <div className="loading-screen">
        <div>
          <p style={{ color: 'var(--error)' }}>No exam result found. Please start a new session.</p>
          <button className="btn btn-primary" onClick={() => navigate('/reading')} style={{ marginTop: 16 }}>
            Back to Reading
          </button>
        </div>
      </div>
    );
  }

  const percentage = result.totalQuestions > 0
    ? Math.round((result.totalCorrect / result.totalQuestions) * 100)
    : 0;

  // Active quiz result representation
  const activeQuizResult = result.quizResults[activeTab];

  return (
    <div className="reading-result-page" id="reading-result-page">
      <div className="result-content" style={{ maxWidth: '1200px', margin: '0 auto', padding: '2rem 1rem' }}>
        {/* Score Card */}
        <div className="result-score-card" id="result-score-card" style={{ display: 'flex', gap: '2rem', flexWrap: 'wrap' }}>
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
              <span className="score-band">{result.overallBand}</span>
              <span className="score-label">Band</span>
            </div>
          </div>
          <div className="score-details">
            <h1 style={{ margin: 0, fontSize: '2rem' }}>Mock Test Completed!</h1>
            <p className="subtitle" style={{ margin: '0.5rem 0 1.5rem', color: 'var(--text-secondary)' }}>
              Here is your overall performance report across all 3 passages.
            </p>
            <div className="score-stats" style={{ display: 'flex', gap: '2rem', marginBottom: '1.5rem' }}>
              <div className="score-stat">
                <span className="stat-value" style={{ display: 'block', fontSize: '1.75rem', fontWeight: 700 }}>{result.totalCorrect}</span>
                <span className="stat-label" style={{ color: 'var(--text-secondary)', fontSize: '0.875rem' }}>Correct Answers</span>
              </div>
              <div className="score-stat">
                <span className="stat-value" style={{ display: 'block', fontSize: '1.75rem', fontWeight: 700 }}>{result.totalQuestions}</span>
                <span className="stat-label" style={{ color: 'var(--text-secondary)', fontSize: '0.875rem' }}>Total Questions</span>
              </div>
              <div className="score-stat">
                <span className="stat-value" style={{ display: 'block', fontSize: '1.75rem', fontWeight: 700 }}>{percentage}%</span>
                <span className="stat-label" style={{ color: 'var(--text-secondary)', fontSize: '0.875rem' }}>Accuracy</span>
              </div>
            </div>
          </div>
        </div>

        {/* Tab selection to view specific passage feedback */}
        <div className="passage-tab-selector" style={{ display: 'flex', gap: '0.5rem', margin: '2rem 0 1rem', borderBottom: '1px solid var(--border-color)', paddingBottom: '0.5rem' }}>
          {result.quizResults.map((qr, idx) => (
            <button
              key={qr.quizId}
              onClick={() => setActiveTab(idx)}
              className={`tab-btn ${activeTab === idx ? 'active' : ''}`}
              style={{
                padding: '0.75rem 1.5rem',
                background: 'none',
                border: 'none',
                borderBottom: activeTab === idx ? '3px solid var(--primary-color)' : '3px solid transparent',
                color: activeTab === idx ? 'var(--primary-color)' : 'var(--text-secondary)',
                fontWeight: '600',
                cursor: 'pointer',
                transition: 'all 0.2s',
                fontSize: '1rem'
              }}
            >
              Passage {idx + 1} ({qr.correctAnswers} Correct)
            </button>
          ))}
        </div>

        {activeQuizResult && (
          <div className="passage-detail-review" style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '2rem', marginTop: '1rem' }}>
            {/* Left Column: Passage Text */}
            <div className="passage-container-column" style={{ background: 'var(--surface-container-low)', padding: '1.5rem', borderRadius: 'var(--radius-lg)', maxHeight: '600px', overflowY: 'auto' }}>
              <h3>Passage Content</h3>
              <div className="result-passage">
                {activeQuizResult.passageText?.split('\n').filter(p => p.trim()).map((para, idx) => {
                  const labelMatch = para.match(/^([A-Z])\.\s+(.*)/s);
                  if (labelMatch) {
                    return (
                      <div key={idx} className="passage-paragraph labeled-paragraph" style={{ display: 'flex', gap: '1rem', marginBottom: '1rem' }}>
                        <span className="paragraph-label" style={{ fontWeight: 700, color: 'var(--primary-color)' }}>{labelMatch[1]}</span>
                        <p style={{ margin: 0 }}>{labelMatch[2]}</p>
                      </div>
                    );
                  }
                  return <p key={idx} style={{ marginBottom: '1rem' }}>{para}</p>;
                })}
              </div>
            </div>

            {/* Right Column: Question Reviews */}
            <div className="questions-container-column" style={{ background: 'var(--surface-container-low)', padding: '1.5rem', borderRadius: 'var(--radius-lg)', maxHeight: '600px', overflowY: 'auto' }}>
              <ResultQuestions questions={activeQuizResult.questions} />
            </div>
          </div>
        )}

        {/* Action Buttons */}
        <div className="result-actions" style={{ display: 'flex', gap: '1rem', marginTop: '3rem', justifyContent: 'center' }}>
          <button className="btn btn-primary" onClick={() => navigate('/reading')} id="new-test-btn">
            Take Another Test
          </button>
          <button className="btn btn-outline" onClick={() => navigate('/reading/history')} id="view-all-history-btn">
            View Complete History
          </button>
        </div>
      </div>
    </div>
  );
}

function ResultQuestions({ questions }) {
  const groups = useMemo(() => {
    if (!questions) return [];
    const groupMap = new Map();
    questions.forEach((q) => {
      const gid = q.groupId || 0;
      if (!groupMap.has(gid)) {
        groupMap.set(gid, {
          groupId: gid,
          groupLabel: q.groupLabel || '',
          groupType: q.questionType,
          groupContext: q.groupContext || null,
          questions: [],
        });
      }
      groupMap.get(gid).questions.push(q);
    });
    return Array.from(groupMap.values());
  }, [questions]);

  return (
    <div className="result-questions">
      <h3 style={{ borderBottom: '1px solid var(--border-color)', paddingBottom: '0.5rem', marginBottom: '1rem' }}>Review Answers</h3>
      {groups.map((group) => (
        <div key={group.groupId} className="result-question-group" style={{ marginBottom: '2rem' }}>
          {group.groupLabel && (
            <div className="group-label result-group-label" style={{ fontWeight: 600, color: 'var(--text-secondary)', marginBottom: '0.5rem' }}>
              {group.groupLabel}
            </div>
          )}

          {group.groupContext && group.groupType === 'SUMMARY_COMPLETION' && (
            <ResultSummaryBlock context={group.groupContext} questions={group.questions} />
          )}

          {group.questions.map((q, idx) => (
            <ResultQuestionItem key={q.questionId || idx} q={q} idx={idx} />
          ))}
        </div>
      ))}
    </div>
  );
}

function ResultQuestionItem({ q, idx }) {
  const typeLabel = formatQuestionType(q.questionType);

  return (
    <div
      className={`result-question-item ${q.correct ? 'correct' : 'incorrect'}`}
      id={`result-q-${q.orderIndex || idx + 1}`}
      style={{
        borderLeft: q.correct ? '4px solid var(--success)' : '4px solid var(--error)',
        background: 'var(--surface-container-high)',
        padding: '1rem',
        borderRadius: 'var(--radius-md)',
        marginBottom: '1rem'
      }}
    >
      <div className="rq-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' }}>
        <span className="rq-number" style={{ fontWeight: 700 }}>Q{q.orderIndex || idx + 1}</span>
        <span className={`rq-status ${q.correct ? 'correct' : 'incorrect'}`} style={{
          color: q.correct ? 'var(--success)' : 'var(--error)',
          fontWeight: 600,
          fontSize: '0.875rem'
        }}>
          {q.correct ? 'Correct' : 'Incorrect'}
        </span>
        <span className="rq-type-badge" style={{
          fontSize: '0.75rem',
          padding: '2px 8px',
          background: 'var(--surface-container-highest)',
          borderRadius: '100px'
        }}>{typeLabel}</span>
      </div>
      <p className="rq-text" style={{ margin: '0 0 1rem' }}>{q.questionText}</p>

      {/* MCQ: Show options with highlights */}
      {q.questionType === 'MCQ' && (
        <div className="rq-options" style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
          {(q.options && q.options.length > 0
            ? q.options.map(opt => ({ key: opt.label, text: opt.content }))
            : [
                { key: 'A', text: q.optionA },
                { key: 'B', text: q.optionB },
                { key: 'C', text: q.optionC },
                { key: 'D', text: q.optionD },
              ].filter(o => o.text)
          ).map(opt => {
            const isCorrectAnswer = opt.key === q.correctAnswer;
            const isUserAnswer = opt.key === q.userAnswer;
            const isWrongSelection = isUserAnswer && !q.correct;
            return (
              <div
                key={opt.key}
                className={`rq-option
                  ${isCorrectAnswer ? 'correct-answer' : ''}
                  ${isWrongSelection ? 'wrong-answer' : ''}
                  ${isUserAnswer && q.correct ? 'correct-answer user-selected' : ''}
                `}
                style={{
                  display: 'flex', gap: '0.5rem', padding: '0.5rem', borderRadius: 'var(--radius-sm)',
                  background: isCorrectAnswer ? 'var(--success-container)' : isWrongSelection ? 'var(--error-container)' : 'transparent',
                  color: isCorrectAnswer ? 'var(--on-success-container)' : isWrongSelection ? 'var(--on-error-container)' : 'inherit'
                }}
              >
                <span className="rq-option-key" style={{ fontWeight: 700 }}>{opt.key}.</span>
                <span>{opt.text}</span>
              </div>
            );
          })}
          {!q.userAnswer && (
            <div className="rq-no-answer">
              <em style={{ color: 'var(--text-secondary)' }}>(Not answered)</em>
            </div>
          )}
        </div>
      )}

      {/* TFNG / YNNG */}
      {(q.questionType === 'TFNG' || q.questionType === 'YNNG') && (
        <div className="rq-tfng-answer" style={{ display: 'flex', flexDirection: 'column', gap: '0.25rem' }}>
          <span>Your answer: <strong style={{ color: q.correct ? 'var(--success)' : 'var(--error)' }}>{q.userAnswer || '(not answered)'}</strong></span>
          {!q.correct && <span>Correct answer: <strong style={{ color: 'var(--success)' }}>{q.correctAnswer}</strong></span>}
        </div>
      )}

      {/* Completion types */}
      {(q.questionType === 'SENTENCE_COMPLETION' || q.questionType === 'SUMMARY_COMPLETION') && (
        <div className="rq-completion-answer" style={{ display: 'flex', flexDirection: 'column', gap: '0.25rem' }}>
          <span>Answer: <strong style={{ color: q.correct ? 'var(--success)' : 'var(--error)' }}>{q.userAnswer || '(not answered)'}</strong></span>
          {!q.correct && <span>Correct answer: <strong style={{ color: 'var(--success)' }}>{q.correctAnswer}</strong></span>}
        </div>
      )}

      {/* Matching types */}
      {(q.questionType === 'MATCHING_HEADINGS' || q.questionType === 'MATCHING_INFORMATION' ||
        q.questionType === 'MATCHING_FEATURES' || q.questionType === 'MATCHING_SENTENCE_ENDINGS') && (
        <div className="rq-matching-answer" style={{ display: 'flex', flexDirection: 'column', gap: '0.25rem' }}>
          <span>You chose: <strong style={{ color: q.correct ? 'var(--success)' : 'var(--error)' }}>{q.userAnswer || '(not answered)'}</strong></span>
          {!q.correct && <span>Correct answer: <strong style={{ color: 'var(--success)' }}>{q.correctAnswer}</strong></span>}
        </div>
      )}

      {/* Explanation */}
      {q.explanation && (
        <div className="rq-explanation" style={{ marginTop: '1rem', paddingTop: '0.5rem', borderTop: '1px solid var(--border-color)', fontSize: '0.875rem' }}>
          <strong>Explanation:</strong> {q.explanation}
        </div>
      )}
    </div>
  );
}

function ResultSummaryBlock({ context, questions }) {
  if (!context) return null;

  const blankMap = {};
  questions.forEach((q) => {
    const match = q.questionText.match(/(\d+)/);
    if (match) blankMap[match[1]] = q;
  });

  const parts = context.split(/(___\d+___)/g);

  return (
    <div className="summary-block result-summary-block" style={{ padding: '1rem', background: 'var(--surface-container-high)', borderRadius: 'var(--radius-md)', marginBottom: '1.5rem' }}>
      <div className="summary-text" style={{ lineHeight: '1.6' }}>
        {parts.map((part, idx) => {
          const blankMatch = part.match(/___(\d+)___/);
          if (blankMatch) {
            const q = blankMap[blankMatch[1]];
            if (!q) return <span key={idx}>{part}</span>;
            return (
              <span
                key={idx}
                className={`summary-filled ${q.correct ? 'correct' : 'incorrect'}`}
                style={{
                  fontWeight: 700,
                  padding: '2px 6px',
                  borderRadius: 'var(--radius-sm)',
                  backgroundColor: q.correct ? 'var(--success-container)' : 'var(--error-container)',
                  color: q.correct ? 'var(--on-success-container)' : 'var(--on-error-container)',
                  margin: '0 4px',
                  display: 'inline-block'
                }}
              >
                {q.userAnswer || '___'}
                {!q.correct && (
                  <span className="summary-correct-hint" style={{ fontSize: '0.8em', opacity: 0.8 }}> [{q.correctAnswer}]</span>
                )}
              </span>
            );
          }
          return <span key={idx}>{part}</span>;
        })}
      </div>
    </div>
  );
}

function formatQuestionType(type) {
  const labels = {
    MCQ: 'MCQ',
    TFNG: 'T/F/NG',
    YNNG: 'Y/N/NG',
    SENTENCE_COMPLETION: 'Sentence Completion',
    SUMMARY_COMPLETION: 'Summary Completion',
    MATCHING_HEADINGS: 'Matching Headings',
    MATCHING_INFORMATION: 'Matching Information',
    MATCHING_FEATURES: 'Matching Features',
    MATCHING_SENTENCE_ENDINGS: 'Matching Endings',
  };
  return labels[type] || type;
}
