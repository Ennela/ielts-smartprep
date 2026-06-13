import { useState, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import writingApi from '../api/writingApi';
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

export default function WritingResultPage() {
    const { submissionId } = useParams();
    const navigate = useNavigate();

    const [result, setResult] = useState(null);
    const [activeTab, setActiveTab] = useState('errors');
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');

    useEffect(() => {
        loadResult();
    }, [submissionId]);

    const loadResult = async () => {
        setLoading(true);
        try {
            const res = await writingApi.getSubmission(submissionId);
            setResult(res.data.data);
        } catch (err) {
            setError('Failed to load result.');
        } finally {
            setLoading(false);
        }
    };

    const getScoreColor = (score) => {
        const s = parseFloat(score);
        if (s >= 7.0) return 'var(--color-success)';
        if (s >= 5.5) return 'var(--color-warning)';
        return 'var(--color-error)';
    };

    const getScorePercent = (score) => {
        return Math.min(100, (parseFloat(score) / 9) * 100);
    };

    if (loading) {
        return (
            <div className="loading-screen">
                <span className="spinner" style={{ width: 24, height: 24 }}></span>
                Loading result...
            </div>
        );
    }

    if (error || !result) {
        return (
            <div className="result-content">
                <div className="error-msg">{error || 'Result not found.'}</div>
                <button className="btn btn-outline" onClick={() => navigate('/writing')}>Go Back to Writing</button>
            </div>
        );
    }

    const criteria = [
        { label: 'Task Response', score: result.taskResponse },
        { label: 'Coherence & Cohesion', score: result.coherence },
        { label: 'Lexical Resource', score: result.lexical },
        { label: 'Grammatical Range & Accuracy', score: result.grammar },
    ];

    return (
        <div className="writing-result-page">
            <div className="writing-result-content">
                <button className="btn-back" onClick={() => navigate('/writing')} id="back-to-writing">
                    <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2"><path d="M19 12H5M12 19l-7-7 7-7" /></svg>
                    Go Back to Writing
                </button>

                <h1>Writing Assessment Report</h1>

                {/* Overall Score Circle */}
                <div className="writing-score-section">
                    <div className="overall-score-circle">
                        <svg viewBox="0 0 120 120" width="140" height="140">
                            <circle cx="60" cy="60" r="52" fill="none" stroke="var(--color-border)" strokeWidth="8" />
                            <circle
                                cx="60" cy="60" r="52"
                                fill="none"
                                stroke="url(#writingGrad)"
                                strokeWidth="8"
                                strokeLinecap="round"
                                strokeDasharray={`${getScorePercent(result.overallBand) * 3.27} 327`}
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
                            <span className="overall-band">{result.overallBand}</span>
                            <span className="overall-label">Overall Band</span>
                        </div>
                    </div>

                    <div className="criteria-bars">
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
                {result.generalFeedback && (
                    <div className="card feedback-card">
                        <h3>General Feedback</h3>
                        <p>{result.generalFeedback}</p>
                    </div>
                )}

                {/* Tabs: Errors | Rewritten Essay */}
                <div className="writing-tabs">
                    <button
                        className={`tab-btn ${activeTab === 'errors' ? 'active' : ''}`}
                        onClick={() => setActiveTab('errors')}
                        id="tab-errors"
                    >
                        Error Analysis ({result.errors?.length || 0})
                    </button>
                    <button
                        className={`tab-btn ${activeTab === 'rewrite' ? 'active' : ''}`}
                        onClick={() => setActiveTab('rewrite')}
                        id="tab-rewrite"
                    >
                        Upgraded Essay
                    </button>
                </div>

                <div className="tab-content">
                    {activeTab === 'errors' && (
                        <div className="errors-panel">
                            {(!result.errors || result.errors.length === 0) ? (
                                <p className="empty-msg">No errors found. Excellent writing!</p>
                            ) : (
                                <div className="error-table">
                                    <div className="error-table-header">
                                        <span>Original</span>
                                        <span>Explanation</span>
                                        <span>Corrected</span>
                                    </div>
                                    {result.errors.map((err, i) => (
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

                    {activeTab === 'rewrite' && (
                        <div className="rewrite-panel">
                            {result.rewrittenVersion ? (
                                <>
                                    <div className="rewrite-text card">
                                        {result.rewrittenVersion.split('\n').map((p, i) => (
                                            <p key={i} className="rewrite-paragraph">{p}</p>
                                        ))}
                                    </div>
                                    {result.improvementNotes && result.improvementNotes.length > 0 && (
                                        <div className="improvement-notes card">
                                            <h3>Improvement Notes</h3>
                                            <ul>
                                                {result.improvementNotes.map((note, i) => (
                                                    <li key={i}>{note}</li>
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

                {/* Grading Criteria Explanations Reference */}
                <div className="card criteria-explanations-card" style={{ marginTop: 24, padding: 24 }}>
                    <h3 style={{ marginBottom: 16 }}>Grading Criteria Explanations</h3>
                    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 16 }}>
                        {CRITERIA_EXPLANATIONS.map((c, i) => (
                            <div key={i} style={{
                                padding: 16, borderRadius: 'var(--radius-lg)',
                                background: 'var(--surface-container-low)',
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
            </div>
            <AiVocabularyButton skillType="WRITING" sourceId={submissionId} />
        </div>
    );
}
