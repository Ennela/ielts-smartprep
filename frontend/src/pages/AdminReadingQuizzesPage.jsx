import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import adminApi from '../api/adminApi';

const TOPICS = [
  { value: 'ENVIRONMENT', label: 'Môi trường' },
  { value: 'TECHNOLOGY', label: 'Công nghệ' },
  { value: 'HISTORY', label: 'Lịch sử' },
  { value: 'HEALTH', label: 'Sức khỏe' },
  { value: 'EDUCATION', label: 'Giáo dục' },
];

const DIFFICULTIES = [
  { value: 'PASSAGE_1', label: 'Passage 1 (Dễ)', defaultTime: 600 },
  { value: 'PASSAGE_2', label: 'Passage 2 (Vừa)', defaultTime: 900 },
  { value: 'PASSAGE_3', label: 'Passage 3 (Khó)', defaultTime: 1200 },
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
  ENVIRONMENT: 'Môi trường',
  TECHNOLOGY: 'Công nghệ',
  HISTORY: 'Lịch sử',
  HEALTH: 'Sức khỏe',
  EDUCATION: 'Giáo dục'
};

const DIFFICULTY_LABELS = {
  PASSAGE_1: 'Passage 1 (Dễ)',
  PASSAGE_2: 'Passage 2 (Vừa)',
  PASSAGE_3: 'Passage 3 (Khó)'
};

