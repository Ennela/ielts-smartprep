import { useState, useEffect, useCallback, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import writingApi from '../api/writingApi';

const FORMAT_TYPE = (type) => {
  const map = {
    CAUSE_AND_EFFECT: 'Cause & Effect', PROBLEM_AND_SOLUTION: 'Problem & Solution',
    ADVANTAGES_DISADVANTAGES: 'Advantages & Disadvantages', TWO_PART_QUESTION: 'Two-Part Question',
    LINE_GRAPH: 'Line Graph', BAR_CHART: 'Bar Chart', PIE_CHART: 'Pie Chart',
    TABLE: 'Table', MAP: 'Map', DIAGRAM: 'Diagram',
  };
  return map[type] || type || '';
};

export default function WritingFullExamPage() {
  const navigate = useNavigate();
  const [task1Prompt, setTask1Prompt] = useState(null);
  const [task2Prompt, setTask2Prompt] = useState(null);
  const [activeTab, setActiveTab] = useState(1); // 1 for Task 1, 2 for Task 2

  // Essays and Word Counts
  const [essay1, setEssay1] = useState('');
  const [essay2, setEssay2] = useState('');
  const [wordCount1, setWordCount1] = useState(0);
  const [wordCount2, setWordCount2] = useState(0);

  // States
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [timeLeft, setTimeLeft] = useState(3600); // 60 minutes for both tasks

  const timerRef = useRef(null);
  const task1IdRef = useRef(null);
  const task2IdRef = useRef(null);

  const countWords = useCallback((text) => {
    if (!text?.trim()) return 0;
    return text.trim().split(/\s+/).filter(w => w.length > 0).length;
  }, []);

  // Fetch prompts and restore drafts
  useEffect(() => {
    const query = new URLSearchParams(window.location.search);
    const t1Id = query.get('task1Id');
    const t2Id = query.get('task2Id');

    if (!t1Id || !t2Id) {
      setError('Invalid or missing writing tasks.');
      setLoading(false);
      return;
    }

    task1IdRef.current = t1Id;
    task2IdRef.current = t2Id;

    const fetchPrompts = async () => {
      try {
        const [res1, res2] = await Promise.all([
          writingApi.getPromptById(t1Id),
          writingApi.getPromptById(t2Id)
        ]);
        setTask1Prompt(res1.data.data);
        setTask2Prompt(res2.data.data);

        // Restore draft from local storage
        const storedDraft = localStorage.getItem(`writing_draft_${t1Id}_${t2Id}`);
        if (storedDraft) {
          try {
            const parsed = JSON.parse(storedDraft);
            if (parsed.essay1) setEssay1(parsed.essay1);
            if (parsed.essay2) setEssay2(parsed.essay2);
          } catch (e) {
            console.error('Error parsing stored writing drafts', e);
          }
        }
      } catch (err) {
        setError('Failed to load Writing mock test prompts.');
      } finally {
        setLoading(false);
      }
    };

    fetchPrompts();
  }, []);

  // Word count triggers
  useEffect(() => {
    const t = setTimeout(() => setWordCount1(countWords(essay1)), 300);
    return () => clearTimeout(t);
  }, [essay1, countWords]);

  useEffect(() => {
    const t = setTimeout(() => setWordCount2(countWords(essay2)), 300);
    return () => clearTimeout(t);
  }, [essay2, countWords]);

  // Auto-save drafts
  useEffect(() => {
    if (loading || !task1IdRef.current || !task2IdRef.current) return;
    const draft = { essay1, essay2 };
    localStorage.setItem(`writing_draft_${task1IdRef.current}_${task2IdRef.current}`, JSON.stringify(draft));
  }, [essay1, essay2, loading]);

  // Timer Tick
  useEffect(() => {
    if (loading || submitting || error || !task1Prompt || !task2Prompt) return;

    timerRef.current = setInterval(() => {
      setTimeLeft(prev => {
        if (prev <= 1) {
          clearInterval(timerRef.current);
          handleAutoSubmit();
          return 0;
        }
        return prev - 1;
      });
    }, 1000);

    return () => clearInterval(timerRef.current);
  }, [loading, submitting, error, task1Prompt, task2Prompt]);

  const handleSubmit = async () => {
    if (submitting) return;

    // Word count requirements validation
    if (wordCount1 < 150 || wordCount2 < 250) {
      setError(`Your essays must meet the minimum word count requirements. Task 1: ${wordCount1}/150 words. Task 2: ${wordCount2}/250 words. Please write more before submitting.`);
      window.scrollTo(0, 0);
      return;
    }

    setSubmitting(true);
    setError('');
    clearInterval(timerRef.current);

    try {
      const payload = {
        task1PromptId: Number(task1IdRef.current),
        task1EssayText: essay1,
        task2PromptId: Number(task2IdRef.current),
        task2EssayText: essay2
      };
      const res = await writingApi.submitFullWriting(payload);

      // Clean up draft on success
      localStorage.removeItem(`writing_draft_${task1IdRef.current}_${task2IdRef.current}`);

      navigate('/writing/full-result', { state: { result: res.data.data }, replace: true });
    } catch (err) {
      setError(err.response?.data?.message || 'Grading failed. Please try again.');
      setSubmitting(false);
    }
  };

  const handleAutoSubmit = () => {
    // If timer runs out, submit whatever is written, overriding the validation block
    if (submitting) return;
    setSubmitting(true);
    writingApi.submitFullWriting({
      task1PromptId: Number(task1IdRef.current),
      task1EssayText: essay1,
      task2PromptId: Number(task2IdRef.current),
      task2EssayText: essay2
    }).then(res => {
      localStorage.removeItem(`writing_draft_${task1IdRef.current}_${task2IdRef.current}`);
      navigate('/writing/full-result', { state: { result: res.data.data }, replace: true });
    }).catch(() => {
      setError('Exam ended, but auto-submit failed. Please try submitting manually.');
      setSubmitting(false);
    });
  };

  const formatTime = (seconds) => {
    const m = Math.floor(seconds / 60).toString().padStart(2, '0');
    const s = (seconds % 60).toString().padStart(2, '0');
    return `${m}:${s}`;
  };

  if (loading) return <div className="loading-screen"><span className="spinner" style={{ width: 24, height: 24 }} />Loading mock exam prompts...</div>;

  if (error && !submitting) {
    return (
      <div className="loading-screen" style={{ padding: '2rem' }}>
        <div style={{ maxWidth: '600px', margin: '0 auto', textAlign: 'center' }}>
          <p style={{ color: 'var(--error)', whiteSpace: 'pre-line', fontSize: '1.1rem', fontWeight: 600 }}>{error}</p>
          <button className="btn btn-primary" onClick={() => { setError(''); }} style={{ marginTop: 16, marginRight: 8 }}>
            Continue Writing
          </button>
          <button className="btn btn-outline" onClick={() => navigate('/writing')} style={{ marginTop: 16 }}>
            Exit Exam
          </button>
        </div>
      </div>
    );
  }

  const activePrompt = activeTab === 1 ? task1Prompt : task2Prompt;
  const activeEssayText = activeTab === 1 ? essay1 : essay2;
  const activeWordCount = activeTab === 1 ? wordCount1 : wordCount2;
  const minWords = activeTab === 1 ? 150 : 250;
  const pct = Math.min(100, Math.round((activeWordCount / minWords) * 100));
  const isOk = activeWordCount >= minWords;

  return (
    <div className="writing-editor-page" id="writing-exam-page" style={{ display: 'flex', flexDirection: 'column', height: '100vh', overflow: 'hidden' }}>
      {/* ── Header ── */}
      <header style={{
        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
        padding: '0 24px', height: 64, flexShrink: 0,
        background: 'var(--surface-container-lowest)',
        borderBottom: '1px solid var(--outline-variant)',
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          <span style={{ fontSize: '1.25rem', fontWeight: 700, color: 'var(--primary)' }}>IELTS Writing Practice</span>
          <div style={{ width: 1, height: 24, background: 'var(--outline-variant)' }} />

          {/* Task Tab Switcher */}
          <div style={{ display: 'flex', gap: 8 }}>
            <button
              onClick={() => setActiveTab(1)}
              className={`btn ${activeTab === 1 ? 'btn-primary' : 'btn-outline'}`}
              style={{ padding: '4px 16px', borderRadius: 'var(--radius-md)', fontSize: '0.875rem' }}
            >
              Task 1 ({wordCount1}/150 w)
            </button>
            <button
              onClick={() => setActiveTab(2)}
              className={`btn ${activeTab === 2 ? 'btn-primary' : 'btn-outline'}`}
              style={{ padding: '4px 16px', borderRadius: 'var(--radius-md)', fontSize: '0.875rem' }}
            >
              Task 2 ({wordCount2}/250 w)
            </button>
          </div>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          <div className="exam-timer" style={{
            display: 'flex', alignItems: 'center', gap: 8,
            padding: '6px 12px', background: 'var(--surface-container-high)',
            borderRadius: 'var(--radius-md)', color: timeLeft < 300 ? 'var(--error)' : 'var(--on-surface)',
            fontWeight: 700, fontSize: '1.1rem'
          }}>
            <span className="material-symbols-outlined" style={{ fontSize: 20 }}>timer</span>
            {formatTime(timeLeft)}
          </div>
          <button
            className="btn btn-primary"
            onClick={handleSubmit}
            disabled={submitting}
            id="submit-full-writing-btn"
          >
            {submitting ? 'Evaluating...' : 'Submit Both Tasks'}
          </button>
        </div>
      </header>

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
            {activeTab === 1 ? 'Writing Task 1' : 'Writing Task 2'}
          </h2>

          <div style={{
            padding: 20, borderRadius: 'var(--radius-lg)',
            background: 'var(--surface-container-lowest)',
            border: '1px solid var(--outline-variant)',
            marginBottom: 16,
          }}>
            <p style={{ fontSize: '0.95rem', lineHeight: 1.75, color: 'var(--on-surface)' }}>
              {activePrompt?.promptText}
            </p>
          </div>

          {activeTab === 1 && activePrompt?.imageUrl && (
            <div className="prompt-image-container" style={{ textAlign: 'center', marginBottom: 16 }}>
              <img src={activePrompt.imageUrl} alt="Prompt Diagram" className="prompt-image" style={{ maxWidth: '100%', borderRadius: 'var(--radius-md)' }} />
            </div>
          )}

          <p style={{ fontSize: '0.82rem', color: 'var(--on-surface-variant)', marginTop: 12, fontStyle: 'italic' }}>
            {activeTab === 1
              ? `Write at least ${minWords} words. Describe key features and make comparisons.`
              : `Write at least ${minWords} words. Provide reasons and relevant examples.`}
          </p>
        </div>

        {/* Center Pane: Editor */}
        <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
          <textarea
            className="editor-textarea"
            value={activeEssayText}
            onChange={e => activeTab === 1 ? setEssay1(e.target.value) : setEssay2(e.target.value)}
            placeholder={activeTab === 1
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
            <div style={{ flex: 1, height: 4, background: 'var(--surface-variant)', borderRadius: 'var(--radius-full)', overflow: 'hidden' }}>
              <div style={{
                height: '100%', borderRadius: 'var(--radius-full)',
                background: isOk ? 'var(--secondary)' : 'var(--primary)',
                width: `${pct}%`, transition: 'width 0.3s ease',
              }} />
            </div>
            <div className={`word-count ${isOk ? 'count-ok' : 'count-low'}`}>
              <strong>{activeWordCount}</strong> / {minWords} words
              {!isOk && <span className="count-warning" style={{ color: 'var(--error)', marginLeft: 8 }}> · need {minWords - activeWordCount} more</span>}
            </div>
          </div>
        </div>

      </div>
    </div>
  );
}
