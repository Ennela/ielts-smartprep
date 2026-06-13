import { useEffect, useState, useRef } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import mockTestApi from '../api/mockTestApi';
import AiVocabularyButton from '../components/vocab/AiVocabularyButton';

const CRITERIA_EXPLANATIONS = [
  {
    label: 'Task Response / Achievement',
    desc: 'Completing all instructions, describing key trends in Task 1, or fully answering the prompt prompt with logical supporting ideas in Task 2.',
  },
  {
    label: 'Coherence & Cohesion',
    desc: 'Logical structure, paragraphing, cohesive linking devices, and general readability of sentences.',
  },
  {
    label: 'Lexical Resource',
    desc: 'Range, diversity, precision, and spelling accuracy of the vocabulary used.',
  },
  {
    label: 'Grammatical Range & Accuracy',
    desc: 'Mix of simple and complex sentence structures, punctuation accuracy, and verb tense precision.',
  }
];

export default function MockTestResultPage() {
  const { submissionId } = useParams();
  const navigate = useNavigate();

  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [writingTab, setWritingTab] = useState('task1'); // 'task1' or 'task2'
  const [feedbackSubTab, setFeedbackSubTab] = useState('errors'); // 'errors' or 'rewrite' or 'essay'
  
  const pollTimerRef = useRef(null);

  // Poll for completion if grading
  useEffect(() => {
    loadResult();
    return () => {
      if (pollTimerRef.current) clearInterval(pollTimerRef.current);
    };
  }, [submissionId]);

  const loadResult = async () => {
    try {
      const res = await mockTestApi.getSubmission(submissionId);
      const data = res.data?.data;
      setResult(data);
      setLoading(false);

      if (data && data.status === 'GRADING') {
        startPolling();
      } else {
        if (pollTimerRef.current) {
          clearInterval(pollTimerRef.current);
          pollTimerRef.current = null;
        }
      }
    } catch (err) {
      setError(err.message || 'Failed to load test report.');
      setLoading(false);
    }
  };

  const startPolling = () => {
    if (pollTimerRef.current) return;
    
    pollTimerRef.current = setInterval(async () => {
      try {
        const res = await mockTestApi.getSubmission(submissionId);
        const data = res.data?.data;
        if (data) {
          setResult(data);
          if (data.status !== 'GRADING') {
            clearInterval(pollTimerRef.current);
            pollTimerRef.current = null;
          }
        }
      } catch (err) {
        console.error('Polling error', err);
      }
    }, 5000);
  };

  const getScoreColor = (score) => {
    const s = parseFloat(score);
    if (s >= 7.0) return 'var(--color-success)';
    if (s >= 5.5) return 'var(--color-warning)';
    return 'var(--color-error)';
  };

  const getScorePercent = (score) => {
    return Math.min(100, (parseFloat(score || 0) / 9) * 100);
  };

  if (loading) {
    return (
      <div className="loading-screen">
        <span className="spinner" style={{ width: 24, height: 24 }}></span>
        Retrieving report details...
      </div>
    );
  }

  if (error || !result) {
    return (
      <div className="dashboard-content">
        <div className="error-msg">{error || 'Exam submission not found.'}</div>
        <button className="btn btn-outline" onClick={() => navigate('/mock-tests')}>
          Back to Lobby
        </button>
      </div>
    );
  }

  // ── 1. GRADING STATE VIEW ──
  if (result.status === 'GRADING') {
    return (
      <div className="dashboard-content" style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '80vh' }}>
        <div className="card" style={{ maxWidth: '560px', width: '100%', textAlign: 'center', padding: '40px' }}>
          <span className="spinner" style={{ width: '48px', height: '48px', borderWidth: '3px', marginBottom: '24px' }} />
          
          <h2 style={{ fontSize: '1.6rem', fontWeight: 700, marginBottom: '8px' }}>Evaluating Mock Test</h2>
          <p style={{ color: 'var(--color-text-secondary)', fontSize: '0.95rem', marginBottom: '32px' }}>
            We are checking your answers and analyzing your essays using Gemini. This process takes 30-60 seconds.
          </p>

          <div style={{ textAlign: 'left', background: 'var(--surface-container-low)', padding: '20px', borderRadius: 'var(--radius-lg)', display: 'flex', flexDirection: 'column', gap: '14px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
              <span className="material-symbols-outlined" style={{ color: 'var(--secondary)' }}>check_circle</span>
              <span style={{ fontSize: '0.9rem', fontWeight: 600 }}>Score Listening answers (Done)</span>
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
              <span className="material-symbols-outlined" style={{ color: 'var(--secondary)' }}>check_circle</span>
              <span style={{ fontSize: '0.9rem', fontWeight: 600 }}>Score Reading answers (Done)</span>
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
              <span className="spinner" style={{ width: '16px', height: '16px' }} />
              <span style={{ fontSize: '0.9rem', fontWeight: 600 }}>Gemini evaluating Task 1 & Task 2 writing...</span>
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px', opacity: 0.5 }}>
              <span className="material-symbols-outlined">hourglass_empty</span>
              <span style={{ fontSize: '0.9rem' }}>Aggregate overall band & compile report</span>
            </div>
          </div>
          
          <p style={{ fontSize: '0.8rem', color: 'var(--color-text-muted)', marginTop: '24px' }}>
            You can safely leave this page. The test will compile in the background and show up in your lobby history.
          </p>
        </div>
      </div>
    );
  }

  // ── 2. FAILED STATE VIEW ──
  if (result.status === 'FAILED') {
    return (
      <div className="dashboard-content" style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '80vh' }}>
        <div className="card" style={{ maxWidth: '480px', width: '100%', textAlign: 'center', padding: '40px' }}>
          <span className="material-symbols-outlined" style={{ fontSize: '48px', color: 'var(--error)', marginBottom: '16px' }}>error</span>
          <h2 style={{ fontSize: '1.5rem', fontWeight: 700, marginBottom: '8px' }}>Grading Interrupted</h2>
          <p style={{ color: 'var(--color-text-secondary)', fontSize: '0.9rem', marginBottom: '24px' }}>
            An unexpected error occurred during AI evaluation. Your listening and reading scores are saved, but writing could not be completed.
          </p>
          <button className="btn btn-primary" onClick={() => navigate('/mock-tests')} style={{ width: '100%' }}>
            Return to Exam Lobby
          </button>
        </div>
      </div>
    );
  }

  // ── 3. COMPLETED STATE VIEW ──
  // Extract writing details
  const w1 = result.writingSubmission1;
  const w2 = result.writingSubmission2;

  const activeWritingSub = writingTab === 'task1' ? w1 : w2;

  // Safe error parser
  let errorsList = [];
  if (activeWritingSub?.errorListJson) {
    try {
      errorsList = JSON.parse(activeWritingSub.errorListJson);
    } catch (_e) {
      errorsList = [];
    }
  }

  // Safe improvement notes parser
  let improvementNotes = [];
  if (activeWritingSub?.improvementNotesJson) {
    try {
      improvementNotes = JSON.parse(activeWritingSub.improvementNotesJson);
    } catch (_e) {
      improvementNotes = [];
    }
  }

  const writingCriteria = activeWritingSub ? [
    { label: 'Task Response', score: activeWritingSub.taskResponse },
    { label: 'Coherence & Cohesion', score: activeWritingSub.coherence },
    { label: 'Lexical Resource', score: activeWritingSub.lexical },
    { label: 'Grammatical Range & Accuracy', score: activeWritingSub.grammar },
  ] : [];

  return (
    <div className="writing-result-page" style={{ padding: '32px' }}>
      <div className="writing-result-content" style={{ maxWidth: '1200px', margin: '0 auto' }}>
        
        {/* Back Button */}
        <button className="btn-back" onClick={() => navigate('/mock-tests')} id="back-to-lobby">
          <span className="material-symbols-outlined" style={{ fontSize: 20 }}>arrow_back</span>
          Back to Exam Lobby
        </button>

        <h1 style={{ fontSize: '2rem', fontWeight: 700, marginBottom: '24px' }}>Mock Test Assessment Report</h1>

        {/* ── Section Scores Summary ── */}
        <div style={{ display: 'grid', gridTemplateColumns: '1.2fr 2fr', gap: '32px', marginBottom: '40px' }}>
          
          {/* Circular Overall Band */}
          <div className="card" style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', padding: '32px' }}>
            <div className="overall-score-circle" style={{ position: 'relative', width: '150px', height: '150px' }}>
              <svg viewBox="0 0 120 120" width="150" height="150">
                <circle cx="60" cy="60" r="52" fill="none" stroke="var(--outline-variant)" strokeWidth="8" />
                <circle
                  cx="60"
                  cy="60"
                  r="52"
                  fill="none"
                  stroke="url(#mockGrad)"
                  strokeWidth="8"
                  strokeLinecap="round"
                  strokeDasharray={`${getScorePercent(result.overallBand) * 3.27} 327`}
                  transform="rotate(-90 60 60)"
                  style={{ transition: 'stroke-dasharray 1s ease' }}
                />
                <defs>
                  <linearGradient id="mockGrad" x1="0%" y1="0%" x2="100%" y2="0%">
                    <stop offset="0%" stopColor="#003fb1" />
                    <stop offset="100%" stopColor="#006c4a" />
                  </linearGradient>
                </defs>
              </svg>
              <div className="overall-score-text" style={{ position: 'absolute', top: '50%', left: '50%', transform: 'translate(-50%, -50%)', display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
                <span className="overall-band" style={{ fontSize: '2.5rem', fontWeight: 800, color: 'var(--primary)' }}>
                  {result.overallBand?.toFixed(1) || '—'}
                </span>
                <span className="overall-label" style={{ fontSize: '0.75rem', textTransform: 'uppercase', letterSpacing: '0.05em', color: 'var(--outline)' }}>
                  Overall Band
                </span>
              </div>
            </div>
          </div>

          {/* Section Band Details Grid */}
          <div className="card" style={{ display: 'flex', flexDirection: 'column', justifyContent: 'center', gap: '20px', padding: '32px' }}>
            <h3 style={{ fontSize: '1.1rem', fontWeight: 600, borderBottom: '1px solid var(--outline-variant)', paddingBottom: '8px' }}>
              Individual Skills Breakdown
            </h3>
            
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '16px' }}>
              {/* Listening card */}
              <div style={{ background: 'var(--surface-container-low)', padding: '16px', borderRadius: 'var(--radius-lg)', textAlign: 'center' }}>
                <span className="material-symbols-outlined" style={{ color: 'var(--primary)', marginBottom: '4px' }}>headphones</span>
                <p style={{ fontSize: '0.75rem', fontWeight: 600, color: 'var(--outline)', textTransform: 'uppercase' }}>Listening</p>
                <p style={{ fontSize: '1.6rem', fontWeight: 700, margin: '4px 0' }}>{result.listeningBand?.toFixed(1) || '—'}</p>
                <p style={{ fontSize: '0.75rem', color: 'var(--color-text-secondary)' }}>Score: {result.listeningScore || 0}/40</p>
              </div>

              {/* Reading card */}
              <div style={{ background: 'var(--surface-container-low)', padding: '16px', borderRadius: 'var(--radius-lg)', textAlign: 'center' }}>
                <span className="material-symbols-outlined" style={{ color: 'var(--secondary)', marginBottom: '4px' }}>menu_book</span>
                <p style={{ fontSize: '0.75rem', fontWeight: 600, color: 'var(--outline)', textTransform: 'uppercase' }}>Reading</p>
                <p style={{ fontSize: '1.6rem', fontWeight: 700, margin: '4px 0' }}>{result.readingBand?.toFixed(1) || '—'}</p>
                <p style={{ fontSize: '0.75rem', color: 'var(--color-text-secondary)' }}>Score: {result.readingScore || 0}/40</p>
              </div>

              {/* Writing card */}
              <div style={{ background: 'var(--surface-container-low)', padding: '16px', borderRadius: 'var(--radius-lg)', textAlign: 'center' }}>
                <span className="material-symbols-outlined" style={{ color: 'var(--tertiary-container)', marginBottom: '4px' }}>edit_note</span>
                <p style={{ fontSize: '0.75rem', fontWeight: 600, color: 'var(--outline)', textTransform: 'uppercase' }}>Writing</p>
                <p style={{ fontSize: '1.6rem', fontWeight: 700, margin: '4px 0' }}>{result.writingBand?.toFixed(1) || '—'}</p>
                <p style={{ fontSize: '0.75rem', color: 'var(--color-text-secondary)' }}>Weighted Average</p>
              </div>
            </div>
          </div>
        </div>

        {/* ── Detailed Writing Assessment Area ── */}
        <h2 style={{ fontSize: '1.5rem', fontWeight: 700, marginBottom: '16px' }}>Writing Assessment Details</h2>
        
        {/* Toggle between Task 1 and Task 2 */}
        <div className="writing-tabs" style={{ marginBottom: '24px', display: 'flex', gap: '8px' }}>
          <button
            className={`tab-btn ${writingTab === 'task1' ? 'active' : ''}`}
            onClick={() => { setWritingTab('task1'); setFeedbackSubTab('errors'); }}
            style={{ flex: 1, padding: '12px' }}
          >
            Task 1 Report (Score: {w1?.overallBand?.toFixed(1) || '—'})
          </button>
          <button
            className={`tab-btn ${writingTab === 'task2' ? 'active' : ''}`}
            onClick={() => { setWritingTab('task2'); setFeedbackSubTab('errors'); }}
            style={{ flex: 1, padding: '12px' }}
          >
            Task 2 Essay (Score: {w2?.overallBand?.toFixed(1) || '—'})
          </button>
        </div>

        {activeWritingSub ? (
          <div>
            {/* Writing Sub-Score Breakdown */}
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 2fr', gap: '32px', marginBottom: '32px' }}>
              <div className="card" style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', padding: '24px' }}>
                <div style={{ textAlign: 'center' }}>
                  <p style={{ fontSize: '0.8rem', color: 'var(--outline)', textTransform: 'uppercase', fontWeight: 600 }}>Task Band Score</p>
                  <p style={{ fontSize: '3rem', fontWeight: 800, color: 'var(--primary)', margin: '8px 0' }}>
                    {activeWritingSub.overallBand?.toFixed(1) || '—'}
                  </p>
                </div>
              </div>

              {/* Task Criteria Scores */}
              <div className="card" style={{ padding: '24px', display: 'flex', flexDirection: 'column', justifyContent: 'center' }}>
                <div className="criteria-bars">
                  {writingCriteria.map((c, i) => (
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
                        {c.score?.toFixed(1) || '—'}
                      </span>
                    </div>
                  ))}
                </div>
              </div>
            </div>

            {/* General Feedback Box */}
            <div className="card feedback-card" style={{ marginBottom: '32px' }}>
              <h3 style={{ fontSize: '1.1rem', fontWeight: 700, marginBottom: '12px' }}>General Feedback</h3>
              <p style={{ fontSize: '0.95rem', lineHeight: '1.7', color: 'var(--color-text-secondary)' }}>
                {activeWritingSub.generalFeedback || 'No feedback available.'}
              </p>
            </div>

            {/* Sub-Tabs: Original Essay / Errors Analysis / Upgraded Essay */}
            <div className="writing-tabs" style={{ marginBottom: '20px' }}>
              <button
                className={`tab-btn ${feedbackSubTab === 'errors' ? 'active' : ''}`}
                onClick={() => setFeedbackSubTab('errors')}
              >
                Error Analysis ({(errorsList || []).length})
              </button>
              <button
                className={`tab-btn ${feedbackSubTab === 'rewrite' ? 'active' : ''}`}
                onClick={() => setFeedbackSubTab('rewrite')}
              >
                Upgraded Essay
              </button>
              <button
                className={`tab-btn ${feedbackSubTab === 'essay' ? 'active' : ''}`}
                onClick={() => setFeedbackSubTab('essay')}
              >
                My Submission
              </button>
            </div>

            {/* Sub-Tab Content rendering */}
            <div className="tab-content">
              {feedbackSubTab === 'essay' && (
                <div className="card" style={{ padding: '24px', background: 'var(--surface-container-lowest)', minHeight: '200px' }}>
                  {(activeWritingSub.essayText || '')
                    .split('\n')
                    .map((p, i) => (
                      <p key={i} style={{ fontSize: '0.98rem', lineHeight: 1.75, marginBottom: '14px', whiteSpace: 'pre-wrap' }}>
                        {p}
                      </p>
                    ))}
                </div>
              )}

              {feedbackSubTab === 'errors' && (
                <div className="errors-panel">
                  {(!errorsList || errorsList.length === 0) ? (
                    <p className="empty-msg">No grammar/spelling errors found. Excellent work!</p>
                  ) : (
                    <div className="error-table">
                      <div className="error-table-header">
                        <span>Original Sentence</span>
                        <span>Explanation & Feedback</span>
                        <span>Corrected Sentence</span>
                      </div>
                      {errorsList.map((err, i) => (
                        <div key={i} className="error-table-row">
                          <div className="error-original" style={{ color: 'var(--error)' }}>
                            {err.originalSentence}
                          </div>
                          <div className="error-explanation">
                            <span className={`error-type-badge etype-${(err.errorType || 'grammar').toLowerCase()}`} style={{ marginRight: '8px' }}>
                              {err.errorType || 'GRAMMAR'}
                            </span>
                            {err.explanation}
                          </div>
                          <div className="error-corrected" style={{ color: 'var(--secondary)' }}>
                            {err.correctedSentence}
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              )}

              {feedbackSubTab === 'rewrite' && (
                <div className="rewrite-panel">
                  {activeWritingSub.rewrittenVersion ? (
                    <>
                      <div className="rewrite-text card" style={{ padding: '24px', background: 'var(--surface-container-lowest)' }}>
                        {activeWritingSub.rewrittenVersion.split('\n').map((p, i) => (
                          <p key={i} className="rewrite-paragraph" style={{ fontSize: '0.98rem', lineHeight: 1.75, marginBottom: '14px' }}>
                            {p}
                          </p>
                        ))}
                      </div>
                      
                      {improvementNotes && improvementNotes.length > 0 && (
                        <div className="improvement-notes card" style={{ marginTop: '24px', padding: '24px' }}>
                          <h3 style={{ fontSize: '1.1rem', fontWeight: 700, marginBottom: '12px' }}>Improvement Notes</h3>
                          <ul style={{ paddingLeft: '20px', fontSize: '0.95rem', lineHeight: '1.7', color: 'var(--color-text-secondary)' }}>
                            {improvementNotes.map((note, i) => (
                              <li key={i} style={{ marginBottom: '8px' }}>{note}</li>
                            ))}
                          </ul>
                        </div>
                      )}
                    </>
                  ) : (
                    <p className="empty-msg">No upgraded essay draft available.</p>
                  )}
                </div>
              )}
            </div>
          </div>
        ) : (
          <div className="card" style={{ padding: '24px', textAlign: 'center', color: 'var(--color-text-muted)' }}>
            No essay submission was registered for this task.
          </div>
        )}

        {/* Criteria Explanation Box */}
        <div className="card criteria-explanations-card" style={{ marginTop: '40px', padding: '24px' }}>
          <h3 style={{ marginBottom: '16px', fontSize: '1.1rem', fontWeight: 700 }}>Grading Criteria Breakdown</h3>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '16px' }}>
            {CRITERIA_EXPLANATIONS.map((c, i) => (
              <div key={i} style={{
                padding: '16px', borderRadius: 'var(--radius-lg)',
                background: 'var(--surface-container-low)',
                border: '1px solid var(--outline-variant)'
              }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '8px' }}>
                  <span style={{ fontWeight: 600, fontSize: '0.9rem', color: 'var(--primary)' }}>{c.label}</span>
                </div>
                <p style={{ fontSize: '0.78rem', color: 'var(--on-surface-variant)', lineHeight: 1.5, margin: 0 }}>{c.desc}</p>
              </div>
            ))}
          </div>
        </div>

      </div>
      {activeWritingSub?.submissionId && (
        <AiVocabularyButton skillType="WRITING" sourceId={activeWritingSub.submissionId} />
      )}
    </div>
  );
}
