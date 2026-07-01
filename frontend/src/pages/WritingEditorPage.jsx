import { useState, useEffect, useCallback, useRef } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import writingApi from '../api/writingApi';
import attemptApi from '../api/attemptApi';
import adminApi from '../api/adminApi';
import useExamTimer from '../hooks/useExamTimer';
import useExamWarnings from '../hooks/useExamWarnings';
import { useToast } from '../context/ToastContext';

const TASK1_TYPES = ['LINE_GRAPH', 'BAR_CHART', 'PIE_CHART', 'TABLE', 'MAP', 'DIAGRAM'];

const SESSION_KEY_PREFIX = 'writing_single_attemptId_';

const GRADING_CRITERIA = [
  {
    label: 'Task Achievement (TA)',
    desc: 'This criterion is based on your ability to complete all requirements of the task correctly and fully. If all parts of the question are addressed through logical arguments and accurate data, you will easily achieve a high score.',
    icon: 'task_alt'
  },
  {
    label: 'Coherence & Cohesion (CC)',
    desc: 'This criterion evaluates the clarity and logical flow of the essay. A cohesive essay is easy to read, consistent, and objectively clarifies both main and supporting points.',
    icon: 'account_tree'
  },
  {
    label: 'Lexical Resource (LR)',
    desc: 'This criterion tests the candidate\'s vocabulary range and precision. The more varied and natural the lexical choices, the higher the score. Proper spelling and collocation are also evaluated here.',
    icon: 'spellcheck'
  },
  {
    label: 'Grammatical Range & Accuracy (GRA)',
    desc: 'This criterion assesses grammatical diversity and precision. Candidates should use a mix of simple and complex sentence structures correctly. Proper punctuation is also essential.',
    icon: 'edit'
  },
];

const FORMAT_TYPE = (type) => {
  const map = {
    CAUSE_AND_EFFECT: 'Cause & Effect', PROBLEM_AND_SOLUTION: 'Problem & Solution',
    ADVANTAGES_DISADVANTAGES: 'Advantages & Disadvantages', TWO_PART_QUESTION: 'Two-Part Question',
    LINE_GRAPH: 'Line Graph', BAR_CHART: 'Bar Chart', PIE_CHART: 'Pie Chart',
    TABLE: 'Table', MAP: 'Map', DIAGRAM: 'Diagram',
  };
  return map[type] || (type ? type.charAt(0) + type.slice(1).toLowerCase() : '');
};

