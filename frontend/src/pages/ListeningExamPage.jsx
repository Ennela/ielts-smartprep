import { useState, useEffect, useMemo, useRef, useCallback } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import listeningApi from '../api/listeningApi';
import attemptApi from '../api/attemptApi';
import adminApi from '../api/adminApi';
import AudioPlayer from '../components/listening/AudioPlayer';
import useExamTimer from '../hooks/useExamTimer';
import useExamWarnings from '../hooks/useExamWarnings';
import { useToast } from '../context/ToastContext';

const SESSION_KEY = 'listening_attemptId';

export default function ListeningExamPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { user } = useAuth();
  const mode = searchParams.get('mode') || 'practice';
  const { warning: triggerWarningToast } = useToast();
  const partIds = useMemo(() =>
    (searchParams.get('parts') || '').split(',').map(Number).filter(Boolean),
  [searchParams]);

  const isPreviewParam = searchParams.get('preview') === 'true';
  const isPreview = isPreviewParam && user?.role === 'ADMIN';

  const [parts, setParts] = useState([]);
  const [answers, setAnswers] = useState({});
  const [loading, setLoading] = useState(true);
  const [polling, setPolling] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [currentPartIndex, setCurrentPartIndex] = useState(0);

  // Server-authoritative attempt state
  const [attemptId, setAttemptId] = useState(null);
  const [deadline, setDeadline] = useState(null);
  const submittingRef = useRef(false);
  const answersRef = useRef(answers);

  // Keep answers ref current for auto-submit
  useEffect(() => { answersRef.current = answers; }, [answers]);

  // Auto-submit handler
  const handleAutoSubmit = useCallback(() => {
    if (isPreview || submittingRef.current) return;
    submittingRef.current = true;
    setSubmitting(true);

    const storedAttemptId = attemptId || sessionStorage.getItem(SESSION_KEY);

    listeningApi.submitTest(
      mode === 'mock-test' ? 'MOCK_TEST' : 'PRACTICE',
      partIds,
      answersRef.current,
      storedAttemptId ? Number(storedAttemptId) : undefined,
      true, // autoSubmitted
    ).then(res => {
      sessionStorage.removeItem(SESSION_KEY);
      navigate(`/listening/result/${res.data?.data?.testId}`, { state: res.data?.data });
    }).catch(err => {
      console.error(err);
      alert('Auto-submit failed. Please try submitting manually.');
      setSubmitting(false);
      submittingRef.current = false;
    });
  }, [attemptId, mode, partIds, navigate, isPreview]);

  // Server-authoritative timer (active for ALL modes)
  const { timeLeft, isWarning, isCritical, formattedTime, stopTimer } = useExamTimer({
    deadline,
    onTimeUp: handleAutoSubmit,
    enabled: !loading && !polling && !submitting && parts.length > 0 && !!deadline && !isPreview,
  });

  // Centralized 5min/1min audio-visual warnings
  useExamWarnings({
    timeLeft,
    enabled: !loading && !polling && !submitting && parts.length > 0 && !!deadline && !isPreview,
    showWarning: triggerWarningToast,
  });

  // Load parts + start/resume attempt
  useEffect(() => {
    let active = true;
    let timerId = null;

    const loadParts = async () => {
      try {
        const loaded = [];
        for (const id of partIds) {
          const res = isPreview
            ? await adminApi.getListeningPartPreview(id)
            : await listeningApi.getPartById(id);
          if (res.data?.data) loaded.push(res.data.data);
        }

        if (!active) return;

        // Check if any loaded part needs polling
        const needsPolling = loaded.some(p => p.audioStatus === 'PENDING');

        if (needsPolling) {
          setParts(loaded);
          setPolling(true);
          setLoading(false);

          const poll = async () => {
            try {
              const updated = [];
              let allReady = true;
              for (const p of loaded) {
                if (p.audioStatus === 'PENDING') {
                  const res = isPreview
                    ? await adminApi.getListeningPartPreview(p.partId)
                    : await listeningApi.getPartById(p.partId);
                  const latest = res.data?.data;
                  if (latest) {
                    updated.push(latest);
                    if (latest.audioStatus === 'PENDING') {
                      allReady = false;
                    }
                  } else {
                    updated.push(p);
                    allReady = false;
                  }
                } else {
                  updated.push(p);
                }
              }

              if (!active) return;
              setParts(updated);

              if (allReady) {
                setPolling(false);
                // Start attempt after audio is ready
                if (!isPreview) {
                  await initAttempt();
                } else {
                  setAttemptId(null);
                  setDeadline(null);
                }
              } else {
                timerId = setTimeout(poll, 3000);
              }
            } catch (err) {
              console.error("Polling error:", err);
              if (active) timerId = setTimeout(poll, 3000);
            }
          };

          timerId = setTimeout(poll, 3000);
        } else {
          setParts(loaded);
          setPolling(false);
          setLoading(false);

          // Start/resume attempt for all modes
          if (!isPreview) {
            await initAttempt();
          } else {
            setAttemptId(null);
            setDeadline(null);
          }
        }
      } catch (err) {
        console.error(err);
        if (active) setLoading(false);
      }
    };

    const initAttempt = async () => {
      try {
        const storedAttemptId = sessionStorage.getItem(SESSION_KEY);
        let attempt;

        if (storedAttemptId) {
          try {
            const res = await attemptApi.getAttempt(storedAttemptId);
            attempt = res.data.data;
            if (attempt.status !== 'IN_PROGRESS') attempt = null;
          } catch (_e) { attempt = null; }
        }

        if (!attempt) {
          const res = await attemptApi.startAttempt({
            skillType: 'LISTENING',
            examReferenceIds: JSON.stringify(partIds),
          });
          attempt = res.data.data;
        }

        if (active) {
          setAttemptId(attempt.attemptId);
          setDeadline(attempt.deadline);
          sessionStorage.setItem(SESSION_KEY, String(attempt.attemptId));
        }
      } catch (err) {
        console.error("Failed to start attempt:", err);
      }
    };

    if (partIds.length) {
      setLoading(true);
      loadParts();
    }

    return () => {
      active = false;
      if (timerId) clearTimeout(timerId);
    };
  }, [partIds, isPreview]);

  const handleAnswer = (questionId, value) =>
    setAnswers(prev => ({ ...prev, [questionId]: value }));

  const handleSubmit = async () => {
    if (submitting || submittingRef.current) return;
    submittingRef.current = true;
    setSubmitting(true);
    stopTimer();

    try {
      const res = await listeningApi.submitTest(
        mode === 'mock-test' ? 'MOCK_TEST' : 'PRACTICE',
        partIds,
        answers,
        attemptId || undefined,
        false, // not auto-submitted
      );
      sessionStorage.removeItem(SESSION_KEY);
      navigate(`/listening/result/${res.data?.data?.testId}`, { state: res.data?.data });
    } catch (err) {
      console.error(err);
      alert('Submission failed');
      setSubmitting(false);
      submittingRef.current = false;
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

  if (polling) {
    const readyCount = parts.filter(p => p.audioStatus === 'READY' || p.audioStatus === 'FAILED').length;
    const totalCount = parts.length;
    return (
      <div className="listening-page" style={{ display: 'flex', flexDirection: 'column', justifyContent: 'center', alignItems: 'center', minHeight: '100vh', gap: '24px', padding: '32px' }}>
        <div className="loading-spinner"><div className="spinner" /></div>
        <div style={{ textAlign: 'center' }}>
          <h2 style={{ fontFamily: 'var(--font-heading)', color: 'var(--primary)', marginBottom: '8px' }}>Generating Exam Audio...</h2>
          <p style={{ color: 'var(--on-surface-variant)', fontSize: '0.95rem' }}>
            We are generating high-quality IELTS voices for your test ({readyCount}/{totalCount} parts ready).
          </p>
          <p style={{ color: 'var(--on-surface-variant)', fontSize: '0.85rem', marginTop: '4px' }}>
            This process typically takes 10-20 seconds. Please do not refresh.
          </p>
        </div>
      </div>
    );
  }

  // Timer display helpers
  const timerColor = isCritical ? 'var(--error)' : isWarning ? 'var(--error)' : 'var(--on-surface)';

  return (
    <div className="listening-page" style={{ display: 'flex', flexDirection: 'column', minHeight: '100vh' }}>
      {isPreview && (
        <div style={{
          background: '#fff9c4',
          color: '#5d4037',
          padding: '8px 24px',
          textAlign: 'center',
          fontWeight: 600,
          fontSize: '0.9rem',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          gap: '8px',
          borderBottom: '1px solid #fbc02d',
          zIndex: 1100,
          position: 'relative'
        }}>
          <span className="material-symbols-outlined" style={{ fontSize: 18, color: '#f57c00' }}>warning</span>
          <span>⚠️ PREVIEW MODE — Bạn đang xem với tư cách Admin. Bài làm sẽ không được lưu.</span>
        </div>
      )}

      {/* ── Header ── */}
      <header style={{
        padding: '16px 32px', borderBottom: '1px solid var(--outline-variant)',
        background: 'var(--surface-container-lowest)', flexShrink: 0,
      }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 12, cursor: 'pointer' }}
              onClick={() => navigate(isPreview ? '/admin/listening' : '/listening')}>
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
          <div style={{ textAlign: 'right', display: 'flex', alignItems: 'center', gap: '16px' }}>
            {deadline && !isPreview && (
              <div style={{
                display: 'flex',
                alignItems: 'center',
                gap: '6px',
                padding: '6px 12px',
                borderRadius: 'var(--radius-md)',
                background: isCritical ? 'rgba(186,26,26,0.1)' : 'var(--surface-container-high)',
                color: timerColor,
                fontWeight: '700',
                fontSize: '1rem',
                fontFamily: 'monospace',
                border: isCritical ? '1px solid var(--error)' : '1px solid var(--outline-variant)',
                animation: isCritical ? 'pulse 1s ease-in-out infinite' : 'none',
              }}>
                <span className="material-symbols-outlined" style={{ fontSize: '18px', verticalAlign: 'middle' }}>alarm</span>
                {formattedTime}
              </div>
            )}
            <div>
              <p style={{ fontSize: '0.875rem', color: 'var(--on-surface-variant)' }}>
                Answered <strong style={{ color: 'var(--on-surface)' }}>{answeredCount}</strong> / {totalQuestions}
              </p>
            </div>
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

            {/* Transcript Card (Admin Preview only) */}
            {isPreview && currentPart.transcriptText && (
              <div style={{
                background: 'var(--surface-container-lowest)', border: '1px solid var(--outline-variant)',
                borderRadius: 'var(--radius-xl)', padding: 24, marginBottom: 32,
              }}>
                <h4 style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 12, color: 'var(--primary)', fontFamily: 'var(--font-heading)' }}>
                  <span className="material-symbols-outlined">description</span>
                  Transcript (Preview Mode)
                </h4>
                <div style={{
                  maxHeight: '200px', overflowY: 'auto', fontSize: '0.9rem', lineHeight: 1.6,
                  color: 'var(--on-surface-variant)', background: 'var(--surface-container-low)',
                  padding: 16, borderRadius: 'var(--radius-md)', whiteSpace: 'pre-line'
                }}>
                  {currentPart.transcriptText}
                </div>
              </div>
            )}

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

                      {isPreview && q.correctAnswer && (
                        <div style={{
                          marginTop: '12px',
                          padding: '6px 12px',
                          backgroundColor: 'rgba(0,108,74,0.06)',
                          color: '#006c4a',
                          borderRadius: '4px',
                          fontSize: '0.85rem',
                          fontWeight: 600,
                          border: '1px solid rgba(0,108,74,0.15)',
                          display: 'inline-block'
                        }}>
                          Đáp án đúng: {q.correctAnswer}
                        </div>
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
        <button
          className="btn btn-outline"
          onClick={() => navigate(isPreview ? '/admin/listening' : '/listening')}
        >
          {isPreview ? '← Quay lại Admin' : 'Exit'}
        </button>
        <button
          className="btn btn-primary btn-lg"
          onClick={handleSubmit}
          disabled={isPreview || submitting || answeredCount === 0}
          title={isPreview ? "Không thể nộp ở chế độ preview" : undefined}
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
