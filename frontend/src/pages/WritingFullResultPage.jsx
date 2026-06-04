import { useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import AiVocabularyButton from '../components/vocab/AiVocabularyButton';

const CRITERIA_EXPLANATIONS = [
  {
    label: 'Task Achievement / Response (TA)',
    desc: 'This criterion is based on your ability to complete all requirements of the task correctly and fully. If all parts of the question are addressed through logical arguments and accurate data, you will easily achieve a high score.',
  },
  {
    label: 'Coherence & Cohesion (CC)',
    desc: 'This criterion evaluates the clarity and logical flow of the essay. A cohesive essay is easy to read, consistent, and objectively clarifies both main and supporting points.',
  },
  {
    label: 'Lexical Resource (LR)',
    desc: 'This criterion tests the candidate\'s vocabulary range and precision. The more varied and natural the lexical choices, the higher the score. Proper spelling and collocation are also evaluated here.',
  },
  {
    label: 'Grammatical Range & Accuracy (GRA)',
    desc: 'This criterion assesses grammatical diversity and precision. Candidates should use a mix of simple and complex sentence structures correctly. Proper punctuation is also essential.',
  }
];

export default function WritingFullResultPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const result = location.state?.result;

  const [activeTaskTab, setActiveTaskTab] = useState(1); // 1 or 2
  const [activeDetailTab, setActiveDetailTab] = useState('errors'); // 'errors' or 'rewrite'

  if (!result) {
    return (
      <div className="loading-screen">
        <div>
          <p style={{ color: 'var(--error)' }}>No exam result found. Please start a new session.</p>
          <button className="btn btn-primary" onClick={() => navigate('/writing')} style={{ marginTop: 16 }}>
            Back to Writing
          </button>
        </div>
      </div>
    );
  }

  const getScoreColor = (score) => {
    const s = parseFloat(score);
    if (s >= 7.0) return 'var(--color-success)';
    if (s >= 5.5) return 'var(--color-warning)';
    return 'var(--color-error)';
  };

  const getScorePercent = (score) => {
    return Math.min(100, (parseFloat(score) / 9) * 100);
  };

  const activeTaskResult = activeTaskTab === 1 ? result.task1Result : result.task2Result;

  const criteria = activeTaskResult ? [
    { label: 'Task Response', score: activeTaskResult.taskResponse },
    { label: 'Coherence & Cohesion', score: activeTaskResult.coherence },
    { label: 'Lexical Resource', score: activeTaskResult.lexical },
    { label: 'Grammatical Range & Accuracy', score: activeTaskResult.grammar },
  ] : [];

  return (
    <div className="writing-result-page" id="writing-result-page">
      <div className="writing-result-content">
        <button className="btn-back" onClick={() => navigate('/writing')} id="back-to-writing">
          <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2"><path d="M19 12H5M12 19l-7-7 7-7"/></svg>
          Go Back to Writing
        </button>

        <h1>Full Mock Test Assessment Report</h1>
        <p className="subtitle" style={{ color: 'var(--text-secondary)', marginBottom: '2rem' }}>
          This report presents the weighted overall band calculated as: (Task 1 + 2 * Task 2) / 3.
        </p>

        {/* Grand Score Display */}
        <div className="writing-score-section" style={{ marginBottom: '2rem' }}>
          <div className="overall-score-circle">
            <svg viewBox="0 0 120 120" width="140" height="140">
              <circle cx="60" cy="60" r="52" fill="none" stroke="var(--color-border)" strokeWidth="8" />
              <circle
                cx="60" cy="60" r="52"
                fill="none"
                stroke="url(#writingGrad)"
                strokeWidth="8"
                strokeLinecap="round"
                strokeDasharray={`${getScorePercent(result.overallWritingBand) * 3.27} 327`}
                transform="rotate(-90 60 60)"
                className="score-ring-animated"
              />
              <defs>
                <linearGradient id="writingGrad" x1="0%" y1="0%" x2="100%" y2="0%">
                  <stop offset="0%" stopColor="#14b8a6" />
                  <stop offset="100%" stopColor="#06b6d4" />
                </linearGradient>
              </defs>
            </svg>
            <div className="overall-score-text">
              <span className="overall-band">{result.overallWritingBand}</span>
              <span className="overall-label">Weighted Band</span>
            </div>
          </div>
          <div style={{ marginLeft: '2rem', display: 'flex', flexDirection: 'column', justifyContent: 'center' }}>
            <h2 style={{ margin: 0 }}>Practice Exam Finished</h2>
            <p style={{ margin: '0.5rem 0 0', color: 'var(--text-secondary)' }}>
              Task 1 score: <strong>{result.task1Result.overallBand}</strong> · Task 2 score (Double Weight): <strong>{result.task2Result.overallBand}</strong>
            </p>
          </div>
        </div>

        {/* Task Tab Switcher */}
        <div className="writing-tabs" style={{ marginBottom: '1.5rem', display: 'flex', gap: '0.5rem', borderBottom: '1px solid var(--border-color)', paddingBottom: '0.5rem' }}>
          <button
            className={`tab-btn ${activeTaskTab === 1 ? 'active' : ''}`}
            onClick={() => { setActiveTaskTab(1); setActiveDetailTab('errors'); }}
            style={{
              padding: '0.75rem 1.5rem', background: 'none', border: 'none',
              borderBottom: activeTaskTab === 1 ? '3px solid var(--primary-color)' : '3px solid transparent',
              color: activeTaskTab === 1 ? 'var(--primary-color)' : 'var(--text-secondary)',
              fontWeight: 600, cursor: 'pointer', fontSize: '1rem'
            }}
          >
            Task 1: Academic Report (Band {result.task1Result.overallBand})
          </button>
          <button
            className={`tab-btn ${activeTaskTab === 2 ? 'active' : ''}`}
            onClick={() => { setActiveTaskTab(2); setActiveDetailTab('errors'); }}
            style={{
              padding: '0.75rem 1.5rem', background: 'none', border: 'none',
              borderBottom: activeTaskTab === 2 ? '3px solid var(--primary-color)' : '3px solid transparent',
              color: activeTaskTab === 2 ? 'var(--primary-color)' : 'var(--text-secondary)',
              fontWeight: 600, cursor: 'pointer', fontSize: '1rem'
            }}
          >
            Task 2: Essay Writing (Band {result.task2Result.overallBand})
          </button>
        </div>

        {/* Selected Task Details */}
        {activeTaskResult && (
          <div style={{ animation: 'fadeIn 0.3s ease' }}>
            <div className="writing-score-section" style={{ marginTop: '1rem', padding: '1.5rem', background: 'var(--surface-container-low)', borderRadius: 'var(--radius-lg)' }}>
              <div className="criteria-bars" style={{ flex: 1, width: '100%' }}>
                <h3 style={{ marginTop: 0, marginBottom: '1rem' }}>Component Scores Breakdown</h3>
                {criteria.map((c, i) => (
                  <div key={i} className="criteria-row">
                    <span className="criteria-label">{c.label}</span>
                    <div className="criteria-bar-track">
                      <div
                        className="criteria-bar-fill"
                        style={{
                          width: `${getScorePercent(c.score)}%`,
                          background: getScoreColor(c.score),
                        }}
                      />
                    </div>
                    <span className="criteria-score" style={{ color: getScoreColor(c.score) }}>
                      {c.score}
                    </span>
                  </div>
                ))}
              </div>
            </div>

            {/* General Feedback */}
            {activeTaskResult.generalFeedback && (
              <div className="card feedback-card" style={{ marginTop: '1.5rem', padding: '1.5rem', background: 'var(--surface-container-low)', borderRadius: 'var(--radius-lg)' }}>
                <h3 style={{ marginTop: 0 }}>General Feedback</h3>
                <p style={{ lineHeight: '1.6' }}>{activeTaskResult.generalFeedback}</p>
              </div>
            )}

            {/* Sub-tabs: Error Analysis | Upgraded Essay */}
            <div className="writing-tabs" style={{ marginTop: '2rem' }}>
              <button
                className={`tab-btn ${activeDetailTab === 'errors' ? 'active' : ''}`}
                onClick={() => setActiveDetailTab('errors')}
                id="tab-errors"
              >
                Error Analysis ({activeTaskResult.errors?.length || 0})
              </button>
              <button
                className={`tab-btn ${activeDetailTab === 'rewrite' ? 'active' : ''}`}
                onClick={() => setActiveDetailTab('rewrite')}
                id="tab-rewrite"
              >
                Upgraded Essay
              </button>
            </div>

            <div className="tab-content" style={{ marginTop: '1rem' }}>
              {activeDetailTab === 'errors' && (
                <div className="errors-panel">
                  {(!activeTaskResult.errors || activeTaskResult.errors.length === 0) ? (
                    <p className="empty-msg">No errors found. Excellent writing!</p>
                  ) : (
                    <div className="error-table">
                      <div className="error-table-header">
                        <span>Original</span>
                        <span>Explanation</span>
                        <span>Corrected</span>
                      </div>
                      {activeTaskResult.errors.map((err, i) => (
                        <div key={i} className="error-table-row">
                          <div className="error-original">{err.originalSentence}</div>
                          <div className="error-explanation">
                            <span className={`error-type-badge etype-${(err.errorType || 'grammar').toLowerCase()}`}>
                              {err.errorType}
                            </span>
                            {err.explanation}
                          </div>
                          <div className="error-corrected">{err.correctedSentence}</div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              )}

              {activeDetailTab === 'rewrite' && (
                <div className="rewrite-panel">
                  {activeTaskResult.rewrittenVersion ? (
                    <>
                      <div className="rewrite-text card">
                        {activeTaskResult.rewrittenVersion.split('\n').map((p, i) => (
                          <p key={i} className="rewrite-paragraph" style={{ marginBottom: '1rem', lineHeight: '1.6' }}>{p}</p>
                        ))}
                      </div>
                      {activeTaskResult.improvementNotes && activeTaskResult.improvementNotes.length > 0 && (
                        <div className="improvement-notes card" style={{ marginTop: '1.5rem' }}>
                          <h3>Improvement Notes</h3>
                          <ul>
                            {activeTaskResult.improvementNotes.map((note, i) => (
                              <li key={i} style={{ marginBottom: '0.5rem', lineHeight: '1.5' }}>{note}</li>
                            ))}
                          </ul>
                        </div>
                      )}
                    </>
                  ) : (
                    <p className="empty-msg">No rewritten essay available.</p>
                  )}
                </div>
              )}
            </div>
          </div>
        )}

        {/* Explanations Reference */}
        <div className="card criteria-explanations-card" style={{ marginTop: '2rem', padding: 24, background: 'var(--surface-container-low)', borderRadius: 'var(--radius-lg)' }}>
          <h3 style={{ marginTop: 0, marginBottom: 16 }}>Grading Criteria Explanations</h3>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 16 }}>
            {CRITERIA_EXPLANATIONS.map((c, i) => (
              <div key={i} style={{
                padding: 16, borderRadius: 'var(--radius-lg)',
                background: 'var(--surface-container-lowest)',
                border: '1px solid var(--outline-variant)'
              }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
                  <span style={{ fontWeight: 600, fontSize: '0.9rem', color: 'var(--primary)' }}>{c.label}</span>
                </div>
                <p style={{ fontSize: '0.78rem', color: 'var(--on-surface-variant)', lineHeight: 1.5, margin: 0 }}>{c.desc}</p>
              </div>
            ))}
          </div>
        </div>

        {/* Action Buttons */}
        <div style={{ display: 'flex', gap: '1rem', marginTop: '3rem', justifyContent: 'center' }}>
          <button className="btn btn-primary" onClick={() => navigate('/writing')}>
            Take Another Writing Test
          </button>
          <button className="btn btn-outline" onClick={() => navigate('/writing/history')}>
            View History
          </button>
        </div>
      </div>
      {activeTaskResult?.submissionId && (
        <AiVocabularyButton skillType="WRITING" sourceId={activeTaskResult.submissionId} />
      )}
    </div>
  );
}