export default function WritingEditorPage() {
  const { promptId } = useParams();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { user } = useAuth();
  const { warning: triggerWarningToast } = useToast();
  const [prompt, setPrompt] = useState(null);
  const [essayText, setEssayText] = useState('');
  const [wordCount, setWordCount] = useState(0);
  const [loading, setLoading] = useState(true);
  const [grading, setGrading] = useState(false);
  const [error, setError] = useState('');

  const isPreviewParam = searchParams.get('preview') === 'true';
  const isPreview = isPreviewParam && user?.role === 'ADMIN';

  // Attempt / timer state
  const [attemptId, setAttemptId] = useState(null);
  const [deadline, setDeadline] = useState(null);
  const [, setSuggestedTime] = useState(null); // seconds
  const submittingRef = useRef(false);
  const essayTextRef = useRef(essayText);
  useEffect(() => { essayTextRef.current = essayText; }, [essayText]);

  const isTask1 = prompt ? TASK1_TYPES.includes(prompt.essayType) : false;
  const minWords = isTask1 ? 150 : 250;

  // ── Load prompt ──
  useEffect(() => {
    const fetchPrompt = async () => {
      try {
        let promptData;
        if (isPreview) {
          const res = await adminApi.getWritingPromptPreview(promptId);
          promptData = res.data.data;
        } else {
          const res = await writingApi.getPromptById(promptId);
          promptData = res.data.data;
        }
        setPrompt(promptData);
      } catch (err) {
        setError('Failed to load prompt.');
      } finally {
        setLoading(false);
      }
    };
    fetchPrompt();
  }, [promptId, isPreview]);

  // ── Start / resume attempt after prompt loads ──
  useEffect(() => {
    if (!prompt || isPreview) return;

    const sessionKey = SESSION_KEY_PREFIX + promptId;

    const initAttempt = async () => {
      try {
        const storedAttemptId = sessionStorage.getItem(sessionKey);
        let attempt = null;

        // Try to resume existing attempt
        if (storedAttemptId) {
          try {
            const res = await attemptApi.getAttempt(storedAttemptId);
            attempt = res.data.data;
            if (attempt.status !== 'IN_PROGRESS') attempt = null;
          } catch (_e) {
            attempt = null;
          }
        }

        // Create new attempt if none found
        if (!attempt) {
          const res = await attemptApi.startAttempt({
            skillType: 'WRITING',
            examReferenceIds: JSON.stringify([Number(promptId)]),
          });
          attempt = res.data.data;
        }

        setAttemptId(attempt.attemptId);
        setDeadline(attempt.deadline);
        sessionStorage.setItem(sessionKey, String(attempt.attemptId));

        // Set suggested time based on task type
        const task1 = TASK1_TYPES.includes(prompt.essayType);
        setSuggestedTime(task1
          ? (attempt.suggestedTask1Duration || 1200)
          : (attempt.suggestedTask2Duration || 2400));
      } catch (err) {
        // Timer is non-blocking — if attempt fails, user can still write without timer
        console.warn('Failed to initialize exam attempt for timer:', err);
      }
    };

    initAttempt();
  }, [prompt, promptId, isPreview]);

  // ── Auto-submit when timer expires ──
  const handleAutoSubmit = useCallback(() => {
    if (submittingRef.current) return;
    submittingRef.current = true;
    setGrading(true);
    setError('');

    const currentEssay = essayTextRef.current;
    const currentWordCount = currentEssay?.trim()
      ? currentEssay.trim().split(/\s+/).filter(w => w.length > 0).length
      : 0;

    const sessionKey = SESSION_KEY_PREFIX + promptId;
    const storedAttemptId = attemptId || sessionStorage.getItem(sessionKey);

    // Complete the attempt first
    if (storedAttemptId) {
      attemptApi.completeAttempt(storedAttemptId, { autoSubmitted: true }).catch(() => {});
    }

    // If essay meets minimum, grade it; otherwise show time-expired message
    if (currentWordCount >= (isTask1 ? 150 : 250)) {
      writingApi.gradeEssay(Number(promptId), currentEssay)
        .then(res => {
          sessionStorage.removeItem(sessionKey);
          navigate(`/writing/result/${res.data.data.submissionId}`, { replace: true });
        })
        .catch(() => {
          setError('Auto-submit failed. Please try submitting manually.');
          setGrading(false);
          submittingRef.current = false;
        });
    } else {
      // Not enough words — notify user but don't block
      sessionStorage.removeItem(sessionKey);
      alert(`⏰ Time is up! Your essay has ${currentWordCount} words (minimum: ${isTask1 ? 150 : 250}). The essay was not graded because it did not meet the minimum word count.`);
      setGrading(false);
      submittingRef.current = false;
      navigate('/writing', { replace: true });
    }
  }, [attemptId, promptId, isTask1, navigate]);

  // ── Server-authoritative countdown timer ──
  const { timeLeft, isWarning, isCritical, formattedTime, stopTimer } = useExamTimer({
    deadline,
    onTimeUp: handleAutoSubmit,
    enabled: !loading && !grading && !!deadline,
  });

  // Centralized 5min/1min audio-visual warnings
  useExamWarnings({
    timeLeft,
    enabled: !loading && !grading && !!deadline,
    showWarning: triggerWarningToast,
  });

  // ── Word count ──
  const countWords = useCallback((text) => {
    if (!text?.trim()) return 0;
    return text.trim().split(/\s+/).filter(w => w.length > 0).length;
  }, []);

  useEffect(() => {
    const t = setTimeout(() => setWordCount(countWords(essayText)), 300);
    return () => clearTimeout(t);
  }, [essayText, countWords]);

  // ── Manual submit (grade) ──
  const handleGrade = async () => {
    if (wordCount < minWords || submittingRef.current) return;
    submittingRef.current = true;
    setGrading(true);
    setError('');
    stopTimer();

    const sessionKey = SESSION_KEY_PREFIX + promptId;

    try {
      const res = await writingApi.gradeEssay(Number(promptId), essayText);

      // Complete the attempt
      const storedAttemptId = attemptId || sessionStorage.getItem(sessionKey);
      if (storedAttemptId) {
        await attemptApi.completeAttempt(storedAttemptId, { autoSubmitted: false }).catch(() => {});
      }

      sessionStorage.removeItem(sessionKey);
      navigate(`/writing/result/${res.data.data.submissionId}`);
    } catch (err) {
      setError(err.response?.data?.message || 'Grading failed. Please try again.');
      submittingRef.current = false;
    } finally {
      setGrading(false);
    }
  };

  if (loading) return <div className="loading-screen"><span className="spinner" style={{ width: 24, height: 24 }} />Loading...</div>;

  const pct = Math.min(100, Math.round((wordCount / minWords) * 100));
  const isOk = wordCount >= minWords;

  // Timer visual states
  const timerColor = isCritical ? 'var(--error)' : isWarning ? 'var(--error)' : 'var(--on-surface)';

  return (
    <div className="writing-editor-page" style={{ display: 'flex', flexDirection: 'column', height: '100vh', overflow: 'hidden' }}>
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
          position: 'relative',
          flexShrink: 0
        }}>
          <span className="material-symbols-outlined" style={{ fontSize: 18, color: '#f57c00' }}>warning</span>
          <span>⚠️ PREVIEW MODE — Bạn đang xem với tư cách Admin. Bài làm sẽ không được lưu.</span>
        </div>
      )}

      {/* ── Header ── */}
      <header style={{
        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
        padding: '0 24px', height: 64, flexShrink: 0,
        background: 'var(--surface-container-lowest)',
        borderBottom: '1px solid var(--outline-variant)',
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          <button
            className="btn-back"
            onClick={() => navigate(isPreview ? '/admin/writing-prompts' : '/writing')}
            id="back-to-prompts"
            style={{ margin: 0 }}
          >
            <span className="material-symbols-outlined" style={{ fontSize: 20 }}>arrow_back</span>
            {isPreview ? '← Quay lại Admin' : 'Prompts'}
          </button>
          <div style={{ width: 1, height: 24, background: 'var(--outline-variant)' }} />
          <span style={{
            padding: '4px 12px', borderRadius: 'var(--radius-full)',
            background: 'rgba(0,63,177,0.06)', color: 'var(--primary)',
            fontSize: '0.75rem', fontWeight: 600,
          }}>
            {isTask1 ? 'Task 1' : 'Task 2'} · {FORMAT_TYPE(prompt?.essayType)}
          </span>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          {error && <span style={{ color: 'var(--error)', fontSize: '0.875rem' }}>{error}</span>}

          {/* Countdown timer pill */}
          {deadline && !isPreview && (
            <div
              className={`exam-timer-pill ${isCritical ? 'warning' : isWarning ? 'warning' : ''}`}
              id="writing-countdown-timer"
              style={{
                display: 'flex', alignItems: 'center', gap: 6,
                padding: '6px 14px',
                background: isCritical ? 'rgba(186,26,26,0.12)' : undefined,
                color: timerColor,
                fontWeight: 700, fontSize: '1rem',
                border: isCritical ? '1px solid var(--error)' : undefined,
                animation: isCritical ? 'pulse 1s ease-in-out infinite' : undefined,
              }}
              title="Time remaining for this writing session"
            >
              <span className="material-symbols-outlined" style={{ fontSize: 18 }}>timer</span>
              {formattedTime}
            </div>
          )}

          <button
            className="btn btn-primary btn-grade"
            onClick={handleGrade}
            disabled={isPreview || !isOk || grading}
            title={isPreview ? "Không thể nộp ở chế độ preview" : undefined}
            id="grade-essay-btn"
          >
            {grading ? <><span className="spinner" />AI is grading...</> : 'Submit for Grading'}
          </button>
        </div>
      </header>

      {/* ── Suggested time indicator ── */}
      {deadline && !isPreview && (
        <div className="writing-suggested-time" id="writing-suggested-time">
          <span className="material-symbols-outlined" style={{ fontSize: 16, color: 'var(--primary)' }}>info</span>
          <span>
            {isTask1
              ? 'Suggested: ~20 minutes for Task 1 (Report)'
              : 'Suggested: ~40 minutes for Task 2 (Essay)'}
          </span>
          <span style={{ color: 'var(--outline)' }}>·</span>
          <span>Total: 60 minutes</span>
        </div>
      )}

      {/* ── 3-Pane Body ── */}
      <div style={{ display: 'flex', flex: 1, overflow: 'hidden' }}>

        {/* Left Pane: Prompt */}
        <div style={{
          width: '35%', minWidth: 280, overflowY: 'auto', padding: '32px 24px',
          background: 'var(--surface-container-low)',
          borderRight: '1px solid var(--outline-variant)',
        }}>
          <p style={{ fontSize: '0.72rem', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.08em', color: 'var(--primary)', marginBottom: 8 }}>
            Prompt
          </p>
          <h2 style={{ fontFamily: 'var(--font-heading)', fontSize: '1.5rem', fontWeight: 700, color: 'var(--on-surface)', marginBottom: 16 }}>
            {isTask1 ? 'Writing Task 1' : 'Writing Task 2'}
          </h2>

          {/* Prompt quote box */}
          <div style={{
            padding: 20, borderRadius: 'var(--radius-lg)',
            background: 'var(--surface-container-lowest)',
            border: '1px solid var(--outline-variant)',
            marginBottom: 16,
          }}>
            <p style={{ fontSize: '0.95rem', lineHeight: 1.75, color: 'var(--on-surface)' }}>
              {prompt?.promptText}
            </p>
          </div>

          {/* Task 1 image */}
          {prompt?.imageUrl && (
            <div className="prompt-image-container">
              <img src={prompt.imageUrl} alt="Prompt Chart" className="prompt-image" />
            </div>
          )}

          <p style={{ fontSize: '0.82rem', color: 'var(--on-surface-variant)', marginTop: 12, fontStyle: 'italic' }}>
            {isTask1
              ? `Write at least ${minWords} words. Describe key features and make comparisons.`
              : `Write at least ${minWords} words. Provide reasons and relevant examples.`}
          </p>
        </div>

        {/* Center Pane: Editor */}
        <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
          <textarea
            className="editor-textarea"
            value={essayText}
            onChange={e => setEssayText(e.target.value)}
            placeholder={isTask1
              ? 'Start writing your chart description here...'
              : 'Start writing your essay here...'}
            id="essay-editor"
            style={{
              flex: 1, resize: 'none', border: 'none', outline: 'none',
              borderRadius: 0, boxShadow: 'none', padding: '32px',
              background: 'var(--surface-container-lowest)',
              borderBottom: '1px solid var(--outline-variant)',
            }}
          />

          {/* Word count status bar */}
          <div style={{
            display: 'flex', alignItems: 'center', gap: 16,
            padding: '10px 32px', background: 'var(--surface-container-low)',
            flexShrink: 0,
          }}>
            {/* Progress bar */}
            <div style={{ flex: 1, height: 4, background: 'var(--surface-variant)', borderRadius: 'var(--radius-full)', overflow: 'hidden' }}>
              <div style={{
                height: '100%', borderRadius: 'var(--radius-full)',
                background: isOk ? 'var(--secondary)' : 'var(--primary)',
                width: `${pct}%`, transition: 'width 0.3s ease',
              }} />
            </div>
            <div className={`word-count ${isOk ? 'count-ok' : 'count-low'}`}>
              <strong>{wordCount}</strong> / {minWords} words
              {!isOk && <span className="count-warning"> · need {minWords - wordCount} more</span>}
            </div>
          </div>
        </div>

        {/* Right Pane: Grading Criteria (hidden on small screens) */}
        <div style={{
          width: 260, flexShrink: 0, overflowY: 'auto',
          padding: '32px 20px', background: 'var(--surface-container-low)',
          borderLeft: '1px solid var(--outline-variant)',
          display: 'flex', flexDirection: 'column', gap: 16,
        }}>
          <p style={{ fontSize: '0.72rem', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.08em', color: 'var(--on-surface-variant)', marginBottom: 4 }}>
            Grading Criteria
          </p>
          {GRADING_CRITERIA.map(c => (
            <div key={c.label} style={{
              padding: 16, borderRadius: 'var(--radius-lg)',
              background: 'var(--surface-container-lowest)',
              border: '1px solid var(--outline-variant)',
            }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 6 }}>
                <span className="material-symbols-outlined" style={{ fontSize: 20, color: 'var(--primary)' }}>{c.icon}</span>
                <span style={{ fontFamily: 'var(--font-heading)', fontWeight: 600, fontSize: '0.875rem' }}>{c.label}</span>
              </div>
              <p style={{ fontSize: '0.75rem', color: 'var(--on-surface-variant)', lineHeight: 1.5 }}>{c.desc}</p>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
