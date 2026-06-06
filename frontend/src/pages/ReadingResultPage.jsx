import { useEffect, useState, useMemo } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useReading } from '../context/ReadingContext';
import readingApi from '../api/readingApi';
import analyticsApi from '../api/analyticsApi';
import AiVocabularyButton from '../components/vocab/AiVocabularyButton';

export default function ReadingResultPage() {
  const { quizId } = useParams();
  const navigate = useNavigate();
  const { result, setResult } = useReading();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [weakness, setWeakness] = useState(null);

  useEffect(() => {
    if (!result || String(result.quizId) !== String(quizId)) {
      const fetchResult = async () => {
        setLoading(true);
        try {
          const res = await readingApi.getResult(quizId);
          setResult(res.data.data);
        } catch (err) {
          if (err.status === 400) {
            navigate(`/reading/exam/${quizId}`, { replace: true });
            return;
          }
          setError(err.response?.data?.message || err.message || 'Unable to load result');
        } finally {
          setLoading(false);
        }
      };
      fetchResult();
    }
  }, [quizId]);

  useEffect(() => {
    analyticsApi.getWeakness('READING')
      .then(res => setWeakness(res.data?.data))
      .catch(() => {});
  }, [result]);

  if (loading) {
    return <div className="loading-screen">Loading result...</div>;
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
            <h1>Quiz Completed!</h1>
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

        {/* AI Weakness & Recommendation Card */}
        {weakness && weakness.weakestType && (
          <div className="ai-weakness-card" style={{
            background: 'linear-gradient(135deg, var(--surface-container-low) 0%, var(--surface-container-high) 100%)',
            border: '1px solid var(--outline-variant)',
            borderRadius: 'var(--radius-lg)',
            padding: '1.5rem',
            margin: '1.5rem 0',
            boxShadow: 'var(--shadow-sm)'
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', color: 'var(--primary)', marginBottom: '12px' }}>
              <span className="material-symbols-outlined" style={{ fontSize: 24, color: 'var(--primary)' }}>tips_and_updates</span>
              <h3 style={{ margin: 0, fontSize: '1.15rem', fontWeight: 700, color: 'var(--on-surface)' }}>AI Weakness Analysis</h3>
            </div>
            <p style={{ margin: '0 0 12px 0', fontSize: '0.92rem', color: 'var(--on-surface-variant)' }}>
              Based on your overall reading history, your weakest question type is <strong style={{ color: 'var(--primary)' }}>{weakness.weakestType}</strong> (Accuracy: <strong style={{ color: 'var(--error)' }}>{weakness.weakestAccuracy?.toFixed(1)}%</strong>).
            </p>
            {weakness.recommendation && (
              <div style={{
                background: 'var(--surface-container-lowest)',
                borderLeft: '4px solid var(--primary)',
                padding: '12px 16px',
                borderRadius: '0 var(--radius-md) var(--radius-md) 0',
                fontSize: '0.9rem',
                lineHeight: '1.5',
                color: 'var(--on-surface-variant)',
                fontStyle: 'italic'
              }}>
                "{weakness.recommendation}"
              </div>
            )}
          </div>
        )}

        {/* Passage Review */}
        {result.passageText && (
          <details className="result-passage-toggle">
            <summary>View Passage</summary>
            <div className="result-passage">
              {renderHighlightedPassage(result.passageText, result.questions)}
            </div>
          </details>
        )}

        {/* Question Results — grouped */}
        <ResultQuestions questions={result.questions} />

        {/* Action Buttons */}
        <div className="result-actions">
          <button className="btn btn-primary" onClick={() => navigate('/reading')} id="new-test-btn">
            Take Another Test
          </button>
          <button className="btn btn-outline" onClick={() => navigate('/reading/history')} id="view-all-history-btn">
            View Complete History
          </button>
        </div>
      </div>
      <AiVocabularyButton skillType="READING" sourceId={result.quizId} />
    </div>
  );
}

// ============================================================
// ResultQuestions — groups and renders question results
// ============================================================
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
      <h2>Review Answers</h2>
      {groups.map((group) => (
        <div key={group.groupId} className="result-question-group">
          {group.groupLabel && (
            <div className="group-label result-group-label">{group.groupLabel}</div>
          )}

          {/* Summary context with filled answers */}
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

// ============================================================
// ResultQuestionItem — single question result
// ============================================================
function ResultQuestionItem({ q, idx }) {
  const typeLabel = formatQuestionType(q.questionType);

  return (
    <div
      className={`result-question-item ${q.correct ? 'correct' : 'incorrect'}`}
      id={`result-q-${q.orderIndex || idx + 1}`}
    >
      <div className="rq-header">
        <span className="rq-number">Q{q.orderIndex || idx + 1}</span>
        <span className={`rq-status ${q.correct ? 'correct' : 'incorrect'}`}>
          {q.correct ? 'Correct' : 'Incorrect'}
        </span>
        <span className="rq-type-badge">{typeLabel}</span>
      </div>
      <p className="rq-text">{q.questionText}</p>

      {/* MCQ: Show options with highlights */}
      {q.questionType === 'MCQ' && (
        <div className="rq-options">
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
              >
                <span className="rq-option-key">{opt.key}</span>
                <span>{opt.text}</span>
              </div>
            );
          })}
          {!q.userAnswer && (
            <div className="rq-no-answer">
              <em>(Not answered)</em>
            </div>
          )}
        </div>
      )}

      {/* TFNG / YNNG */}
      {(q.questionType === 'TFNG' || q.questionType === 'YNNG') && (
        <div className="rq-tfng-answer">
          <span>Your answer: <strong className={q.correct ? 'text-success' : 'text-error'}>{q.userAnswer || '(not answered)'}</strong></span>
          {!q.correct && <span>Correct answer: <strong className="text-success">{q.correctAnswer}</strong></span>}
        </div>
      )}

      {/* Completion types */}
      {(q.questionType === 'SENTENCE_COMPLETION' || q.questionType === 'SUMMARY_COMPLETION') && (
        <div className="rq-completion-answer">
          <span>Answer: <strong className={q.correct ? 'text-success' : 'text-error'}>{q.userAnswer || '(not answered)'}</strong></span>
          {!q.correct && <span>Correct answer: <strong className="text-success">{q.correctAnswer}</strong></span>}
        </div>
      )}

      {/* Matching types */}
      {(q.questionType === 'MATCHING_HEADINGS' || q.questionType === 'MATCHING_INFORMATION' ||
        q.questionType === 'MATCHING_FEATURES' || q.questionType === 'MATCHING_SENTENCE_ENDINGS') && (
        <div className="rq-matching-answer">
          <span>You chose: <strong className={q.correct ? 'text-success' : 'text-error'}>{q.userAnswer || '(not answered)'}</strong></span>
          {!q.correct && <span>Correct answer: <strong className="text-success">{q.correctAnswer}</strong></span>}
        </div>
      )}

      {/* Evidence */}
      {q.evidenceText && (
        <div className="rq-evidence" style={{ marginTop: '0.75rem', fontSize: '0.875rem', color: 'var(--text-secondary)' }}>
          <strong>Evidence in Passage:</strong> <span style={{ fontStyle: 'italic', backgroundColor: 'var(--surface-container-highest)', padding: '2px 6px', borderRadius: '4px', borderLeft: '3px solid var(--primary-color)' }}>"{q.evidenceText}"</span>
        </div>
      )}

      {/* Explanation */}
      {q.explanation && (
        <div className="rq-explanation" style={{ whiteSpace: 'pre-line' }}>
          <strong>Explanation:</strong> {renderExplanationText(q.explanation)}
        </div>
      )}
    </div>
  );
}

// ============================================================
// ResultSummaryBlock — shows filled summary with color-coded answers
// ============================================================
function ResultSummaryBlock({ context, questions }) {
  if (!context) return null;

  const blankMap = {};
  questions.forEach((q) => {
    const match = q.questionText.match(/(\d+)/);
    if (match) blankMap[match[1]] = q;
  });

  const parts = context.split(/(___\d+___)/g);

  return (
    <div className="summary-block result-summary-block">
      <div className="summary-text">
        {parts.map((part, idx) => {
          const blankMatch = part.match(/___(\d+)___/);
          if (blankMatch) {
            const q = blankMap[blankMatch[1]];
            if (!q) return <span key={idx}>{part}</span>;
            return (
              <span key={idx} className={`summary-filled ${q.correct ? 'correct' : 'incorrect'}`}>
                {q.userAnswer || '___'}
                {!q.correct && (
                  <span className="summary-correct-hint"> [{q.correctAnswer}]</span>
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

// ============================================================
// Helper
// ============================================================
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

function renderExplanationText(text) {
  if (!text) return null;
  
  const regex = /\[ANS_(\d+)\](.*?)\[\/ANS_\1\]/gi;
  const parts = [];
  let lastIndex = 0;
  let match;

  while ((match = regex.exec(text)) !== null) {
    const qIndex = match.index;
    const qNum = match[1];
    const content = match[2];

    if (qIndex > lastIndex) {
      parts.push(text.substring(lastIndex, qIndex));
    }

    parts.push(
      <span key={qIndex} className="answer-highlight" style={{
        backgroundColor: 'rgba(251, 191, 36, 0.25)',
        borderBottom: '2px solid #f59e0b',
        padding: '0.1rem 0.3rem',
        borderRadius: '4px',
        fontWeight: '600',
        color: 'var(--text-main)',
        margin: '0 0.1rem',
        display: 'inline-flex',
        alignItems: 'center',
        gap: '4px'
      }}>
        {content}
        <span style={{
          backgroundColor: '#f59e0b',
          color: '#fff',
          fontSize: '0.7rem',
          padding: '0.05rem 0.25rem',
          borderRadius: '3px',
          fontWeight: 'bold',
          lineHeight: '1',
          marginLeft: '2px'
        }}>
          Q{qNum}
        </span>
      </span>
    );

    lastIndex = regex.lastIndex;
  }

  if (lastIndex < text.length) {
    parts.push(text.substring(lastIndex));
  }

  return parts;
}

function renderHighlightedPassage(passageText, questions) {
  if (!passageText) return null;
  if (!questions || questions.length === 0) {
    return passageText.split('\n').filter(p => p.trim()).map((para, idx) => <p key={idx}>{para}</p>);
  }

  const highlights = questions
    .filter(q => q.evidenceOffset !== null && q.evidenceLength > 0)
    .map(q => ({
      start: q.evidenceOffset,
      end: q.evidenceOffset + q.evidenceLength,
      text: q.evidenceText,
      qNum: q.orderIndex,
      correct: q.correct
    }))
    .sort((a, b) => a.start - b.start);

  const parts = [];
  let lastIndex = 0;

  for (const hl of highlights) {
    if (hl.start < lastIndex || hl.start >= passageText.length || hl.end > passageText.length) {
      continue;
    }

    if (hl.start > lastIndex) {
      parts.push(passageText.substring(lastIndex, hl.start));
    }

    parts.push({
      type: 'highlight',
      text: passageText.substring(hl.start, hl.end),
      qNum: hl.qNum,
      correct: hl.correct,
      key: `hl-${hl.qNum}-${hl.start}`
    });

    lastIndex = hl.end;
  }

  if (lastIndex < passageText.length) {
    parts.push(passageText.substring(lastIndex));
  }

  const paragraphs = [];
  let currentParagraph = [];

  for (const part of parts) {
    if (typeof part === 'string') {
      const splitText = part.split('\n');
      for (let i = 0; i < splitText.length; i++) {
        if (i > 0) {
          paragraphs.push(currentParagraph);
          currentParagraph = [];
        }
        if (splitText[i]) {
          currentParagraph.push(splitText[i]);
        }
      }
    } else {
      if (part.text.includes('\n')) {
        const splitText = part.text.split('\n');
        for (let i = 0; i < splitText.length; i++) {
          if (i > 0) {
            paragraphs.push(currentParagraph);
            currentParagraph = [];
          }
          if (splitText[i]) {
            currentParagraph.push({
              ...part,
              text: splitText[i]
            });
          }
        }
      } else {
        currentParagraph.push(part);
      }
    }
  }
  if (currentParagraph.length > 0) {
    paragraphs.push(currentParagraph);
  }

  return paragraphs.map((paraContent, idx) => {
    let isLabeled = false;
    let label = '';
    
    if (paraContent.length > 0 && typeof paraContent[0] === 'string') {
      const labelMatch = paraContent[0].match(/^([A-Z])\.\s+(.*)/s);
      if (labelMatch) {
        isLabeled = true;
        label = labelMatch[1];
        paraContent[0] = labelMatch[2];
      }
    }

    const contentElements = paraContent.map((item, itemIdx) => {
      if (typeof item === 'string') {
        return <span key={itemIdx}>{item}</span>;
      }
      return (
        <span
          key={item.key || itemIdx}
          className={`passage-evidence-highlight ${item.correct ? 'correct' : 'incorrect'}`}
          style={{
            backgroundColor: item.correct ? 'rgba(16, 185, 129, 0.15)' : 'rgba(239, 68, 68, 0.15)',
            borderBottom: item.correct ? '2px solid var(--success)' : '2px solid var(--error)',
            padding: '0.1rem 0.2rem',
            borderRadius: '2px',
            fontWeight: '550',
            display: 'inline',
            cursor: 'help'
          }}
          title={`Evidence for Question ${item.qNum}`}
        >
          {item.text}
          <sub
            style={{
              fontSize: '0.7rem',
              color: item.correct ? 'var(--success)' : 'var(--error)',
              marginLeft: '2px',
              fontWeight: 'bold',
              verticalAlign: 'sub'
            }}
          >
            Q{item.qNum}
          </sub>
        </span>
      );
    });

    if (isLabeled) {
      return (
        <div key={idx} className="passage-paragraph labeled-paragraph" style={{ display: 'flex', gap: '1rem', marginBottom: '1rem' }}>
          <span className="paragraph-label" style={{ fontWeight: 700, color: 'var(--primary-color)' }}>{label}</span>
          <p style={{ margin: 0 }}>{contentElements}</p>
        </div>
      );
    }

    return (
      <p key={idx} className="passage-paragraph" style={{ marginBottom: '1rem' }}>
        {contentElements}
      </p>
    );
  });
}
