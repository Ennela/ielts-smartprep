import { useState, useEffect, useCallback } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import writingApi from '../api/writingApi';

const TASK1_TYPES = ['LINE_GRAPH', 'BAR_CHART', 'PIE_CHART', 'TABLE', 'MAP', 'DIAGRAM'];

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
  const [prompt, setPrompt] = useState(null);
  const [essayText, setEssayText] = useState('');
  const [wordCount, setWordCount] = useState(0);
  const [loading, setLoading] = useState(true);
  const [grading, setGrading] = useState(false);
  const [error, setError] = useState('');

  const isTask1 = prompt ? TASK1_TYPES.includes(prompt.essayType) : false;
  const minWords = isTask1 ? 150 : 250;

  useEffect(() => {
    writingApi.getPromptById(promptId)
      .then(res => setPrompt(res.data.data))
      .catch(() => setError('Failed to load prompt.'))
      .finally(() => setLoading(false));
  }, [promptId]);

  const countWords = useCallback((text) => {
    if (!text?.trim()) return 0;
    return text.trim().split(/\s+/).filter(w => w.length > 0).length;
  }, []);

  useEffect(() => {
    const t = setTimeout(() => setWordCount(countWords(essayText)), 300);
    return () => clearTimeout(t);
  }, [essayText, countWords]);

  const handleGrade = async () => {
    if (wordCount < minWords) return;
    setGrading(true);
    setError('');
    try {
      const res = await writingApi.gradeEssay(Number(promptId), essayText);
      navigate(`/writing/result/${res.data.data.submissionId}`);
    } catch (err) {
      setError(err.response?.data?.message || 'Grading failed. Please try again.');
    } finally {
      setGrading(false);
    }
  };

  if (loading) return <div className="loading-screen"><span className="spinner" style={{ width: 24, height: 24 }} />Loading...</div>;

  const pct = Math.min(100, Math.round((wordCount / minWords) * 100));
  const isOk = wordCount >= minWords;

  return (
    <div className="writing-editor-page" style={{ display: 'flex', flexDirection: 'column', height: '100vh', overflow: 'hidden' }}>

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
            onClick={() => navigate('/writing')}
            id="back-to-prompts"
            style={{ margin: 0 }}
          >
            <span className="material-symbols-outlined" style={{ fontSize: 20 }}>arrow_back</span>
            Prompts
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
          <button
            className="btn btn-primary btn-grade"
            onClick={handleGrade}
            disabled={!isOk || grading}
            id="grade-essay-btn"
          >
            {grading ? <><span className="spinner" />AI is grading...</> : 'Submit for Grading'}
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
