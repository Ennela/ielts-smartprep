import { useState, useEffect, useMemo } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import listeningApi from '../api/listeningApi';
import AudioPlayer from '../components/listening/AudioPlayer';

export default function ListeningExamPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const mode = searchParams.get('mode') || 'practice';
  const partIds = useMemo(() =>
    (searchParams.get('parts') || '').split(',').map(Number).filter(Boolean),
  [searchParams]);

  const [parts, setParts] = useState([]);
  const [answers, setAnswers] = useState({});
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [currentPartIndex, setCurrentPartIndex] = useState(0);

  useEffect(() => {
    const loadParts = async () => {
      try {
        const loaded = [];
        for (const id of partIds) {
          const res = await listeningApi.getPartById(id);
          if (res.data?.data) loaded.push(res.data.data);
        }
        setParts(loaded);
      } catch (err) {
        console.error(err);
      } finally {
        setLoading(false);
      }
    };
    if (partIds.length) loadParts();
  }, [partIds]);

  const handleAnswer = (questionId, value) =>
    setAnswers(prev => ({ ...prev, [questionId]: value }));

  const handleSubmit = async () => {
    setSubmitting(true);
    try {
      const res = await listeningApi.submitTest(
        mode === 'mock-test' ? 'MOCK_TEST' : 'PRACTICE',
        partIds,
        answers,
      );
      navigate(`/listening/result/${res.data?.data?.testId}`, { state: res.data?.data });
    } catch (err) {
      console.error(err);
      alert('Submission failed');
    } finally {
      setSubmitting(false);
    }
  };

  const currentPart = parts[currentPartIndex];
  const totalQuestions = parts.reduce((s, p) => s + (p.questions?.length || 0), 0);
  const answeredCount = Object.keys(answers).filter(k => answers[k]?.trim()).length;
  const audioBaseUrl = import.meta.env.VITE_API_URL?.replace('/api/v1', '') || 'http://localhost:8080';

  if (loading) return (
    <div className="listening-page">
      <div className="loading-spinner"><div className="spinner" /></div>
    </div>
  );

  return (
    <div className="listening-page" style={{ display: 'flex', flexDirection: 'column', minHeight: '100vh' }}>

      {/* ── Header ── */}
      <header style={{
        padding: '16px 32px', borderBottom: '1px solid var(--outline-variant)',
        background: 'var(--surface-container-lowest)', flexShrink: 0,
      }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 12, cursor: 'pointer' }}
              onClick={() => navigate('/listening')}>
              <span style={{ fontFamily: 'var(--font-heading)', fontWeight: 700, fontSize: '1.1rem', color: 'var(--primary)' }}>
                SmartPrep
              </span>
              <span style={{
                padding: '2px 10px', borderRadius: 'var(--radius-full)', fontSize: '0.72rem', fontWeight: 700,
                background: mode === 'mock-test' ? 'rgba(186,26,26,0.08)' : 'rgba(0,108,74,0.08)',
                color: mode === 'mock-test' ? 'var(--error)' : 'var(--secondary)',
              }}>
                {mode === 'mock-test' ? 'MOCK TEST' : 'PRACTICE'}
              </span>
            </div>
            {currentPart && (
              <p style={{ fontSize: '0.875rem', color: 'var(--on-surface-variant)', marginTop: 4 }}>
                <strong style={{ color: 'var(--on-surface)' }}>
                  Part {currentPart.partNumber}: {currentPart.title}
                </strong>
                {currentPart.topic && ` · ${currentPart.topic}`}
              </p>
            )}
          </div>
          <div style={{ textAlign: 'right' }}>
            <p style={{ fontSize: '0.875rem', color: 'var(--on-surface-variant)' }}>
              Answered <strong style={{ color: 'var(--on-surface)' }}>{answeredCount}</strong> / {totalQuestions}
            </p>
          </div>
        </div>
      </header>

      {/* ── Part Tabs ── */}
      {parts.length > 1 && (
        <div className="listening-part-tabs" style={{ padding: '12px 32px', background: 'var(--surface-container-low)', borderBottom: '1px solid var(--outline-variant)' }}>
          {parts.map((part, idx) => (
            <button
              key={part.partId}
              className={`part-tab ${idx === currentPartIndex ? 'active' : ''}`}
              onClick={() => setCurrentPartIndex(idx)}
            >
              Part {part.partNumber}: {part.title}
            </button>
          ))}
        </div>
      )}

      {/* ── Main Content ── */}
      <main style={{ flex: 1, maxWidth: 820, margin: '0 auto', width: '100%', padding: '32px 24px' }}>
        {currentPart && (
          <>
            {/* Audio Player Card */}
            <div style={{
              background: 'var(--surface-container-lowest)', border: '1px solid var(--outline-variant)',
              borderRadius: 'var(--radius-xl)', padding: 24, marginBottom: 32,
            }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 16 }}>
                <span className="material-symbols-outlined" style={{ color: 'var(--primary)', fontSize: 24 }}>headphones</span>
                <div>
                  <p style={{ fontFamily: 'var(--font-heading)', fontWeight: 600, fontSize: '0.95rem' }}>
                    Part {currentPart.partNumber}: {currentPart.title}
                  </p>
                  {currentPart.topic && (
                    <p style={{ fontSize: '0.8', color: 'var(--on-surface-variant)' }}>{currentPart.topic}</p>
                  )}
                </div>
              </div>
              <AudioPlayer
                src={`${audioBaseUrl}${currentPart.audioUrl}`}
                mode={mode}
              />
            </div>

            {/* Questions */}
            <div className="listening-questions">
              {(currentPart.questions || [])
                .sort((a, b) => a.orderIndex - b.orderIndex)
                .map((q, qIdx) => {
                  let globalNum = qIdx + 1;
                  for (let i = 0; i < currentPartIndex; i++) globalNum += (parts[i].questions?.length || 0);
                  return (
                    <div key={q.questionId} style={{
                      background: 'var(--surface-container-lowest)',
                      border: '1px solid var(--outline-variant)',
                      borderRadius: 'var(--radius-xl)', padding: 20,
                    }}>
                      <div className="question-number" style={{ marginBottom: 8 }}>Q{globalNum}</div>
                      {q.questionType === 'MCQ' ? (
                        <McqQuestion question={q} value={answers[q.questionId] || ''} onChange={v => handleAnswer(q.questionId, v)} />
                      ) : (
                        <FillBlankQuestion question={q} value={answers[q.questionId] || ''} onChange={v => handleAnswer(q.questionId, v)} />
                      )}
                    </div>
                  );
                })}
            </div>
          </>
        )}
      </main>

      {/* ── Submit Bar ── */}
      <div style={{
        position: 'sticky', bottom: 0, padding: '16px 32px',
        background: 'var(--surface-container-lowest)', borderTop: '1px solid var(--outline-variant)',
        display: 'flex', justifyContent: 'flex-end', gap: 12,
      }}>
        <button className="btn btn-outline" onClick={() => navigate('/listening')}>Exit</button>
        <button
          className="btn btn-primary btn-lg"
          onClick={handleSubmit}
          disabled={submitting || answeredCount === 0}
          id="submit-listening-btn"
        >
          {submitting ? 'Grading...' : `Submit (${answeredCount}/${totalQuestions})`}
        </button>
      </div>
    </div>
  );
}

