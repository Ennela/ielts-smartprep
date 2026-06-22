import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import adminApi from '../api/adminApi';
import listeningApi from '../api/listeningApi';
import { usePaginatedQuery } from '../hooks/usePaginatedQuery';
import Pagination from '../components/Pagination';

export default function AdminMockTestsPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [error, setError] = useState(null);
  const [successMsg, setSuccessMsg] = useState(null);

  // Available components for builders
  const [allListeningParts, setAllListeningParts] = useState([]);
  const [allReadingQuizzes, setAllReadingQuizzes] = useState([]);
  const [allWritingPrompts, setAllWritingPrompts] = useState([]);
  const [componentsLoading, setComponentsLoading] = useState(false);

  // Modal state
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState(null); // null = create, object = edit
  const [form, setForm] = useState({
    title: '',
    description: '',
    difficulty: 'MEDIUM',
    listeningPart1: '',
    listeningPart2: '',
    listeningPart3: '',
    listeningPart4: '',
    readingPassage1: '',
    readingPassage2: '',
    readingPassage3: '',
    writingTask1: '',
    writingTask2: '',
  });
  const [saving, setSaving] = useState(false);

  // Delete state
  const [deleteId, setDeleteId] = useState(null);
  const [deleting, setDeleting] = useState(false);

  const {
    content,
    totalPages,
    totalElements,
    page,
    size,
    setPage,
    isLoading,
    isFetching,
    isPlaceholderData,
  } = usePaginatedQuery({
    queryKey: ['admin', 'mock-tests'],
    queryFn: (pg, sz) => adminApi.listMockTests(pg, sz),
  });

  const invalidateList = () => {
    queryClient.invalidateQueries({ queryKey: ['admin', 'mock-tests'] });
  };

  const fetchComponents = async () => {
    setComponentsLoading(true);
    try {
      const [lRes, rRes, wRes] = await Promise.all([
        listeningApi.getAllParts(),
        adminApi.listReadingQuizzes(null, null, null, 0, 100),
        adminApi.listWritingPrompts(null, 0, 100),
      ]);
      setAllListeningParts(lRes.data?.data || []);
      setAllReadingQuizzes(rRes.data?.data?.content || []);
      setAllWritingPrompts(wRes.data?.data?.content || []);
    } catch (err) {
      console.error("Failed to load mock components", err);
    } finally {
      setComponentsLoading(false);
    }
  };

  useEffect(() => {
    fetchComponents();
  }, []);

  const openCreate = () => {
    setEditing(null);
    setForm({
      title: '',
      description: '',
      difficulty: 'MEDIUM',
      listeningPart1: allListeningParts[0]?.partId || '',
      listeningPart2: allListeningParts[1]?.partId || '',
      listeningPart3: allListeningParts[2]?.partId || '',
      listeningPart4: allListeningParts[3]?.partId || '',
      readingPassage1: allReadingQuizzes[0]?.quizId || '',
      readingPassage2: allReadingQuizzes[1]?.quizId || '',
      readingPassage3: allReadingQuizzes[2]?.quizId || '',
      writingTask1: allWritingPrompts.find(p => p.essayType.includes('GRAPH') || p.essayType.includes('CHART') || p.essayType === 'TABLE' || p.essayType === 'MAP' || p.essayType === 'DIAGRAM')?.promptId || '',
      writingTask2: allWritingPrompts.find(p => !p.essayType.includes('GRAPH') && !p.essayType.includes('CHART') && p.essayType !== 'TABLE' && p.essayType !== 'MAP' && p.essayType !== 'DIAGRAM')?.promptId || '',
    });
    setModalOpen(true);
  };

  const openEdit = (test) => {
    setEditing(test);
    setForm({
      title: test.title || '',
      description: test.description || '',
      difficulty: test.difficulty || 'MEDIUM',
      listeningPart1: test.listeningPartIds?.[0] || '',
      listeningPart2: test.listeningPartIds?.[1] || '',
      listeningPart3: test.listeningPartIds?.[2] || '',
      listeningPart4: test.listeningPartIds?.[3] || '',
      readingPassage1: test.readingQuizIds?.[0] || '',
      readingPassage2: test.readingQuizIds?.[1] || '',
      readingPassage3: test.readingQuizIds?.[2] || '',
      writingTask1: test.writingPromptIds?.[0] || '',
      writingTask2: test.writingPromptIds?.[1] || '',
    });
    setModalOpen(true);
  };

  const closeModal = () => {
    setModalOpen(false);
    setEditing(null);
    setError(null);
  };

  const handleSave = async () => {
    if (!form.title.trim()) { setError('Mock test title cannot be empty'); return; }
    if (!form.listeningPart1 || !form.listeningPart2 || !form.listeningPart3 || !form.listeningPart4) {
      setError('You must select exactly 4 Listening parts (Parts 1 to 4)'); return;
    }
    if (!form.readingPassage1 || !form.readingPassage2 || !form.readingPassage3) {
      setError('You must select exactly 3 Reading passages (Passages 1 to 3)'); return;
    }
    if (!form.writingTask1 || !form.writingTask2) {
      setError('You must select exactly 2 Writing prompts (Task 1 and Task 2)'); return;
    }

    setSaving(true);
    setError(null);
    try {
      const payload = {
        title: form.title.trim(),
        description: form.description.trim(),
        difficulty: form.difficulty,
        listeningPartIds: [form.listeningPart1, form.listeningPart2, form.listeningPart3, form.listeningPart4].map(Number),
        readingQuizIds: [form.readingPassage1, form.readingPassage2, form.readingPassage3].map(Number),
        writingPromptIds: [form.writingTask1, form.writingTask2].map(Number),
      };

      if (editing) {
        await adminApi.updateMockTest(editing.mockTestId, payload);
        setSuccessMsg('Mock test updated successfully!');
      } else {
        await adminApi.createMockTest(payload);
        setSuccessMsg('Mock test created successfully!');
      }
      closeModal();
      invalidateList();
      setTimeout(() => setSuccessMsg(null), 3000);
    } catch (err) {
      setError(err.message);
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async () => {
    if (!deleteId) return;
    setDeleting(true);
    try {
      await adminApi.deleteMockTest(deleteId);
      setDeleteId(null);
      setSuccessMsg('Mock test deleted successfully!');
      invalidateList();
      setTimeout(() => setSuccessMsg(null), 3000);
    } catch (err) {
      setError(err.message);
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
          <h1>IELTS Mock Tests Builder</h1>
          <p className="subtitle">{totalElements} active mock tests configured</p>
        </div>
        <button
          className="btn btn-primary"
          onClick={openCreate}
          disabled={componentsLoading || allListeningParts.length < 4 || allReadingQuizzes.length < 3 || allWritingPrompts.length < 2}
          id="create-mock-btn"
        >
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
          Build Full Mock Test
        </button>
      </div>

      {/* Dependency Warning */}
      {!componentsLoading && (allListeningParts.length < 4 || allReadingQuizzes.length < 3 || allWritingPrompts.length < 2) && (
        <div className="error-msg" style={{ marginTop: 12 }}>
          ⚠️ You need at least 4 listening parts, 3 reading passages, and 2 writing prompts in the system database to construct a new mock test.
        </div>
      )}

      {successMsg && <div className="success-msg">{successMsg}</div>}
      {error && !modalOpen && <div className="error-msg">{error}</div>}

      {/* Main Table */}
      <div className={`admin-table-section reveal reveal-delay-1${isFetching && isPlaceholderData ? ' is-fetching' : ''}`} style={{ marginTop: 24 }}>
        {isLoading ? (
          <div className="loading-spinner"><div className="spinner" /></div>
        ) : content.length === 0 ? (
          <div className="empty-state">
            <p>No full mock tests configured yet. Click "Build Full Mock Test" to assemble your first exam template.</p>
          </div>
        ) : (
          <>
            <div className="history-table-wrapper">
              <table className="history-table" id="admin-mock-tests-table">
                <thead>
                  <tr>
                    <th>#</th>
                    <th>Mock Test Title</th>
                    <th>Difficulty</th>
                    <th>Listening Parts</th>
                    <th>Reading Passages</th>
                    <th>Writing Tasks</th>
                    <th>Action</th>
                  </tr>
                </thead>
                <tbody>
                  {content.map((t, idx) => (
                    <tr key={t.mockTestId}>
                      <td>{page * size + idx + 1}</td>
                      <td>
                        <div style={{ fontWeight: 600 }}>{t.title}</div>
                        <div style={{ fontSize: '0.8rem', color: 'var(--color-text-secondary)' }}>{t.description || 'No description provided'}</div>
                      </td>
                      <td>
                        <span className={`difficulty-badge diff-${t.difficulty?.toLowerCase()}`}>
                          {t.difficulty}
                        </span>
                      </td>
                      <td>{t.listeningPartsCount} Parts</td>
                      <td>{t.readingQuizzesCount} Passages</td>
                      <td>{t.writingPromptsCount} Tasks</td>
                      <td>
                        <div className="admin-action-btns">
                          <button
                            className="btn btn-sm btn-outline"
                            onClick={() => openEdit(t)}
                            id={`edit-mock-${t.mockTestId}`}
                          >Edit</button>
                          <button
                            className="btn btn-sm admin-btn-danger"
                            onClick={() => setDeleteId(t.mockTestId)}
                            id={`delete-mock-${t.mockTestId}`}
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
          <div className="admin-modal admin-modal-wide" onClick={(e) => e.stopPropagation()} style={{ maxWidth: 800 }}>
            <button className="admin-modal-close" onClick={closeModal} id="close-mock-modal">
              <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
            </button>

            <h2 style={{ fontFamily: 'var(--font-heading)', fontSize: '1.2rem', fontWeight: 700, marginBottom: 20 }}>
              {editing ? 'Modify Mock Test Template' : 'Assemble Full Mock Test'}
            </h2>

            {error && <div className="error-msg" style={{ marginBottom: 16 }}>{error}</div>}

            <div className="grid grid-cols-2 gap-4">
              <div className="admin-form-group">
                <label className="admin-form-label">Mock Test Title</label>
                <input
                  type="text"
                  className="completion-input"
                  value={form.title}
                  onChange={e => setForm(f => ({ ...f, title: e.target.value }))}
                  placeholder="e.g. IELTS Academic Mock Test 01"
                  style={{ maxWidth: '100%' }}
                />
              </div>

              <div className="admin-form-group">
                <label className="admin-form-label">Difficulty Rating</label>
                <select
                  className="matching-select"
                  value={form.difficulty}
                  onChange={e => setForm(f => ({ ...f, difficulty: e.target.value }))}
                  style={{ maxWidth: '100%' }}
                >
                  <option value="EASY">EASY</option>
                  <option value="MEDIUM">MEDIUM</option>
                  <option value="HARD">HARD</option>
                </select>
              </div>
            </div>

            <div className="admin-form-group">
              <label className="admin-form-label">Mock Description</label>
              <textarea
                className="editor-textarea"
                value={form.description}
                onChange={e => setForm(f => ({ ...f, description: e.target.value }))}
                placeholder="Details about mock test source, format or instructions..."
                style={{ minHeight: 60 }}
              />
            </div>

            <div style={{ marginTop: 24, padding: 16, backgroundColor: 'rgba(0,0,0,0.02)', borderRadius: 8 }}>
              <h3 style={{ fontSize: '0.95rem', fontWeight: 650, marginBottom: 12, color: 'var(--color-primary)' }}>1. Listening Configuration (Select 4 Parts)</h3>
              <div className="grid grid-cols-2 gap-4">
                <div className="admin-form-group">
                  <label className="admin-form-label">Part 1 Section</label>
                  <select className="matching-select" value={form.listeningPart1} onChange={e => setForm(f => ({ ...f, listeningPart1: e.target.value }))} style={{ maxWidth: '100%' }}>
                    <option value="">-- Choose Listening Part --</option>
                    {allListeningParts.map(p => <option key={p.partId} value={p.partId}>Part {p.partNumber}: {p.title} ({p.topic})</option>)}
                  </select>
                </div>
                <div className="admin-form-group">
                  <label className="admin-form-label">Part 2 Section</label>
                  <select className="matching-select" value={form.listeningPart2} onChange={e => setForm(f => ({ ...f, listeningPart2: e.target.value }))} style={{ maxWidth: '100%' }}>
                    <option value="">-- Choose Listening Part --</option>
                    {allListeningParts.map(p => <option key={p.partId} value={p.partId}>Part {p.partNumber}: {p.title} ({p.topic})</option>)}
                  </select>
                </div>
                <div className="admin-form-group">
                  <label className="admin-form-label">Part 3 Section</label>
                  <select className="matching-select" value={form.listeningPart3} onChange={e => setForm(f => ({ ...f, listeningPart3: e.target.value }))} style={{ maxWidth: '100%' }}>
                    <option value="">-- Choose Listening Part --</option>
                    {allListeningParts.map(p => <option key={p.partId} value={p.partId}>Part {p.partNumber}: {p.title} ({p.topic})</option>)}
                  </select>
                </div>
                <div className="admin-form-group">
                  <label className="admin-form-label">Part 4 Section</label>
                  <select className="matching-select" value={form.listeningPart4} onChange={e => setForm(f => ({ ...f, listeningPart4: e.target.value }))} style={{ maxWidth: '100%' }}>
                    <option value="">-- Choose Listening Part --</option>
                    {allListeningParts.map(p => <option key={p.partId} value={p.partId}>Part {p.partNumber}: {p.title} ({p.topic})</option>)}
                  </select>
                </div>
              </div>
            </div>

            <div style={{ marginTop: 16, padding: 16, backgroundColor: 'rgba(0,0,0,0.02)', borderRadius: 8 }}>
              <h3 style={{ fontSize: '0.95rem', fontWeight: 650, marginBottom: 12, color: 'var(--color-primary)' }}>2. Reading Configuration (Select 3 Passages)</h3>
              <div className="grid grid-cols-3 gap-4">
                <div className="admin-form-group">
                  <label className="admin-form-label">Passage 1 Section</label>
                  <select className="matching-select" value={form.readingPassage1} onChange={e => setForm(f => ({ ...f, readingPassage1: e.target.value }))} style={{ maxWidth: '100%' }}>
                    <option value="">-- Choose Passage --</option>
                    {allReadingQuizzes.map(q => <option key={q.quizId} value={q.quizId}>{q.topic} - Passage ID: {q.quizId}</option>)}
                  </select>
                </div>
                <div className="admin-form-group">
                  <label className="admin-form-label">Passage 2 Section</label>
                  <select className="matching-select" value={form.readingPassage2} onChange={e => setForm(f => ({ ...f, readingPassage2: e.target.value }))} style={{ maxWidth: '100%' }}>
                    <option value="">-- Choose Passage --</option>
                    {allReadingQuizzes.map(q => <option key={q.quizId} value={q.quizId}>{q.topic} - Passage ID: {q.quizId}</option>)}
                  </select>
                </div>
                <div className="admin-form-group">
                  <label className="admin-form-label">Passage 3 Section</label>
                  <select className="matching-select" value={form.readingPassage3} onChange={e => setForm(f => ({ ...f, readingPassage3: e.target.value }))} style={{ maxWidth: '100%' }}>
                    <option value="">-- Choose Passage --</option>
                    {allReadingQuizzes.map(q => <option key={q.quizId} value={q.quizId}>{q.topic} - Passage ID: {q.quizId}</option>)}
                  </select>
                </div>
              </div>
            </div>

            <div style={{ marginTop: 16, padding: 16, backgroundColor: 'rgba(0,0,0,0.02)', borderRadius: 8 }}>
              <h3 style={{ fontSize: '0.95rem', fontWeight: 650, marginBottom: 12, color: 'var(--color-primary)' }}>3. Writing Configuration (Select 2 Tasks)</h3>
              <div className="grid grid-cols-2 gap-4">
                <div className="admin-form-group">
                  <label className="admin-form-label">Task 1 (Report/Chart)</label>
                  <select className="matching-select" value={form.writingTask1} onChange={e => setForm(f => ({ ...f, writingTask1: e.target.value }))} style={{ maxWidth: '100%' }}>
                    <option value="">-- Choose Task 1 Prompt --</option>
                    {allWritingPrompts.filter(p => p.essayType.includes('GRAPH') || p.essayType.includes('CHART') || p.essayType === 'TABLE' || p.essayType === 'MAP' || p.essayType === 'DIAGRAM').map(p => (
                      <option key={p.promptId} value={p.promptId}>[{p.essayType}] {p.promptText.substring(0, 50)}...</option>
                    ))}
                  </select>
                </div>
                <div className="admin-form-group">
                  <label className="admin-form-label">Task 2 (Essay)</label>
                  <select className="matching-select" value={form.writingTask2} onChange={e => setForm(f => ({ ...f, writingTask2: e.target.value }))} style={{ maxWidth: '100%' }}>
                    <option value="">-- Choose Task 2 Prompt --</option>
                    {allWritingPrompts.filter(p => !p.essayType.includes('GRAPH') && !p.essayType.includes('CHART') && p.essayType !== 'TABLE' && p.essayType !== 'MAP' && p.essayType !== 'DIAGRAM').map(p => (
                      <option key={p.promptId} value={p.promptId}>[{p.essayType}] {p.promptText.substring(0, 50)}...</option>
                    ))}
                  </select>
                </div>
              </div>
            </div>

            <div className="admin-form-actions" style={{ marginTop: 24 }}>
              <button className="btn btn-outline" onClick={closeModal}>Cancel</button>
              <button className="btn btn-primary" onClick={handleSave} disabled={saving}>
                {saving && <span className="spinner" />}
                {editing ? 'Update Mock Test' : 'Deploy Mock Test'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Delete Confirmation Modal */}
      {deleteId && (
        <div className="admin-modal-overlay" onClick={() => setDeleteId(null)}>
          <div className="admin-modal" onClick={(e) => e.stopPropagation()} style={{ maxWidth: 420 }}>
            <h2 style={{ fontFamily: 'var(--font-heading)', fontSize: '1.1rem', fontWeight: 700, marginBottom: 12 }}>
              Confirm Delete
            </h2>
            <p style={{ color: 'var(--color-text-secondary)', fontSize: '0.9rem', marginBottom: 24, lineHeight: 1.6 }}>
              Are you sure you want to delete this full mock test? This will remove the exam template setup, but student submissions and histories will remain untouched.
            </p>
            <div className="admin-form-actions">
              <button className="btn btn-outline" onClick={() => setDeleteId(null)}>Cancel</button>
              <button className="btn admin-btn-danger-fill" onClick={handleDelete} disabled={deleting}>
                {deleting && <span className="spinner" />}
                Delete Mock Test
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
