import { Fragment, useState, useEffect } from 'react';
import { useParams, useLocation, useNavigate } from 'react-router-dom';
import listeningApi from '../api/listeningApi';
import AiVocabularyButton from '../components/vocab/AiVocabularyButton';

// Transcripts mark each answer as [ANS_3]forty[/ANS_3] so grading feedback can point at
// it. This page printed those tags verbatim. After submission the answers are the point of
// the transcript, so each is highlighted and labelled with its question number instead.
const ANSWER_MARKER = /\[ANS_(\d+)\]([\s\S]*?)\[\/ANS_\1\]/gi;
const STRAY_MARKER = /\[\/?ANS_\d+\]/gi;

export function renderTranscriptLine(line) {
  const parts = [];
  let last = 0;
  for (const match of line.matchAll(ANSWER_MARKER)) {
    if (match.index > last) parts.push(line.slice(last, match.index).replace(STRAY_MARKER, ''));
    parts.push(
      <mark key={match.index} className="transcript-answer" title={`Answer to question ${match[1]}`}>
        {match[2]}
      </mark>
    );
    last = match.index + match[0].length;
  }
  if (last < line.length) parts.push(line.slice(last).replace(STRAY_MARKER, ''));
  return parts;
}

export default function ListeningResultPage() {
  const { testId } = useParams();
  const location = useLocation();
  const navigate = useNavigate();

  const [result, setResult] = useState(location.state || null);
  const [loading, setLoading] = useState(!location.state);
  const [error, setError] = useState(null);
  const [activeTab, setActiveTab] = useState('answers');
  const [vocabData, setVocabData] = useState(null);
  const [vocabLoading, setVocabLoading] = useState(false);
  const [aiAnalysis, setAiAnalysis] = useState({});
  const [aiLoading, setAiLoading] = useState({});

  // Arriving straight after a submit carries the result in router state. Arriving from the
  // history list, a refresh, or a pasted link carries nothing — previously that left the
  // page spinning forever, so fetch the result instead.
  useEffect(() => {
    if (result || !testId) return;
    let cancelled = false;
    setLoading(true);
    listeningApi.getTestResult(testId)
      .then(res => { if (!cancelled) setResult(res.data.data); })
      .catch(err => {
        if (!cancelled) setError(err.response?.data?.message || 'Could not load this result.');
      })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [testId, result]);

  if (loading) return (
    <div className="listening-page">
      <div className="loading-spinner"><div className="spinner" /></div>
    </div>
  );

  if (error || !result) return (
    <div className="listening-page">
      <div className="loading-screen">
        <div>
          <p style={{ color: 'var(--color-error)' }}>{error || 'Result not found.'}</p>
          <button className="btn btn-primary" onClick={() => navigate('/listening/history')}
                  style={{ marginTop: 16 }}>
            Back to History
          </button>
        </div>
      </div>
    </div>
  );

  const scoreColor = result.score >= 7.0 ? 'var(--color-success)' :
                     result.score >= 5.5 ? 'var(--color-warning)' : 'var(--color-error)';

  const circumference = 2 * Math.PI * 54;
  const scorePercent = (result.score / 9) * 100;
  const dashOffset = circumference - (scorePercent / 100) * circumference;

  const handleAnalyze = async (questionId) => {
    if (aiAnalysis[questionId]) return;
    setAiLoading(prev => ({ ...prev, [questionId]: true }));
    try {
      const res = await listeningApi.analyzeQuestion(questionId);
      setAiAnalysis(prev => ({ ...prev, [questionId]: res.data?.data }));
    } catch (err) {
      console.error(err);
    } finally {
      setAiLoading(prev => ({ ...prev, [questionId]: false }));
    }
  };

  const handleVocab = async (partId) => {
    if (vocabData) return;
    setVocabLoading(true);
    try {
      const res = await listeningApi.extractVocabulary(partId);
      setVocabData(res.data?.data);
    } catch (err) {
      console.error(err);
    } finally {
      setVocabLoading(false);
    }
  };

  return (
    <div className="listening-page">
      <div className="listening-result-content">
        <h1>Listening Test Results</h1>

        {/* Score Ring */}
        <div className="result-score-section">
          <div className="score-ring-container">
            {/* Not rotated as a whole, unlike the other result pages: the score is drawn
                inside this SVG, so the shared .score-ring rotation turned the number on its
                side. The progress arc turns itself to start at the top. */}
            <svg className="score-ring" width="140" height="140" viewBox="0 0 120 120" style={{ transform: 'none' }}>
              <circle cx="60" cy="60" r="54" fill="none" stroke="var(--color-border)" strokeWidth="8" />
              <circle
                cx="60" cy="60" r="54" fill="none"
                stroke={scoreColor} strokeWidth="8"
                strokeLinecap="round"
                strokeDasharray={circumference}
                strokeDashoffset={dashOffset}
                transform="rotate(-90 60 60)"
                className="score-ring-progress"
              />
              <text x="60" y="55" textAnchor="middle" fill={scoreColor} fontSize="28" fontWeight="700">
                {result.score?.toFixed ? result.score.toFixed(1) : result.score}
              </text>
              <text x="60" y="75" textAnchor="middle" fill="var(--color-text-muted)" fontSize="11">
                Band Score
              </text>
            </svg>
          </div>
          <div className="score-stats">
            <div className="stat-item">
              <span className="stat-label">Mode</span>
              <span className={`badge ${result.testMode === 'MOCK_TEST' ? 'badge-mock' : 'badge-practice'}`}>
                {result.testMode === 'MOCK_TEST' ? 'Mock Test' : 'Practice'}
              </span>
            </div>
            <div className="stat-item">
              <span className="stat-label">Correct</span>
              <span className="stat-value">{result.correctAnswers}/{result.totalQuestions}</span>
            </div>
            <div className="stat-item">
              <span className="stat-label">Accuracy</span>
              <span className="stat-value">
                {result.totalQuestions ? Math.round((result.correctAnswers / result.totalQuestions) * 100) : 0}%
              </span>
            </div>
            {result.timeSpentSeconds != null && (
              <div className="stat-item">
                <span className="stat-label">Time Spent</span>
                <span className="stat-value">
                  {Math.floor(result.timeSpentSeconds / 60)}:{(result.timeSpentSeconds % 60).toString().padStart(2, '0')}
                </span>
              </div>
            )}
          </div>
          {result.autoSubmitted && (
            <div style={{
              display: 'inline-flex', alignItems: 'center', gap: 6, marginTop: 12,
              padding: '4px 12px', borderRadius: 'var(--radius-full)',
              background: 'rgba(186,26,26,0.08)', color: 'var(--error)',
              fontSize: '0.8rem', fontWeight: 600,
            }}>
              <span aria-hidden="true" className="material-symbols-outlined" style={{ fontSize: 16 }}>timer_off</span>
              Auto-submitted (time expired)
            </div>
          )}
        </div>

        {/* Tabs */}
        <div className="result-tabs">
          <button className={`tab-btn ${activeTab === 'answers' ? 'active' : ''}`}
            onClick={() => setActiveTab('answers')}>Review Answers</button>
          <button className={`tab-btn ${activeTab === 'transcript' ? 'active' : ''}`}
            onClick={() => setActiveTab('transcript')}>Transcript</button>
          <button className={`tab-btn ${activeTab === 'vocabulary' ? 'active' : ''}`}
            onClick={() => {
              setActiveTab('vocabulary');
              if (result.parts?.[0]) handleVocab(result.parts[0].partId);
            }}>AI Vocabulary</button>
        </div>

        {/* Answer Review Tab */}
        {activeTab === 'answers' && (
          <div className="result-answers">
            {result.parts?.map(part => (
              <div key={part.partId} className="result-part-section">
                <h3>Part {part.partNumber}: {part.title}</h3>
                <div className="answer-grid">
                  {part.questions?.sort((a,b) => a.orderIndex - b.orderIndex).map((q, i, sorted) => (
                    <Fragment key={q.questionId}>
                    {/* A grouped part (V54) states its instructions once, above the group. */}
                    {q.groupId != null && (i === 0 || sorted[i - 1].groupId !== q.groupId) && (() => {
                      const image = sorted.find(x => x.groupId === q.groupId && x.imageUrl)?.imageUrl;
                      if (!q.groupLabel && !image) return null;
                      return (
                        <div style={{ gridColumn: '1 / -1' }}>
                          {q.groupLabel && <div className="group-label">{q.groupLabel}</div>}
                          {image && <img src={image} alt={q.groupLabel || 'Diagram for these questions'} className="prompt-image" style={{ maxWidth: '100%', marginTop: 8 }} />}
                        </div>
                      );
                    })()}
                    <div className={`answer-card ${q.isCorrect ? 'correct' : 'wrong'}`}>
                      <div className="answer-card-header">
                        <span className={`answer-status ${q.isCorrect ? 'status-correct' : 'status-wrong'}`}>
                          {q.isCorrect ? '✓' : '✗'}
                        </span>
                        <span className="answer-type">{q.questionType}</span>
                      </div>
                      <p className="answer-question-text">
                        {q.questionType === 'MCQ' ? (q.options && q.options.length > 0 ? q.questionText : q.questionText.split('\n')[0]) : q.questionText}
                      </p>
                      {q.questionType === 'MCQ' && q.options && q.options.length > 0 && (
                        <div className="mcq-options-review" style={{ marginTop: 12, marginBottom: 12, display: 'flex', flexDirection: 'column', gap: 6 }}>
                          {q.options.map(opt => {
                            // A "Choose TWO" answer is the whole choice, e.g. "B,D".
                            const isUserSelected = (q.selectCount > 1
                              ? (q.userAnswer || '').split(',') : [q.userAnswer]).includes(opt.label);
                            const isCorrectOption = opt.isCorrect || q.correctAnswer === opt.label;
                            let optBg = 'transparent';
                            let optBorder = '1px solid var(--outline-variant)';
                            if (isCorrectOption) {
                              optBg = 'rgba(0, 108, 74, 0.08)';
                              optBorder = '1px solid var(--color-success)';
                            } else if (isUserSelected) {
                              optBg = 'rgba(186, 26, 26, 0.08)';
                              optBorder = '1px solid var(--error)';
                            }
                            return (
                              <div key={opt.optionId} style={{
                                padding: '6px 12px',
                                borderRadius: 'var(--radius-md)',
                                background: optBg,
                                border: optBorder,
                                display: 'flex',
                                alignItems: 'center',
                                gap: 8,
                                fontSize: '0.875rem'
                              }}>
                                <strong style={{ color: isCorrectOption ? 'var(--color-success)' : (isUserSelected ? 'var(--error)' : 'inherit') }}>
                                  {opt.label}.
                                </strong>
                                <span>{opt.content}</span>
                              </div>
                            );
                          })}
                        </div>
                      )}
                      <div className="answer-comparison">
                        <div className="answer-row">
                          <span className="answer-label">Your answer:</span>
                          <span className={`answer-value ${q.isCorrect ? '' : 'answer-wrong'}`}>
                            {q.userAnswer || '(not answered)'}
                          </span>
                        </div>
                        {!q.isCorrect && (
                          <div className="answer-row">
                            <span className="answer-label">Correct answer:</span>
                            <span className="answer-value answer-correct">{q.correctAnswer}</span>
                          </div>
                        )}
                      </div>
                      {!q.isCorrect && (
                        <button
                          className="btn btn-sm btn-outline ai-analyze-btn"
                          onClick={() => handleAnalyze(q.questionId)}
                          disabled={aiLoading[q.questionId]}
                        >
                          {aiLoading[q.questionId] ? 'Analyzing...' :
                           aiAnalysis[q.questionId] ? 'View Analysis' : 'AI Analysis'}
                        </button>
                      )}
                      {aiAnalysis[q.questionId] && (
                        <div className="ai-analysis-panel">
                          <div className="ai-analysis-item">
                            <strong>Answer Location:</strong>
                            <p>{aiAnalysis[q.questionId].correctAnswerLocation}</p>
                          </div>
                          <div className="ai-analysis-item">
                            <strong>Trap Explanation:</strong>
                            <p>{aiAnalysis[q.questionId].trapExplanation}</p>
                          </div>
                          <div className="ai-analysis-item">
                            <strong>Tip:</strong>
                            <p>{aiAnalysis[q.questionId].tip}</p>
                          </div>
                        </div>
                      )}
                      {q.explanation && (
                        <p className="answer-explanation" style={{ marginTop: 8, fontSize: '0.85rem' }}>{q.explanation}</p>
                      )}
                    </div>
                    </Fragment>
                  ))}
                </div>
              </div>
            ))}
          </div>
        )}

        {/* Transcript Tab */}
        {activeTab === 'transcript' && (
          <div className="result-transcripts">
            {result.parts?.map(part => (
              <div key={part.partId} className="transcript-section">
                <h3>Part {part.partNumber}: {part.title}</h3>
                <div className="transcript-text">
                  {part.transcriptText?.split('\n').map((line, i) => (
                    <p key={i}>{renderTranscriptLine(line)}</p>
                  ))}
                </div>
              </div>
            ))}
          </div>
        )}

        {/* Vocabulary Tab */}
        {activeTab === 'vocabulary' && (
          <div className="result-vocabulary">
            {vocabLoading ? (
              <div className="loading-spinner"><div className="spinner" /></div>
            ) : vocabData?.vocabularies ? (
              <div className="vocab-grid">
                {vocabData.vocabularies.map((v, i) => (
                  <div key={i} className="vocab-card">
                    <div className="vocab-header">
                      <span className="vocab-word">{v.word}</span>
                      <span className={`badge badge-level-${v.level?.toLowerCase()}`}>{v.level}</span>
                    </div>
                    <span className="vocab-pos">{v.partOfSpeech}</span>
                    <p className="vocab-meaning">{v.vietnameseMeaning}</p>
                    <p className="vocab-context">"{v.contextExample}"</p>
                  </div>
                ))}
              </div>
            ) : (
              <p className="text-muted">No vocabulary found. Please try again.</p>
            )}
          </div>
        )}
      </div>
      <AiVocabularyButton skillType="LISTENING" sourceId={result.testId} />
    </div>
  );
}
