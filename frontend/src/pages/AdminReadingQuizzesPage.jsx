import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import adminApi from '../api/adminApi';
import { usePaginatedQuery } from '../hooks/usePaginatedQuery';
import Pagination from '../components/Pagination';

const TOPICS = [
  { value: 'ENVIRONMENT', label: 'Environment' },
  { value: 'TECHNOLOGY', label: 'Technology' },
  { value: 'HISTORY', label: 'History' },
  { value: 'HEALTH', label: 'Health' },
  { value: 'EDUCATION', label: 'Education' },
];

const DIFFICULTIES = [
  { value: 'PASSAGE_1', label: 'Passage 1 (Easy)', defaultTime: 600 },
  { value: 'PASSAGE_2', label: 'Passage 2 (Medium)', defaultTime: 900 },
  { value: 'PASSAGE_3', label: 'Passage 3 (Hard)', defaultTime: 1200 },
];

const QUESTION_TYPES = [
  { value: 'MCQ', label: 'Multiple Choice (MCQ)' },
  { value: 'TFNG', label: 'True / False / Not Given (TFNG)' },
  { value: 'YNNG', label: 'Yes / No / Not Given (YNNG)' },
  { value: 'SENTENCE_COMPLETION', label: 'Sentence Completion' },
  { value: 'SUMMARY_COMPLETION', label: 'Summary Completion' },
  { value: 'MATCHING_HEADINGS', label: 'Matching Headings' },
  { value: 'MATCHING_INFORMATION', label: 'Matching Information' },
  { value: 'MATCHING_FEATURES', label: 'Matching Features' },
  { value: 'MATCHING_SENTENCE_ENDINGS', label: 'Matching Sentence Endings' }
];

const TOPIC_LABELS = {
  ENVIRONMENT: 'Environment',
  TECHNOLOGY: 'Technology',
  HISTORY: 'History',
  HEALTH: 'Health',
  EDUCATION: 'Education'
};

const DIFFICULTY_LABELS = {
  PASSAGE_1: 'Passage 1 (Easy)',
  PASSAGE_2: 'Passage 2 (Medium)',
  PASSAGE_3: 'Passage 3 (Hard)'
};