/* ── MCQ Question ── */
function McqQuestion({ question, value, onChange }) {
  if (question.options && question.options.length > 0) {
    return (
      <div className="mcq-question">
        <p className="question-text">{question.questionText}</p>
        <div className="mcq-options">
          {question.options.map((opt, i) => {
            const letter = opt.label;
            return (
              <label key={opt.optionId || i} className={`mcq-option ${value === letter ? 'selected' : ''}`}>
                <input type="radio" name={`q-${question.questionId}`} value={letter}
                  checked={value === letter} onChange={() => onChange(letter)} />
                <span className="mcq-letter">{letter}</span>
                <span className="mcq-label">{opt.content}</span>
              </label>
            );
          })}
        </div>
      </div>
    );
  }

  const lines = question.questionText.split('\n');
  const stem = lines[0];
  const options = lines.slice(1).filter(l => l.trim());
  return (
    <div className="mcq-question">
      <p className="question-text">{stem}</p>
      <div className="mcq-options">
        {options.map((opt, i) => {
          const letter = opt.trim().charAt(0);
          return (
            <label key={i} className={`mcq-option ${value === letter ? 'selected' : ''}`}>
              <input type="radio" name={`q-${question.questionId}`} value={letter}
                checked={value === letter} onChange={() => onChange(letter)} />
              <span className="mcq-letter">{letter}</span>
              <span className="mcq-label">{opt.trim().substring(2).trim()}</span>
            </label>
          );
        })}
      </div>
    </div>
  );
}

/* ── Fill-in-the-Blank Question ── */
function FillBlankQuestion({ question, value, onChange }) {
  const parts = question.questionText.split('___');
  return (
    <div className="fill-blank-question">
      <p className="question-text">
        {parts.map((part, i) => (
          <span key={i}>
            {part}
            {i < parts.length - 1 && (
              <input type="text" className="fill-blank-input" value={value}
                onChange={e => onChange(e.target.value)} placeholder="your answer" />
            )}
          </span>
        ))}
      </p>
    </div>
  );
}
