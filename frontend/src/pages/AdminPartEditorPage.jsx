import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import adminApi from '../api/adminApi';

const TOPICS = [
  { value: 'ACCOMMODATION', label: 'Accommodation / Booking' },
  { value: 'EDUCATION', label: 'Campus / Education' },
  { value: 'CULTURE', label: 'Culture / Museum' },
  { value: 'SCIENCE', label: 'Science / Technology' },
  { value: 'ENVIRONMENT', label: 'Environment / Nature' },
];

const QUESTION_TYPES = [
  { value: 'MCQ', label: 'Multiple Choice (MCQ)' },
  { value: 'FILL_BLANK', label: 'Fill in the Blank' },
];

export default function AdminPartEditorPage() {
  const navigate = useNavigate();
  const { partId } = useParams();
  const isEdit = !!partId;

  const [loading, setLoading] = useState(isEdit);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);

  const [form, setForm] = useState({
    partNumber: 1,
    title: '',
    topic: 'ACCOMMODATION',
    transcriptText: '',
    durationSeconds: 180,
    questions: [],
  });

  useEffect(() => {
    if (isEdit) {
      adminApi.getListeningPartById(partId)
        .then(res => {
          const part = res.data?.data;
          if (part) {
            setForm({
              partNumber: part.partNumber || 1,
              title: part.title || '',
              topic: part.topic || 'ACCOMMODATION',
              transcriptText: part.transcriptText || '',
              durationSeconds: part.durationSeconds || 180,
              questions: part.questions ? part.questions.map(q => {
                const mappedQ = {
                  questionId: q.questionId,
                  questionType: q.questionType || 'MCQ',
                  questionText: q.questionText || '',
                  correctAnswer: q.correctAnswer || '',
                  orderIndex: q.orderIndex || 1,
                  optionA: '',
                  optionB: '',
                  optionC: '',
                  optionD: '',
                };
                if (q.options && q.options.length > 0) {
                  mappedQ.optionA = q.options.find(o => o.label === 'A')?.content || '';
                  mappedQ.optionB = q.options.find(o => o.label === 'B')?.content || '';
                  mappedQ.optionC = q.options.find(o => o.label === 'C')?.content || '';
                  mappedQ.optionD = q.options.find(o => o.label === 'D')?.content || '';
                }
                return mappedQ;
              }) : [],
            });
          }
        })
        .catch(err => setError(err.message))
        .finally(() => setLoading(false));
    }
  }, [partId, isEdit]);

  const addQuestion = () => {
    setForm(f => ({
      ...f,
      questions: [
        ...f.questions,
        {
          questionType: 'MCQ',
          questionText: '',
          optionA: '',
          optionB: '',
          optionC: '',
          optionD: '',
          correctAnswer: 'A',
          orderIndex: f.questions.length + 1,
        }
      ]
    }));
  };

  const removeQuestion = (idx) => {
    setForm(f => {
      const updated = f.questions.filter((_, i) => i !== idx);
      const reindexed = updated.map((q, i) => ({ ...q, orderIndex: i + 1 }));
      return { ...f, questions: reindexed };
    });
  };

  const updateQuestionField = (idx, field, value) => {
    setForm(f => {
      const updated = [...f.questions];
      updated[idx] = { ...updated[idx], [field]: value };
      
      // Auto-set default correct answer when changing type
      if (field === 'questionType') {
        if (value === 'MCQ') {
          updated[idx].correctAnswer = 'A';
        } else {
          updated[idx].correctAnswer = '';
        }
      }
      return { ...f, questions: updated };
    });
  };

  const handleSave = async (e) => {
    e.preventDefault();
    setError(null);

    // Validations
    if (!form.title.trim()) { setError('Title is required'); return; }
    if (!form.transcriptText.trim()) { setError('Transcript text is required'); return; }
    if (form.questions.length === 0) { setError('A listening part must contain at least 1 question'); return; }

    for (let i = 0; i < form.questions.length; i++) {
      const q = form.questions[i];
      if (!q.questionText.trim()) { setError(`Question #${i + 1}: Question text is required`); return; }
      if (!q.correctAnswer.trim()) { setError(`Question #${i + 1}: Correct answer is required`); return; }
      if (q.questionType === 'MCQ') {
        if (!q.optionA.trim() || !q.optionB.trim()) {
          setError(`Question #${i + 1} (Multiple Choice): Must have at least Option A and Option B`);
          return;
        }
      }
    }

    // Format payload
    const formattedQuestions = form.questions.map(q => {
      const copy = { ...q };
      if (q.questionType === 'MCQ') {
        const opts = [];
        if (q.optionA.trim()) opts.push({ label: 'A', content: q.optionA.trim() });
        if (q.optionB.trim()) opts.push({ label: 'B', content: q.optionB.trim() });
        if (q.optionC.trim()) opts.push({ label: 'C', content: q.optionC.trim() });
        if (q.optionD.trim()) opts.push({ label: 'D', content: q.optionD.trim() });
        copy.options = opts;
      } else {
        copy.options = null;
      }
      return copy;
    });

    const payload = {
      ...form,
      questions: formattedQuestions,
    };

    setSaving(true);
    try {
      if (isEdit) {
        await adminApi.updateListeningPart(partId, payload);
      } else {
        await adminApi.createListeningPart(payload);
      }
      navigate('/admin/listening');
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Failed to save listening part');
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <div className="admin-dashboard-content">
        <div className="loading-spinner"><div className="spinner" /></div>
      </div>
    );
  }

  return (
    <div className="admin-dashboard-content">
      {/* Header */}
      <div className="admin-dash-header reveal">
        <div>
          <button className="btn-back" onClick={() => navigate('/admin/listening')} id="back-to-parts">
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2"><path d="M19 12H5M12 19l-7-7 7-7"/></svg>
            Listening Parts List
          </button>
          <h1>{isEdit ? `Edit Listening Part #${partId}` : 'Create New Listening Part'}</h1>
          <p className="subtitle">{isEdit ? 'Modify details, transcript, and questions' : 'Manually input new questions and transcript'}</p>
        </div>
      </div>

      {error && <div className="error-msg" style={{ marginBottom: '1.5rem' }}>{error}</div>}

      <form onSubmit={handleSave} className="card reveal reveal-delay-1" style={{ padding: '2rem', display: 'flex', flexDirection: 'column', gap: '1.5rem', maxWidth: '900px' }}>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1.5rem' }}>
          <div className="admin-form-group">
            <label className="admin-form-label">Part Number (1 - 4)</label>
            <select
              className="matching-select"
              value={form.partNumber}
              onChange={e => setForm(f => ({ ...f, partNumber: parseInt(e.target.value) || 1 }))}
              style={{ width: '100%', maxWidth: '100%' }}
            >
              <option value="1">Part 1 (Everyday Conversation)</option>
              <option value="2">Part 2 (Everyday Monologue)</option>
              <option value="3">Part 3 (Academic Discussion)</option>
              <option value="4">Part 4 (Academic Lecture)</option>
            </select>
          </div>

          <div className="admin-form-group">
            <label className="admin-form-label">Topic</label>
            <select
              className="matching-select"
              value={form.topic}
              onChange={e => setForm(f => ({ ...f, topic: e.target.value }))}
              style={{ width: '100%', maxWidth: '100%' }}
            >
              {TOPICS.map(t => <option key={t.value} value={t.value}>{t.label}</option>)}
            </select>
          </div>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: '1.5rem' }}>
          <div className="admin-form-group">
            <label className="admin-form-label">Title</label>
            <input
              type="text"
              className="completion-input"
              value={form.title}
              onChange={e => setForm(f => ({ ...f, title: e.target.value }))}
              placeholder="e.g. Inquiry about renting accommodation"
              style={{ width: '100%', maxWidth: '100%' }}
              required
            />
          </div>

          <div className="admin-form-group">
            <label className="admin-form-label">Duration (seconds)</label>
            <input
              type="number"
              className="completion-input"
              value={form.durationSeconds}
              onChange={e => setForm(f => ({ ...f, durationSeconds: parseInt(e.target.value) || 0 }))}
              placeholder="e.g. 180"
              style={{ width: '100%', maxWidth: '100%' }}
              required
            />
          </div>
        </div>

        <div className="admin-form-group">
          <label className="admin-form-label">
            Transcript Text (Note: Put `[ANS_1]...[/ANS_1]` around the answers in script text for grading feedback)
          </label>
          <textarea
            className="editor-textarea"
            value={form.transcriptText}
            onChange={e => setForm(f => ({ ...f, transcriptText: e.target.value }))}
            placeholder="Write or paste the listening transcript. E.g. Hello, I'd like to book an [ANS_1]apartment[/ANS_1] please..."
            style={{ minHeight: '220px', width: '100%', fontFamily: 'inherit', lineHeight: 1.6 }}
            required
          />
        </div>

        {/* Dynamic Questions section */}
        <div style={{ borderTop: '1px solid var(--border-color)', paddingTop: '1.5rem', marginTop: '0.5rem' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem' }}>
            <h3 style={{ fontSize: '1.1rem', fontWeight: 600 }}>Questions Checklist ({form.questions.length})</h3>
            <button type="button" className="btn btn-outline" onClick={addQuestion} id="add-question-btn">
              + Add Question
            </button>
          </div>

          {form.questions.length === 0 ? (
            <div style={{ textAlign: 'center', padding: '3rem 1.5rem', border: '1px dashed var(--border-color)', borderRadius: '8px', color: 'var(--text-secondary)' }}>
              No questions added yet. Click "+ Add Question" to start adding questions for this part.
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
              {form.questions.map((q, idx) => (
                <div
                  key={idx}
                  style={{
                    border: '1px solid var(--border-color)',
                    borderRadius: '8px',
                    padding: '1.5rem',
                    background: 'var(--bg-body)',
                    position: 'relative'
                  }}
                >
                  <button
                    type="button"
                    onClick={() => removeQuestion(idx)}
                    style={{
                      position: 'absolute',
                      top: '1rem',
                      right: '1rem',
                      background: 'none',
                      border: 'none',
                      color: 'var(--color-danger, #ef4444)',
                      cursor: 'pointer',
                      fontWeight: 600,
                      fontSize: '0.875rem'
                    }}
                  >
                    Delete Question
                  </button>

                  <h4 style={{ fontWeight: 600, marginBottom: '1rem', color: 'var(--primary-color)' }}>
                    Question #{idx + 1}
                  </h4>

                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem', marginBottom: '1rem' }}>
                    <div>
                      <label className="admin-form-label" style={{ fontSize: '0.85rem' }}>Question Type</label>
                      <select
                        className="matching-select"
                        value={q.questionType}
                        onChange={e => updateQuestionField(idx, 'questionType', e.target.value)}
                        style={{ width: '100%', maxWidth: '100%', padding: '0.4rem 0.8rem' }}
                      >
                        {QUESTION_TYPES.map(qt => <option key={qt.value} value={qt.value}>{qt.label}</option>)}
                      </select>
                    </div>

                    <div>
                      <label className="admin-form-label" style={{ fontSize: '0.85rem' }}>Correct Answer</label>
                      {q.questionType === 'MCQ' ? (
                        <select
                          className="matching-select"
                          value={q.correctAnswer}
                          onChange={e => updateQuestionField(idx, 'correctAnswer', e.target.value)}
                          style={{ width: '100%', maxWidth: '100%', padding: '0.4rem 0.8rem' }}
                        >
                          <option value="A">A</option>
                          <option value="B">B</option>
                          <option value="C">C</option>
                          <option value="D">D</option>
                        </select>
                      ) : (
                        <input
                          type="text"
                          className="completion-input"
                          value={q.correctAnswer}
                          onChange={e => updateQuestionField(idx, 'correctAnswer', e.target.value)}
                          placeholder="e.g. apartment (must match script text exactly)"
                          style={{ width: '100%', maxWidth: '100%', padding: '0.4rem 0.8rem' }}
                          required
                        />
                      )}
                    </div>
                  </div>

                  <div className="admin-form-group" style={{ marginBottom: '1rem' }}>
                    <label className="admin-form-label" style={{ fontSize: '0.85rem' }}>Question Text</label>
                    <input
                      type="text"
                      className="completion-input"
                      value={q.questionText}
                      onChange={e => updateQuestionField(idx, 'questionText', e.target.value)}
                      placeholder="e.g. What type of accommodation does the student want to rent?"
                      style={{ width: '100%', maxWidth: '100%', padding: '0.5rem 0.8rem' }}
                      required
                    />
                  </div>

                  {q.questionType === 'MCQ' && (
                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.75rem' }}>
                      <div>
                        <label className="admin-form-label" style={{ fontSize: '0.8rem' }}>Option A</label>
                        <input
                          type="text"
                          className="completion-input"
                          value={q.optionA}
                          onChange={e => updateQuestionField(idx, 'optionA', e.target.value)}
                          style={{ width: '100%', maxWidth: '100%', padding: '0.4rem 0.8rem' }}
                          required
                        />
                      </div>
                      <div>
                        <label className="admin-form-label" style={{ fontSize: '0.8rem' }}>Option B</label>
                        <input
                          type="text"
                          className="completion-input"
                          value={q.optionB}
                          onChange={e => updateQuestionField(idx, 'optionB', e.target.value)}
                          style={{ width: '100%', maxWidth: '100%', padding: '0.4rem 0.8rem' }}
                          required
                        />
                      </div>
                      <div>
                        <label className="admin-form-label" style={{ fontSize: '0.8rem' }}>Option C</label>
                        <input
                          type="text"
                          className="completion-input"
                          value={q.optionC}
                          onChange={e => updateQuestionField(idx, 'optionC', e.target.value)}
                          style={{ width: '100%', maxWidth: '100%', padding: '0.4rem 0.8rem' }}
                        />
                      </div>
                      <div>
                        <label className="admin-form-label" style={{ fontSize: '0.8rem' }}>Option D</label>
                        <input
                          type="text"
                          className="completion-input"
                          value={q.optionD}
                          onChange={e => updateQuestionField(idx, 'optionD', e.target.value)}
                          style={{ width: '100%', maxWidth: '100%', padding: '0.4rem 0.8rem' }}
                        />
                      </div>
                    </div>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>

        <div className="admin-form-actions" style={{ display: 'flex', justifyContent: 'flex-end', gap: '1rem', borderTop: '1px solid var(--border-color)', paddingTop: '1.5rem', marginTop: '1.5rem' }}>
          <button className="btn btn-outline" onClick={() => navigate('/admin/listening')} type="button" disabled={saving}>
            Cancel
          </button>
          <button className="btn btn-primary" type="submit" disabled={saving} id="save-part-btn">
            {saving && <span className="spinner" />}
            {isEdit ? 'Update Part' : 'Create Part'}
          </button>
        </div>
      </form>
    </div>
  );
}