export default function AdminReadingQuizzesPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [filterTopic, setFilterTopic] = useState('');
  const [filterDifficulty, setFilterDifficulty] = useState('');
  const [filterSource, setFilterSource] = useState('');
  const [error, setError] = useState(null);
  const [successMsg, setSuccessMsg] = useState(null);

  // Modal state
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState(null); // null = create, object = edit
  const [form, setForm] = useState({
    topic: 'ENVIRONMENT',
    difficulty: 'PASSAGE_1',
    passageText: '',
    timeLimitSeconds: 600,
    questions: []
  });
  const [saving, setSaving] = useState(false);

  // Delete confirm
  const [deleteId, setDeleteId] = useState(null);
  const [deleting, setDeleting] = useState(false);

  const {
    content,
    totalPages,
    totalElements,
    page,
    size,
    setPage,
    resetPage,
    isLoading,
    isFetching,
    isPlaceholderData,
  } = usePaginatedQuery({
    queryKey: ['admin', 'reading-quizzes'],
    queryFn: (pg, sz) => adminApi.listReadingQuizzes(
      filterTopic || null, filterDifficulty || null, filterSource || null, pg, sz
    ),
    filters: { filterTopic, filterDifficulty, filterSource },
  });

  const invalidateList = () => {
    queryClient.invalidateQueries({ queryKey: ['admin', 'reading-quizzes'] });
  };

  const openCreate = () => {
    setEditing(null);
    setForm({
      topic: 'ENVIRONMENT',
      difficulty: 'PASSAGE_1',
      passageText: '',
      timeLimitSeconds: 600,
      questions: []
    });
    setModalOpen(true);
  };

  const openEdit = (quiz) => {
    setEditing(quiz);
    setForm({
      topic: quiz.topic || 'ENVIRONMENT',
      difficulty: quiz.difficulty || 'PASSAGE_1',
      passageText: quiz.passageText || '',
      timeLimitSeconds: quiz.timeLimitSeconds || 600,
      questions: quiz.questions ? quiz.questions.map(q => {
        const mappedQ = { ...q };
        if (q.options && q.options.length > 0) {
          const optA = q.options.find(o => o.label === 'A')?.content || '';
          const optB = q.options.find(o => o.label === 'B')?.content || '';
          const optC = q.options.find(o => o.label === 'C')?.content || '';
          const optD = q.options.find(o => o.label === 'D')?.content || '';
          mappedQ.optionA = optA;
          mappedQ.optionB = optB;
          mappedQ.optionC = optC;
          mappedQ.optionD = optD;
        }
        return mappedQ;
      }) : []
    });
    setModalOpen(true);
  };

  const closeModal = () => {
    setModalOpen(false);
    setEditing(null);
    setError(null);
  };

  const handleDifficultyChange = (val) => {
    const diffObj = DIFFICULTIES.find(d => d.value === val);
    setForm(f => ({
      ...f,
      difficulty: val,
      timeLimitSeconds: diffObj ? diffObj.defaultTime : f.timeLimitSeconds
    }));
  };

  // Questions dynamic manipulation
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
          explanation: '',
          orderIndex: f.questions.length + 1
        }
      ]
    }));
  };

  const removeQuestion = (idx) => {
    setForm(f => {
      const updated = f.questions.filter((_, i) => i !== idx);
      // Re-index orderIndex
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
        if (value === 'TFNG') {
          updated[idx].correctAnswer = 'TRUE';
        } else if (value === 'YNNG') {
          updated[idx].correctAnswer = 'YES';
        } else if (value === 'MCQ') {
          updated[idx].correctAnswer = 'A';
        } else {
          updated[idx].correctAnswer = '';
        }
      }
      return { ...f, questions: updated };
    });
  };

  const handleSave = async () => {
    // Validate
    if (!form.passageText.trim()) { setError('Passage text cannot be empty'); return; }
    if (form.questions.length === 0) { setError('The passage must contain at least 1 question'); return; }
    for (let i = 0; i < form.questions.length; i++) {
      const q = form.questions[i];
      if (!q.questionText.trim()) { setError(`Question #${i + 1}: Question text cannot be empty`); return; }
      if (!q.correctAnswer.trim()) { setError(`Question #${i + 1}: Correct answer cannot be empty`); return; }
      if (q.questionType === 'MCQ') {
        if (!q.optionA || !q.optionA.trim() || !q.optionB || !q.optionB.trim()) {
          setError(`Question #${i + 1} (Multiple Choice): Must have at least Option A and Option B`);
          return;
        }
      }
    }

    // Format request payload to include structured options list
    const formattedQuestions = form.questions.map(q => {
      const copy = { ...q };
      if (q.questionType === 'MCQ') {
        const opts = [];
        if (q.optionA && q.optionA.trim()) opts.push({ label: 'A', content: q.optionA.trim() });
        if (q.optionB && q.optionB.trim()) opts.push({ label: 'B', content: q.optionB.trim() });
        if (q.optionC && q.optionC.trim()) opts.push({ label: 'C', content: q.optionC.trim() });
        if (q.optionD && q.optionD.trim()) opts.push({ label: 'D', content: q.optionD.trim() });
        copy.options = opts;
      } else {
        copy.options = null;
      }
      return copy;
    });

    const payload = {
      ...form,
      questions: formattedQuestions
    };

    setSaving(true);
    setError(null);
    try {
      if (editing) {
        await adminApi.updateReadingQuiz(editing.quizId, payload);
        setSuccessMsg('Reading passage updated successfully!');
      } else {
        await adminApi.createReadingQuiz(payload);
        setSuccessMsg('Reading passage created successfully!');
      }
      closeModal();
      invalidateList();
      setTimeout(() => setSuccessMsg(null), 3000);
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Failed to save quiz');
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async () => {
    if (!deleteId) return;
    setDeleting(true);
    try {
      await adminApi.deleteReadingQuiz(deleteId);
      setDeleteId(null);
      setSuccessMsg('Reading passage deleted successfully!');
      invalidateList();
      setTimeout(() => setSuccessMsg(null), 3000);
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Failed to delete quiz');
    } finally {
      setDeleting(false);
    }
  };


  return (
    <div className="admin-dashboard-content">
      {/* Header */}
      <div className="admin-dash-header reveal">
        <div>
          <button className="btn-back" onClick={() => navigate('/admin')} id="back-to-admin">
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2"><path d="M19 12H5M12 19l-7-7 7-7"/></svg>
            Overview
          </button>
          <h1>Reading Quizzes Management</h1>
          <p className="subtitle">{totalElements} sample passages in the system</p>
        </div>
        <button className="btn btn-primary" onClick={openCreate} id="create-quiz-btn">
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
          Add New Passage
        </button>
      </div>

      {successMsg && <div className="success-msg">{successMsg}</div>}
      {error && !modalOpen && <div className="error-msg">{error}</div>}

      {/* Filter */}
      <div className="writing-filter reveal reveal-delay-1" style={{ display: 'flex', gap: '1rem', flexWrap: 'wrap', alignItems: 'center' }}>
        <select
          value={filterTopic}
          onChange={(e) => { setFilterTopic(e.target.value); resetPage(); }}
          className="matching-select"
          style={{ minWidth: '180px', width: 'auto' }}
        >
          <option value="">All Topics</option>
          {TOPICS.map(t => (
            <option key={t.value} value={t.value}>{t.label}</option>
          ))}
        </select>

        <select
          value={filterDifficulty}
          onChange={(e) => { setFilterDifficulty(e.target.value); resetPage(); }}
          className="matching-select"
          style={{ minWidth: '180px', width: 'auto' }}
        >
          <option value="">All Difficulties</option>
          {DIFFICULTIES.map(d => (
            <option key={d.value} value={d.value}>{d.value.replace('_', ' ')}</option>
          ))}
        </select>

        <select
          value={filterSource}
          onChange={(e) => { setFilterSource(e.target.value); resetPage(); }}
          className="matching-select"
          style={{ minWidth: '180px', width: 'auto' }}
        >
          <option value="">All Sources</option>
          <option value="ADMIN">Admin Created</option>
          <option value="AI">AI Generated</option>
        </select>
      </div>

      {/* Table */}
      <div className={`admin-table-section reveal reveal-delay-2${isFetching && isPlaceholderData ? ' is-fetching' : ''}`} style={{ marginTop: '1.5rem' }}>
        {isLoading ? (
          <div className="loading-spinner"><div className="spinner" /></div>
        ) : content.length === 0 ? (
          <div className="empty-state">
            <p>No reading passages found.</p>
          </div>
        ) : (
          <>
            <div className="history-table-wrapper">
              <table className="history-table" id="admin-reading-table">
                <thead>
                  <tr>
                    <th>#</th>
                    <th>Topic</th>
                    <th>Difficulty</th>
                    <th>Source</th>
                    <th>Time Limit</th>
                    <th>Questions Count</th>
                    <th>Passage Text</th>
                    <th>Action</th>
                  </tr>
                </thead>
                <tbody>
                  {content.map((quiz, idx) => (
                    <tr key={quiz.quizId}>
                      <td>{page * size + idx + 1}</td>
                      <td>
                        <span className="essay-type-badge badge-opinion">
                          {TOPIC_LABELS[quiz.topic] || quiz.topic}
                        </span>
                      </td>
                      <td>
                        <span className="essay-type-badge badge-line-graph">
                          {DIFFICULTY_LABELS[quiz.difficulty] || quiz.difficulty}
                        </span>
                      </td>
                      <td>
                        {quiz.isTemplate ? (
                          <span className="essay-type-badge" style={{ backgroundColor: 'rgba(59, 130, 246, 0.1)', color: '#3b82f6', border: '1px solid rgba(59, 130, 246, 0.2)', padding: '0.2rem 0.5rem', borderRadius: '4px', fontSize: '0.75rem', fontWeight: 600 }}>Admin</span>
                        ) : (
                          <span className="essay-type-badge" style={{ backgroundColor: 'rgba(16, 185, 129, 0.1)', color: '#10b981', border: '1px solid rgba(16, 185, 129, 0.2)', padding: '0.2rem 0.5rem', borderRadius: '4px', fontSize: '0.75rem', fontWeight: 600 }} title={`Tạo bởi user: ${quiz.createdBy || 'AI'}`}>AI Generate</span>
                        )}
                      </td>
                      <td>{Math.round(quiz.timeLimitSeconds / 60)} mins</td>
                      <td>{quiz.totalQuestions}</td>
                      <td style={{ maxWidth: '300px', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                        {quiz.passageText}
                      </td>
                      <td>
                        <div className="admin-action-btns">
                          <button
                            className="btn btn-sm btn-outline"
                            onClick={() => navigate(`/reading/exam/${quiz.quizId}?preview=true&adminView=true`)}
                            id={`preview-quiz-${quiz.quizId}`}
                          >👁 Xem thử</button>
                          <button
                            className="btn btn-sm btn-outline"
                            onClick={() => openEdit(quiz)}
                            id={`edit-quiz-${quiz.quizId}`}
                          >Edit</button>
                          <button
                            className="btn btn-sm admin-btn-danger"
                            onClick={() => setDeleteId(quiz.quizId)}
                            id={`delete-quiz-${quiz.quizId}`}
                          >Delete</button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <Pagination
              page={page}
              totalPages={totalPages}
              totalElements={totalElements}
              size={size}
              onPageChange={setPage}
              isFetching={isFetching}
              isPlaceholderData={isPlaceholderData}
            />
          </>
        )}
      </div>

      {/* Create/Edit Modal */}
      {modalOpen && (
        <div className="admin-modal-overlay" onClick={closeModal}>
          <div className="admin-modal admin-modal-wide" onClick={(e) => e.stopPropagation()} style={{ maxWidth: '900px', width: '90%', maxHeight: '90vh', overflowY: 'auto' }}>
            <button className="admin-modal-close" onClick={closeModal} id="close-quiz-modal">
              <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
            </button>

            <h2 style={{ fontFamily: 'var(--font-heading)', fontSize: '1.25rem', fontWeight: 700, marginBottom: 24 }}>
              {editing ? 'Edit Reading Passage' : 'Create New Sample Reading Passage'}
            </h2>

            {error && <div className="error-msg" style={{ marginBottom: '1rem' }}>{error}</div>}

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem', marginBottom: '1rem' }}>
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

              <div className="admin-form-group">
                <label className="admin-form-label">Difficulty (Passage)</label>
                <select
                  className="matching-select"
                  value={form.difficulty}
                  onChange={e => handleDifficultyChange(e.target.value)}
                  style={{ width: '100%', maxWidth: '100%' }}
                >
                  {DIFFICULTIES.map(d => <option key={d.value} value={d.value}>{d.value.replace('_', ' ')}</option>)}
                </select>
              </div>
            </div>

            <div className="admin-form-group" style={{ marginBottom: '1rem' }}>
              <label className="admin-form-label">Time Limit (seconds)</label>
              <input
                type="number"
                className="completion-input"
                value={form.timeLimitSeconds}
                onChange={e => setForm(f => ({ ...f, timeLimitSeconds: parseInt(e.target.value) || 0 }))}
                style={{ width: '100%', maxWidth: '100%' }}
              />
            </div>

            <div className="admin-form-group" style={{ marginBottom: '1.5rem' }}>
              <label className="admin-form-label">Passage Text</label>
              <textarea
                className="editor-textarea"
                value={form.passageText}
                onChange={e => setForm(f => ({ ...f, passageText: e.target.value }))}
                placeholder="Enter passage text..."
                style={{ minHeight: 200, width: '100%', fontFamily: 'inherit' }}
              />
            </div>

            {/* Questions Section */}
            <div style={{ borderTop: '1px solid var(--border-color)', paddingTop: '1.5rem', marginBottom: '1.5rem' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
                <h3 style={{ fontSize: '1.1rem', fontWeight: '600' }}>Question List ({form.questions.length})</h3>
                <button type="button" className="btn btn-outline" onClick={addQuestion}>
                  + Add Question
                </button>
              </div>

              {form.questions.length === 0 ? (
                <p style={{ textAlign: 'center', color: 'var(--text-secondary)', padding: '2rem', border: '1px dashed var(--border-color)', borderRadius: '8px' }}>
                  No questions added yet. Click "+ Add Question" to start.
                </p>
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
                  {form.questions.map((q, idx) => (
                    <div
                      key={idx}
                      style={{
                        border: '1px solid var(--border-color)',
                        borderRadius: '8px',
                        padding: '1.25rem',
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
                          fontWeight: '600'
                        }}
                      >
                        Delete Question
                      </button>

                      <h4 style={{ fontWeight: '600', marginBottom: '1rem', color: 'var(--primary-color)' }}>
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
                          ) : q.questionType === 'TFNG' ? (
                            <select
                              className="matching-select"
                              value={q.correctAnswer}
                              onChange={e => updateQuestionField(idx, 'correctAnswer', e.target.value)}
                              style={{ width: '100%', maxWidth: '100%', padding: '0.4rem 0.8rem' }}
                            >
                              <option value="TRUE">TRUE</option>
                              <option value="FALSE">FALSE</option>
                              <option value="NOT_GIVEN">NOT GIVEN</option>
                            </select>
                          ) : q.questionType === 'YNNG' ? (
                            <select
                              className="matching-select"
                              value={q.correctAnswer}
                              onChange={e => updateQuestionField(idx, 'correctAnswer', e.target.value)}
                              style={{ width: '100%', maxWidth: '100%', padding: '0.4rem 0.8rem' }}
                            >
                              <option value="YES">YES</option>
                              <option value="NO">NO</option>
                              <option value="NOT_GIVEN">NOT GIVEN</option>
                            </select>
                          ) : (
                            <input
                              type="text"
                              className="completion-input"
                              value={q.correctAnswer}
                              onChange={e => updateQuestionField(idx, 'correctAnswer', e.target.value)}
                              placeholder="Enter the correct answer word or phrase..."
                              style={{ width: '100%', maxWidth: '100%', padding: '0.4rem 0.8rem' }}
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
                          placeholder="e.g. According to paragraph 1, what is the primary benefit..."
                          style={{ width: '100%', maxWidth: '100%', padding: '0.5rem 0.8rem' }}
                        />
                      </div>

                      {q.questionType === 'MCQ' && (
                        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.75rem', marginBottom: '1rem' }}>
                          <div>
                            <label className="admin-form-label" style={{ fontSize: '0.8rem' }}>Option A</label>
                            <input
                              type="text"
                              className="completion-input"
                              value={q.optionA || ''}
                              onChange={e => updateQuestionField(idx, 'optionA', e.target.value)}
                              style={{ width: '100%', maxWidth: '100%', padding: '0.4rem 0.8rem' }}
                            />
                          </div>
                          <div>
                            <label className="admin-form-label" style={{ fontSize: '0.8rem' }}>Option B</label>
                            <input
                              type="text"
                              className="completion-input"
                              value={q.optionB || ''}
                              onChange={e => updateQuestionField(idx, 'optionB', e.target.value)}
                              style={{ width: '100%', maxWidth: '100%', padding: '0.4rem 0.8rem' }}
                            />
                          </div>
                          <div>
                            <label className="admin-form-label" style={{ fontSize: '0.8rem' }}>Option C</label>
                            <input
                              type="text"
                              className="completion-input"
                              value={q.optionC || ''}
                              onChange={e => updateQuestionField(idx, 'optionC', e.target.value)}
                              style={{ width: '100%', maxWidth: '100%', padding: '0.4rem 0.8rem' }}
                            />
                          </div>
                          <div>
                            <label className="admin-form-label" style={{ fontSize: '0.8rem' }}>Option D</label>
                            <input
                              type="text"
                              className="completion-input"
                              value={q.optionD || ''}
                              onChange={e => updateQuestionField(idx, 'optionD', e.target.value)}
                              style={{ width: '100%', maxWidth: '100%', padding: '0.4rem 0.8rem' }}
                            />
                          </div>
                        </div>
                      )}

                      {/* Advanced details (optional metadata) */}
                      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: '0.75rem', marginBottom: '1rem' }}>
                        <div>
                          <label className="admin-form-label" style={{ fontSize: '0.8rem' }}>Group ID (optional)</label>
                          <input
                            type="number"
                            className="completion-input"
                            value={q.groupId || ''}
                            onChange={e => updateQuestionField(idx, 'groupId', parseInt(e.target.value) || null)}
                            style={{ width: '100%', maxWidth: '100%', padding: '0.4rem 0.8rem' }}
                          />
                        </div>
                        <div>
                          <label className="admin-form-label" style={{ fontSize: '0.8rem' }}>Group Label (optional)</label>
                          <input
                            type="text"
                            className="completion-input"
                            value={q.groupLabel || ''}
                            onChange={e => updateQuestionField(idx, 'groupLabel', e.target.value)}
                            placeholder="Questions 1-5"
                            style={{ width: '100%', maxWidth: '100%', padding: '0.4rem 0.8rem' }}
                          />
                        </div>
                        <div>
                          <label className="admin-form-label" style={{ fontSize: '0.8rem' }}>Word Limit (optional)</label>
                          <input
                            type="number"
                            className="completion-input"
                            value={q.wordLimit || ''}
                            onChange={e => updateQuestionField(idx, 'wordLimit', parseInt(e.target.value) || null)}
                            style={{ width: '100%', maxWidth: '100%', padding: '0.4rem 0.8rem' }}
                          />
                        </div>
                      </div>

                      {q.groupId && (
                        <div className="admin-form-group" style={{ marginBottom: '1rem' }}>
                          <label className="admin-form-label" style={{ fontSize: '0.8rem' }}>Group Context (shared text/summary with blanks)</label>
                          <textarea
                            className="editor-textarea"
                            value={q.groupContext || ''}
                            onChange={e => updateQuestionField(idx, 'groupContext', e.target.value)}
                            placeholder="Enter shared context or summary with blanks for the question group..."
                            style={{ minHeight: 60, width: '100%', fontSize: '0.875rem' }}
                          />
                        </div>
                      )}

                      <div className="admin-form-group" style={{ marginBottom: 0 }}>
                        <label className="admin-form-label" style={{ fontSize: '0.85rem' }}>Detailed Explanation</label>
                        <textarea
                          className="editor-textarea"
                          value={q.explanation || ''}
                          onChange={e => updateQuestionField(idx, 'explanation', e.target.value)}
                          placeholder="Enter explanation of why this answer is correct and cite the passage text..."
                          style={{ minHeight: 60, width: '100%' }}
                        />
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>

            <div className="admin-form-actions" style={{ borderTop: '1px solid var(--border-color)', paddingTop: '1.5rem' }}>
              <button className="btn btn-outline" onClick={closeModal} type="button">Cancel</button>
              <button className="btn btn-primary" onClick={handleSave} disabled={saving} type="button">
                {saving && <span className="spinner" />}
                {editing ? 'Update Passage' : 'Create New Passage'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Delete Confirm Modal */}
      {deleteId && (
        <div className="admin-modal-overlay" onClick={() => setDeleteId(null)}>
          <div className="admin-modal" onClick={(e) => e.stopPropagation()} style={{ maxWidth: 420 }}>
            <h2 style={{ fontFamily: 'var(--font-heading)', fontSize: '1.1rem', fontWeight: 700, marginBottom: 12 }}>
              Confirm Delete
            </h2>
            <p style={{ color: 'var(--text-secondary)', fontSize: '0.9rem', marginBottom: 24, lineHeight: 1.6 }}>
              Are you sure you want to delete this reading passage? This action cannot be undone.
            </p>
            <div className="admin-form-actions">
              <button className="btn btn-outline" onClick={() => setDeleteId(null)}>Cancel</button>
              <button className="btn admin-btn-danger-fill" onClick={handleDelete} disabled={deleting}>
                {deleting && <span className="spinner" />}
                Delete Passage
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
