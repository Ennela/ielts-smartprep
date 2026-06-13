import { useState, useEffect, useCallback, useRef } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import writingApi from '../api/writingApi';
import attemptApi from '../api/attemptApi';
import VisualDataRenderer from '../components/writing/VisualDataRenderer';
import useExamTimer from '../hooks/useExamTimer';
import useWritingTaskTimer from '../hooks/useWritingTaskTimer';

const SESSION_KEY = 'writing_full_attemptId';

export default function WritingFullExamPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  const [task1, setTask1] = useState(null);
  const [task2, setTask2] = useState(null);
  const [task1Text, setTask1Text] = useState('');
  const [task2Text, setTask2Text] = useState('');
  const [activeTab, setActiveTab] = useState(1); // 1 or 2
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  // Server-authoritative attempt state
  const [attemptId, setAttemptId] = useState(null);
  const [deadline, setDeadline] = useState(null);
  const [suggestedTask1, setSuggestedTask1] = useState(1200);
  const [suggestedTask2, setSuggestedTask2] = useState(2400);
  const submittingRef = useRef(false);

  // Per-task time tracking
  const {
    task1TimeSpent, task2TimeSpent,
    isTask1OverSuggested, isTask2OverSuggested,
    getFinalTimes,
  } = useWritingTaskTimer({
    activeTask: activeTab,
    enabled: !loading && !submitting && !!deadline,
    suggestedTask1,
    suggestedTask2,
  });

  // Keep refs for auto-submit
  const task1TextRef = useRef(task1Text);
  const task2TextRef = useRef(task2Text);
  useEffect(() => { task1TextRef.current = task1Text; }, [task1Text]);
  useEffect(() => { task2TextRef.current = task2Text; }, [task2Text]);

  const getFinalTimesRef = useRef(getFinalTimes);
  useEffect(() => { getFinalTimesRef.current = getFinalTimes; }, [getFinalTimes]);

  // Auto-submit handler
  const handleAutoSubmit = useCallback(() => {
    if (submittingRef.current) return;
    submittingRef.current = true;
    setSubmitting(true);

    const storedAttemptId = attemptId || sessionStorage.getItem(SESSION_KEY);
    const times = getFinalTimesRef.current();

    writingApi.submitFullWriting({
      task1PromptId: task1?.promptId,
      task1EssayText: task1TextRef.current || '(Time expired - no answer)',
      task2PromptId: task2?.promptId,
      task2EssayText: task2TextRef.current || '(Time expired - no answer)',
      attemptId: storedAttemptId ? Number(storedAttemptId) : undefined,
      autoSubmitted: true,
      timeSpentTask1: times.timeSpentTask1,
      timeSpentTask2: times.timeSpentTask2,
    }).then(res => {
      sessionStorage.removeItem(SESSION_KEY);
      navigate('/writing/full-result', { state: { result: res.data.data }, replace: true });
    }).catch(err => {
      console.error(err);
      alert('Auto-submit failed. Please try submitting manually.');
      setSubmitting(false);
      submittingRef.current = false;
    });
  }, [attemptId, task1, task2, navigate]);

  // Server-authoritative timer
  const { timeLeft, isWarning, isCritical, formattedTime, stopTimer, formatTime } = useExamTimer({
    deadline,
    onTimeUp: handleAutoSubmit,
    enabled: !loading && !submitting && !error && !!deadline,
  });

  // Load prompts + start/resume attempt
  useEffect(() => {
    const init = async () => {
      try {
        const t1Id = searchParams.get('task1');
        const t2Id = searchParams.get('task2');

        if (!t1Id || !t2Id) {
          setError('Missing task prompts');
          setLoading(false);
          return;
        }

        const [t1Res, t2Res] = await Promise.all([
          writingApi.getPromptById(t1Id),
          writingApi.getPromptById(t2Id),
        ]);
        setTask1(t1Res.data.data);
        setTask2(t2Res.data.data);

        // Restore drafts
        try {
          const d1 = localStorage.getItem(`writing_draft_${t1Id}`);
          const d2 = localStorage.getItem(`writing_draft_${t2Id}`);
          if (d1) setTask1Text(d1);
          if (d2) setTask2Text(d2);
        } catch (e) {}

        // Start or resume attempt
        const storedAttemptId = sessionStorage.getItem(SESSION_KEY);
        let attempt;

        if (storedAttemptId) {
          try {
            const res = await attemptApi.getAttempt(storedAttemptId);
            attempt = res.data.data;
            if (attempt.status !== 'IN_PROGRESS') attempt = null;
          } catch (e) { attempt = null; }
        }

        if (!attempt) {
          const res = await attemptApi.startAttempt({
            skillType: 'WRITING',
            examReferenceIds: JSON.stringify([Number(t1Id), Number(t2Id)]),
          });
          attempt = res.data.data;
        }

        setAttemptId(attempt.attemptId);
        setDeadline(attempt.deadline);
        if (attempt.suggestedTask1Duration) setSuggestedTask1(attempt.suggestedTask1Duration);
        if (attempt.suggestedTask2Duration) setSuggestedTask2(attempt.suggestedTask2Duration);
        sessionStorage.setItem(SESSION_KEY, String(attempt.attemptId));
      } catch (err) {
        setError('Failed to load writing test.');
      } finally {
        setLoading(false);
      }
    };

    init();
  }, [searchParams]);

  // Save drafts
  useEffect(() => {
    if (task1?.promptId) {
      try { localStorage.setItem(`writing_draft_${task1.promptId}`, task1Text); } catch (e) {}
    }
  }, [task1Text, task1?.promptId]);

  useEffect(() => {
    if (task2?.promptId) {
      try { localStorage.setItem(`writing_draft_${task2.promptId}`, task2Text); } catch (e) {}
    }
  }, [task2Text, task2?.promptId]);

  const handleSubmit = async () => {
    if (submitting || submittingRef.current) return;
    submittingRef.current = true;
    setSubmitting(true);
    stopTimer();

    const times = getFinalTimes();

    try {
      const res = await writingApi.submitFullWriting({
        task1PromptId: task1?.promptId,
        task1EssayText: task1Text || '(No answer)',
        task2PromptId: task2?.promptId,
        task2EssayText: task2Text || '(No answer)',
        attemptId: attemptId || undefined,
        autoSubmitted: false,
        timeSpentTask1: times.timeSpentTask1,
        timeSpentTask2: times.timeSpentTask2,
      });
      sessionStorage.removeItem(SESSION_KEY);
      try {
        localStorage.removeItem(`writing_draft_${task1?.promptId}`);
        localStorage.removeItem(`writing_draft_${task2?.promptId}`);
      } catch (e) {}
      navigate('/writing/full-result', { state: { result: res.data.data }, replace: true });
    } catch (err) {
      const msg = err.response?.data?.message || 'Submission failed';
      setError(msg);
      setSubmitting(false);
      submittingRef.current = false;
    }
  };

  if (loading) return (
    <div className="loading-screen">
      <div className="loading-spinner"><div className="spinner" /></div>
      <p style={{ marginTop: 16, color: 'var(--on-surface-variant)' }}>Loading Writing Test...</p>
    </div>
  );

  if (error) {
    return (
      <div className="loading-screen">
        <div>
          <p style={{ color: 'var(--error)' }}>{error}</p>
          <button className="btn btn-primary" onClick={() => navigate('/writing')} style={{ marginTop: 16 }}>
            Go Back
          </button>
        </div>
      </div>
    );
  }

  const activeTask = activeTab === 1 ? task1 : task2;
  const activeText = activeTab === 1 ? task1Text : task2Text;
  const setActiveText = activeTab === 1 ? setTask1Text : setTask2Text;
  const wordCount = activeText.trim() ? activeText.trim().split(/\s+/).length : 0;
  const minWords = activeTab === 1 ? 150 : 250;

  // Timer styles
  const timerColor = isCritical ? 'var(--error)' : isWarning ? 'var(--error)' : 'var(--on-surface)';

  // Per-task time helpers
  const formatTaskTime = (seconds) => {
    const m = Math.floor(seconds / 60);
    const s = seconds % 60;
    return `${m}:${s.toString().padStart(2, '0')}`;
  };

  return (
    <div className="writing-exam-page" id="writing-exam-page" style={{ display: 'flex', flexDirection: 'column', minHeight: '100vh' }}>

      {/* ── Header ── */}
      <header className="exam-topbar" style={{ flexShrink: 0 }}>
        <div className="exam-topbar-left">
          <span className="exam-logo">SmartPrep</span>
          <div className="exam-divider-v" />
          <span className="exam-topic-badge">Writing Full Test</span>
        </div>

        <div className="exam-topbar-center" style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
          <button
            onClick={() => setActiveTab(1)}
            className={`btn ${activeTab === 1 ? 'btn-primary' : 'btn-outline'}`}
            style={{ padding: '6px 16px', borderRadius: 'var(--radius-md)', fontSize: '0.875rem', position: 'relative' }}
          >
            Task 1
            {isTask1OverSuggested && (
              <span style={{
                position: 'absolute', top: -4, right: -4,
                width: 8, height: 8, borderRadius: '50%',
                background: 'var(--error)',
              }} />
            )}
          </button>
          <button
            onClick={() => setActiveTab(2)}
            className={`btn ${activeTab === 2 ? 'btn-primary' : 'btn-outline'}`}
            style={{ padding: '6px 16px', borderRadius: 'var(--radius-md)', fontSize: '0.875rem', position: 'relative' }}
          >
            Task 2
            {isTask2OverSuggested && (
              <span style={{
                position: 'absolute', top: -4, right: -4,
                width: 8, height: 8, borderRadius: '50%',
                background: 'var(--error)',
              }} />
            )}
          </button>
        </div>

        <div className="exam-topbar-right">
          {/* Main countdown timer */}
          <div className="exam-timer" style={{
            display: 'flex', alignItems: 'center', gap: 8,
            padding: '6px 12px',
            background: isCritical ? 'rgba(186,26,26,0.12)' : 'var(--surface-container-high)',
            borderRadius: 'var(--radius-md)',
            color: timerColor,
            fontWeight: 700, fontSize: '1.1rem',
            border: isCritical ? '1px solid var(--error)' : 'none',
            animation: isCritical ? 'pulse 1s ease-in-out infinite' : 'none',
          }}>
            <span className="material-symbols-outlined" style={{ fontSize: 20 }}>timer</span>
            {formattedTime}
          </div>
          <button
            className="btn btn-primary btn-submit-exam"
            onClick={handleSubmit}
            disabled={submitting}
            id="submit-writing-btn"
          >
            {submitting ? 'Grading...' : 'Submit'}
          </button>
        </div>
      </header>

      {/* ── Per-task time indicator ── */}
      <div style={{
        display: 'flex', justifyContent: 'center', gap: 32,
        padding: '8px 16px',
        background: 'var(--surface-container-low)',
        borderBottom: '1px solid var(--outline-variant)',
        fontSize: '0.8rem',
        color: 'var(--on-surface-variant)',
      }}>
        <span style={{ color: isTask1OverSuggested ? 'var(--error)' : 'inherit', fontWeight: isTask1OverSuggested ? 600 : 400 }}>
          Task 1: {formatTaskTime(task1TimeSpent)} spent
          {isTask1OverSuggested ? ' ⚠ over suggested 20 min' : ` · suggested ${formatTaskTime(suggestedTask1)}`}
        </span>
        <span style={{ color: 'var(--outline-variant)' }}>|</span>
        <span style={{ color: isTask2OverSuggested ? 'var(--error)' : 'inherit', fontWeight: isTask2OverSuggested ? 600 : 400 }}>
          Task 2: {formatTaskTime(task2TimeSpent)} spent
          {isTask2OverSuggested ? ' ⚠ over suggested 40 min' : ` · suggested ${formatTaskTime(suggestedTask2)}`}
        </span>
      </div>

      {/* ── Main Content ── */}
      <div className="writing-exam-split" style={{ flex: 1, display: 'grid', gridTemplateColumns: '1fr 1fr', overflow: 'hidden' }}>

        {/* Left: Prompt */}
        <div className="writing-prompt-panel" style={{
          padding: '2rem', overflowY: 'auto',
          background: 'var(--surface-container-lowest)',
          borderRight: '1px solid var(--outline-variant)',
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 16 }}>
            <span className={`badge ${activeTab === 1 ? 'badge-practice' : 'badge-mock'}`}>
              Task {activeTab}
            </span>
            <span style={{ fontSize: '0.85rem', color: 'var(--on-surface-variant)' }}>
              {activeTab === 1 ? 'You should spend about 20 minutes on this task.' : 'You should spend about 40 minutes on this task.'}
            </span>
          </div>

          {activeTask && (
            <>
              <h3 style={{ fontSize: '1.1rem', fontWeight: 600, marginBottom: 16 }}>
                {activeTask.essayType || (activeTab === 1 ? 'Task 1' : 'Task 2')}
              </h3>
              <p style={{ lineHeight: 1.8, fontSize: '0.95rem', whiteSpace: 'pre-wrap' }}>
                {activeTask.promptText}
              </p>
              {activeTab === 1 && activeTask.visualData && (
                <div style={{ marginTop: 16 }}>
                  <VisualDataRenderer visualDataJson={activeTask.visualData} essayType={activeTask.essayType} />
                </div>
              )}
              <p style={{ marginTop: 20, fontSize: '0.85rem', color: 'var(--on-surface-variant)', fontStyle: 'italic' }}>
                Write at least {minWords} words.
              </p>
            </>
          )}
        </div>

        {/* Right: Editor */}
        <div className="writing-editor-panel" style={{
          display: 'flex', flexDirection: 'column',
          padding: '2rem', overflowY: 'auto',
        }}>
          <textarea
            className="writing-textarea"
            value={activeText}
            onChange={e => setActiveText(e.target.value)}
            placeholder={`Start writing your Task ${activeTab} response here...`}
            style={{
              flex: 1, resize: 'none',
              fontFamily: 'var(--font-body)',
              fontSize: '1rem', lineHeight: 1.8,
              padding: 20,
              border: '1px solid var(--outline-variant)',
              borderRadius: 'var(--radius-lg)',
              background: 'var(--surface-container-lowest)',
              color: 'var(--on-surface)',
              minHeight: 300,
            }}
          />

          {/* Word count */}
          <div style={{
            marginTop: 12,
            display: 'flex', justifyContent: 'space-between',
            fontSize: '0.85rem',
            color: wordCount < minWords ? 'var(--error)' : 'var(--secondary)',
          }}>
            <span>{wordCount} words</span>
            <span>
              {wordCount < minWords
                ? `${minWords - wordCount} more words needed`
                : '✓ Minimum met'}
            </span>
          </div>
        </div>
      </div>

      {/* ── Bottom Bar ── */}
      <div className="exam-action-bar" style={{ flexShrink: 0 }}>
        <div className="exam-action-bar-left">
          <span style={{ fontSize: '0.875rem', color: 'var(--on-surface-variant)' }}>
            {activeTab === 1
              ? `Task 1: ${task1Text.trim() ? task1Text.trim().split(/\s+/).length : 0} words`
              : `Task 2: ${task2Text.trim() ? task2Text.trim().split(/\s+/).length : 0} words`
            }
          </span>
        </div>
        <div className="exam-action-bar-right">
          <button className="btn btn-outline" onClick={() => navigate('/writing')}>Exit</button>
          <button
            className="btn btn-primary"
            onClick={handleSubmit}
            disabled={submitting}
          >
            {submitting ? 'Grading...' : 'Submit Full Test'}
          </button>
        </div>
      </div>
    </div>
  );
}