export default function AdminReadingQuizzesPage() {
  const navigate = useNavigate();
  const [quizzes, setQuizzes] = useState(null);
  const [filterTopic, setFilterTopic] = useState('');
  const [filterDifficulty, setFilterDifficulty] = useState('');
  const [filterSource, setFilterSource] = useState('');
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
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

  const fetchQuizzes = (topic, difficulty, source, pageVal) => {
    setLoading(true);
    adminApi.listReadingQuizzes(topic || null, difficulty || null, source || null, pageVal, 10)
      .then(res => setQuizzes(res.data?.data))
      .catch(err => setError(err.message))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    fetchQuizzes(filterTopic, filterDifficulty, filterSource, page);
  }, [filterTopic, filterDifficulty, filterSource, page]);

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
      questions: quiz.questions ? quiz.questions.map(q => ({ ...q })) : []
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
    if (!form.passageText.trim()) { setError('Nội dung bài đọc không được để trống'); return; }
    if (form.questions.length === 0) { setError('Bài đọc phải có ít nhất 1 câu hỏi'); return; }
    for (let i = 0; i < form.questions.length; i++) {
      const q = form.questions[i];
      if (!q.questionText.trim()) { setError(`Câu hỏi #${i + 1}: Nội dung câu hỏi không được để trống`); return; }
      if (!q.correctAnswer.trim()) { setError(`Câu hỏi #${i + 1}: Đáp án chính xác không được để trống`); return; }
      if (q.questionType === 'MCQ') {
        if (!q.optionA.trim() || !q.optionB.trim()) {
          setError(`Câu hỏi #${i + 1} (Multiple Choice): Phải có ít nhất Option A và Option B`);
          return;
        }
      }
    }

    setSaving(true);
    setError(null);
    try {
      if (editing) {
        await adminApi.updateReadingQuiz(editing.quizId, form);
        setSuccessMsg('Cập nhật đề đọc thành công!');
      } else {
        await adminApi.createReadingQuiz(form);
        setSuccessMsg('Tạo đề đọc mới thành công!');
      }
      closeModal();
      fetchQuizzes(filterTopic, filterDifficulty, page);
      setTimeout(() => setSuccessMsg(null), 3000);
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Lưu đề thi thất bại');
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
      setSuccessMsg('Đã xóa đề đọc thành công!');
      fetchQuizzes(filterTopic, filterDifficulty, page);
      setTimeout(() => setSuccessMsg(null), 3000);
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Xóa đề thi thất bại');
    } finally {
      setDeleting(false);
    }
  };

  const content = quizzes?.content || [];
  const totalPages = quizzes?.totalPages || 0;
  const totalElements = quizzes?.totalElements || 0;

  return (
    <div className="admin-dashboard-content">
      {/* Header */}
      <div className="admin-dash-header reveal">
        <div>
          <button className="btn-back" onClick={() => navigate('/admin')} id="back-to-admin">
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2"><path d="M19 12H5M12 19l-7-7 7-7"/></svg>
            Tổng quan
          </button>
          <h1>Quản lý đề đọc (Reading)</h1>
          <p className="subtitle">{totalElements} đề đọc mẫu trong hệ thống</p>
        </div>
        <button className="btn btn-primary" onClick={openCreate} id="create-quiz-btn">
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
          Thêm đề đọc mới
        </button>
      </div>

      {successMsg && <div className="success-msg">{successMsg}</div>}
      {error && !modalOpen && <div className="error-msg">{error}</div>}

      {/* Filter */}
      <div className="writing-filter reveal reveal-delay-1" style={{ display: 'flex', gap: '1rem', flexWrap: 'wrap', alignItems: 'center' }}>
        <select
          value={filterTopic}
          onChange={(e) => { setFilterTopic(e.target.value); setPage(0); }}
          className="form-input"
          style={{ padding: '0.5rem 1rem', borderRadius: '8px', border: '1px solid var(--border-color)', background: 'var(--bg-card)', minWidth: '180px' }}
        >
          <option value="">Tất cả chủ đề</option>
          {TOPICS.map(t => (
            <option key={t.value} value={t.value}>{t.label}</option>
          ))}
        </select>

        <select
          value={filterDifficulty}
          onChange={(e) => { setFilterDifficulty(e.target.value); setPage(0); }}
          className="form-input"
          style={{ padding: '0.5rem 1rem', borderRadius: '8px', border: '1px solid var(--border-color)', background: 'var(--bg-card)', minWidth: '180px' }}
        >
          <option value="">Tất cả độ khó</option>
          {DIFFICULTIES.map(d => (
            <option key={d.value} value={d.value}>{d.value.replace('_', ' ')}</option>
          ))}
        </select>

        <select
          value={filterSource}
          onChange={(e) => { setFilterSource(e.target.value); setPage(0); }}
          className="form-input"
          style={{ padding: '0.5rem 1rem', borderRadius: '8px', border: '1px solid var(--border-color)', background: 'var(--bg-card)', minWidth: '180px' }}
        >
          <option value="">Tất cả nguồn</option>
          <option value="ADMIN">Admin soạn</option>
          <option value="AI">AI generate</option>
        </select>
      </div>

      {/* Table */}
      <div className="admin-table-section reveal reveal-delay-2" style={{ marginTop: '1.5rem' }}>
        {loading ? (
          <div className="loading-spinner"><div className="spinner" /></div>
        ) : content.length === 0 ? (
          <div className="empty-state">
            <p>Không tìm thấy đề đọc nào phù hợp.</p>
          </div>
        ) : (
          <>
            <div className="history-table-wrapper">
              <table className="history-table" id="admin-reading-table">
                <thead>
                  <tr>
                    <th>#</th>
                    <th>Chủ đề</th>
                    <th>Độ khó</th>
                    <th>Nguồn</th>
                    <th>Thời gian</th>
                    <th>Số câu hỏi</th>
                    <th>Nội dung bài đọc</th>
                    <th>Hành động</th>
                  </tr>
                </thead>
                <tbody>
                  {content.map((quiz, idx) => (
                    <tr key={quiz.quizId}>
                      <td>{page * 10 + idx + 1}</td>
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
                      <td>{Math.round(quiz.timeLimitSeconds / 60)} phút</td>
                      <td>{quiz.totalQuestions}</td>
                      <td style={{ maxWidth: '300px', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                        {quiz.passageText}
                      </td>
                      <td>
                        <div className="admin-action-btns">
                          <button
                            className="btn btn-sm btn-outline"
                            onClick={() => openEdit(quiz)}
                            id={`edit-quiz-${quiz.quizId}`}
                          >Sửa</button>
                          <button
                            className="btn btn-sm admin-btn-danger"
                            onClick={() => setDeleteId(quiz.quizId)}
                            id={`delete-quiz-${quiz.quizId}`}
                          >Xóa</button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {totalPages > 1 && (
              <div className="ht-pagination">
                <button className="btn btn-sm btn-outline" disabled={page === 0} onClick={() => setPage(p => Math.max(0, p - 1))}>Trước</button>
                <span className="ht-page-info">Trang {page + 1} / {totalPages}</span>
                <button className="btn btn-sm btn-outline" disabled={page >= totalPages - 1} onClick={() => setPage(p => p + 1)}>Sau</button>
              </div>
            )}
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
              {editing ? 'Chỉnh sửa đề đọc' : 'Tạo đề đọc mẫu mới'}
            </h2>

            {error && <div className="error-msg" style={{ marginBottom: '1rem' }}>{error}</div>}

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem', marginBottom: '1rem' }}>
              <div className="admin-form-group">
                <label className="admin-form-label">Chủ đề</label>
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
                <label className="admin-form-label">Độ khó (Passage)</label>
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
              <label className="admin-form-label">Thời gian làm bài (giây)</label>
              <input
                type="number"
                className="completion-input"
                value={form.timeLimitSeconds}
                onChange={e => setForm(f => ({ ...f, timeLimitSeconds: parseInt(e.target.value) || 0 }))}
                style={{ width: '100%', maxWidth: '100%' }}
              />
            </div>

            <div className="admin-form-group" style={{ marginBottom: '1.5rem' }}>
              <label className="admin-form-label">Nội dung bài đọc (Passage Text)</label>
              <textarea
                className="editor-textarea"
                value={form.passageText}
                onChange={e => setForm(f => ({ ...f, passageText: e.target.value }))}
                placeholder="Nhập nội dung văn bản bài đọc..."
                style={{ minHeight: 200, width: '100%', fontFamily: 'inherit' }}
              />
            </div>

            {/* Questions Section */}
            <div style={{ borderTop: '1px solid var(--border-color)', paddingTop: '1.5rem', marginBottom: '1.5rem' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
                <h3 style={{ fontSize: '1.1rem', fontWeight: '600' }}>Danh sách câu hỏi ({form.questions.length})</h3>
                <button type="button" className="btn btn-outline" onClick={addQuestion}>
                  + Thêm câu hỏi
                </button>
              </div>

              {form.questions.length === 0 ? (
                <p style={{ textAlign: 'center', color: 'var(--text-secondary)', padding: '2rem', border: '1px dashed var(--border-color)', borderRadius: '8px' }}>
                  Chưa có câu hỏi nào. Nhấn "+ Thêm câu hỏi" để bắt đầu.
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
                        Xóa câu hỏi
                      </button>

                      <h4 style={{ fontWeight: '600', marginBottom: '1rem', color: 'var(--primary-color)' }}>
                        Câu hỏi #{idx + 1}
                      </h4>

                      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem', marginBottom: '1rem' }}>
                        <div>
                          <label className="admin-form-label" style={{ fontSize: '0.85rem' }}>Loại câu hỏi</label>
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
                          <label className="admin-form-label" style={{ fontSize: '0.85rem' }}>Đáp án chính xác</label>
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
                              placeholder="Nhập từ/cụm từ đáp án chính xác..."
                              style={{ width: '100%', maxWidth: '100%', padding: '0.4rem 0.8rem' }}
                            />
                          )}
                        </div>
                      </div>

                      <div className="admin-form-group" style={{ marginBottom: '1rem' }}>
                        <label className="admin-form-label" style={{ fontSize: '0.85rem' }}>Nội dung câu hỏi</label>
                        <input
                          type="text"
                          className="completion-input"
                          value={q.questionText}
                          onChange={e => updateQuestionField(idx, 'questionText', e.target.value)}
                          placeholder="Ví dụ: According to paragraph 1, what is the primary benefit..."
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
                            placeholder="Nhập ngữ cảnh/tóm tắt chung cho nhóm câu hỏi..."
                            style={{ minHeight: 60, width: '100%', fontSize: '0.875rem' }}
                          />
                        </div>
                      )}

                      <div className="admin-form-group" style={{ marginBottom: 0 }}>
                        <label className="admin-form-label" style={{ fontSize: '0.85rem' }}>Giải thích chi tiết (Explanation)</label>
                        <textarea
                          className="editor-textarea"
                          value={q.explanation || ''}
                          onChange={e => updateQuestionField(idx, 'explanation', e.target.value)}
                          placeholder="Nhập lý do tại sao đáp án này đúng và trích dẫn trong văn bản..."
                          style={{ minHeight: 60, width: '100%' }}
                        />
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>

            <div className="admin-form-actions" style={{ borderTop: '1px solid var(--border-color)', paddingTop: '1.5rem' }}>
              <button className="btn btn-outline" onClick={closeModal} type="button">Hủy</button>
              <button className="btn btn-primary" onClick={handleSave} disabled={saving} type="button">
                {saving && <span className="spinner" />}
                {editing ? 'Cập nhật đề' : 'Tạo đề mới'}
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
              Xác nhận xóa
            </h2>
            <p style={{ color: 'var(--text-secondary)', fontSize: '0.9rem', marginBottom: 24, lineHeight: 1.6 }}>
              Bạn có chắc chắn muốn xóa đề đọc này? Hành động này không thể hoàn tác.
            </p>
            <div className="admin-form-actions">
              <button className="btn btn-outline" onClick={() => setDeleteId(null)}>Hủy</button>
              <button className="btn admin-btn-danger-fill" onClick={handleDelete} disabled={deleting}>
                {deleting && <span className="spinner" />}
                Xóa đề
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
