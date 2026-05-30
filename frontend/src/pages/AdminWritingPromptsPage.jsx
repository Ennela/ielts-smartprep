import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import adminApi from '../api/adminApi';

const TASK1_TYPES = ['LINE_GRAPH', 'BAR_CHART', 'PIE_CHART', 'TABLE', 'MAP', 'DIAGRAM'];
const TASK2_TYPES = ['OPINION', 'DISCUSSION', 'CAUSE_AND_EFFECT', 'PROBLEM_AND_SOLUTION', 'ADVANTAGES_DISADVANTAGES', 'TWO_PART_QUESTION'];
const ALL_TYPES = [...TASK2_TYPES, ...TASK1_TYPES];

const TYPE_LABELS = {
  OPINION: 'Opinion', DISCUSSION: 'Discussion', CAUSE_AND_EFFECT: 'Cause & Effect',
  PROBLEM_AND_SOLUTION: 'Problem & Solution', ADVANTAGES_DISADVANTAGES: 'Advantages & Disadvantages',
  TWO_PART_QUESTION: 'Two-Part Question', LINE_GRAPH: 'Line Graph', BAR_CHART: 'Bar Chart',
  PIE_CHART: 'Pie Chart', TABLE: 'Table', MAP: 'Map', DIAGRAM: 'Diagram',
};

export default function AdminWritingPromptsPage() {
  const navigate = useNavigate();
  const [prompts, setPrompts] = useState(null);
  const [filter, setFilter] = useState('');
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [successMsg, setSuccessMsg] = useState(null);

  // Modal state
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState(null); // null = create, object = edit
  const [form, setForm] = useState({ promptText: '', essayType: 'OPINION', imageUrl: '' });
  const [saving, setSaving] = useState(false);

  // Delete confirm
  const [deleteId, setDeleteId] = useState(null);
  const [deleting, setDeleting] = useState(false);

  const fetchPrompts = (essayType, pageVal) => {
    setLoading(true);
    adminApi.listWritingPrompts(essayType || null, pageVal, 10)
      .then(res => setPrompts(res.data?.data))
      .catch(err => setError(err.message))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    fetchPrompts(filter, page);
  }, [filter, page]);

  const openCreate = () => {
    setEditing(null);
    setForm({ promptText: '', essayType: 'OPINION', imageUrl: '' });
    setModalOpen(true);
  };

  const openEdit = (prompt) => {
    setEditing(prompt);
    setForm({
      promptText: prompt.promptText || '',
      essayType: prompt.essayType || 'OPINION',
      imageUrl: prompt.imageUrl || '',
    });
    setModalOpen(true);
  };

  const closeModal = () => {
    setModalOpen(false);
    setEditing(null);
    setError(null);
  };

  const handleSave = async () => {
    if (!form.promptText.trim()) { setError('Nội dung đề không được để trống'); return; }
    setSaving(true);
    setError(null);
    try {
      const payload = {
        promptText: form.promptText.trim(),
        essayType: form.essayType,
        imageUrl: form.imageUrl.trim() || null,
      };
      if (editing) {
        await adminApi.updateWritingPrompt(editing.promptId, payload);
        setSuccessMsg('Cập nhật đề thành công!');
      } else {
        await adminApi.createWritingPrompt(payload);
        setSuccessMsg('Tạo đề mới thành công!');
      }
      closeModal();
      fetchPrompts(filter, page);
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
      await adminApi.deleteWritingPrompt(deleteId);
      setDeleteId(null);
      setSuccessMsg('Đã xóa đề thành công!');
      fetchPrompts(filter, page);
      setTimeout(() => setSuccessMsg(null), 3000);
    } catch (err) {
      setError(err.message);
    } finally {
      setDeleting(false);
    }
  };

  const content = prompts?.content || [];
  const totalPages = prompts?.totalPages || 0;
  const totalElements = prompts?.totalElements || 0;

  const isTask1 = (type) => TASK1_TYPES.includes(type);

  return (
    <div className="admin-dashboard-content">
      {/* Header */}
      <div className="admin-dash-header reveal">
        <div>
          <button className="btn-back" onClick={() => navigate('/admin')} id="back-to-admin">
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2"><path d="M19 12H5M12 19l-7-7 7-7"/></svg>
            Tổng quan
          </button>
          <h1>Quản lý đề viết</h1>
          <p className="subtitle">{totalElements} đề viết trong hệ thống</p>
        </div>
        <button className="btn btn-primary" onClick={openCreate} id="create-prompt-btn">
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
          Thêm đề mới
        </button>
      </div>

      {successMsg && <div className="success-msg">{successMsg}</div>}
      {error && !modalOpen && <div className="error-msg">{error}</div>}

      {/* Filter */}
      <div className="writing-filter reveal reveal-delay-1">
        <button className={`filter-btn ${filter === '' ? 'active' : ''}`} onClick={() => { setFilter(''); setPage(0); }}>Tất cả</button>
        {ALL_TYPES.map(t => (
          <button key={t} className={`filter-btn ${filter === t ? 'active' : ''}`} onClick={() => { setFilter(t); setPage(0); }}>
            {TYPE_LABELS[t]}
          </button>
        ))}
      </div>

      {/* Table */}
      <div className="admin-table-section reveal reveal-delay-2">
        {loading ? (
          <div className="loading-spinner"><div className="spinner" /></div>
        ) : content.length === 0 ? (
          <div className="empty-state">
            <p>Không tìm thấy đề viết nào{filter ? ` loại "${TYPE_LABELS[filter]}"` : ''}.</p>
          </div>
        ) : (
          <>
            <div className="history-table-wrapper">
              <table className="history-table" id="admin-prompts-table">
                <thead>
                  <tr>
                    <th>#</th>
                    <th>Task</th>
                    <th>Loại đề</th>
                    <th>Nội dung</th>
                    <th>Ngày tạo</th>
                    <th>Hành động</th>
                  </tr>
                </thead>
                <tbody>
                  {content.map((p, idx) => (
                    <tr key={p.promptId}>
                      <td>{page * 10 + idx + 1}</td>
                      <td>
                        <span className={`essay-type-badge ${isTask1(p.essayType) ? 'badge-line-graph' : 'badge-opinion'}`}>
                          {isTask1(p.essayType) ? 'Task 1' : 'Task 2'}
                        </span>
                      </td>
                      <td>
                        <span className={`essay-type-badge badge-${p.essayType?.toLowerCase()}`}>
                          {TYPE_LABELS[p.essayType] || p.essayType}
                        </span>
                      </td>
                      <td className="admin-prompt-text-cell">
                        {p.promptText?.length > 100
                          ? p.promptText.substring(0, 100) + '...'
                          : p.promptText}
                      </td>
                      <td className="ht-date">{formatDate(p.createdAt)}</td>
                      <td>
                        <div className="admin-action-btns">
                          <button
                            className="btn btn-sm btn-outline"
                            onClick={() => openEdit(p)}
                            id={`edit-prompt-${p.promptId}`}
                          >Sửa</button>
                          <button
                            className="btn btn-sm admin-btn-danger"
                            onClick={() => setDeleteId(p.promptId)}
                            id={`delete-prompt-${p.promptId}`}
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
          <div className="admin-modal admin-modal-wide" onClick={(e) => e.stopPropagation()}>
            <button className="admin-modal-close" onClick={closeModal} id="close-prompt-modal">
              <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
            </button>

            <h2 style={{ fontFamily: 'var(--font-heading)', fontSize: '1.2rem', fontWeight: 700, marginBottom: 24 }}>
              {editing ? 'Chỉnh sửa đề viết' : 'Tạo đề viết mới'}
            </h2>

            {error && <div className="error-msg">{error}</div>}

            <div className="admin-form-group">
              <label className="admin-form-label">Loại đề</label>
              <select
                className="matching-select"
                value={form.essayType}
                onChange={e => setForm(f => ({ ...f, essayType: e.target.value }))}
                style={{ maxWidth: '100%' }}
              >
                <optgroup label="Task 2">
                  {TASK2_TYPES.map(t => <option key={t} value={t}>{TYPE_LABELS[t]}</option>)}
                </optgroup>
                <optgroup label="Task 1">
                  {TASK1_TYPES.map(t => <option key={t} value={t}>{TYPE_LABELS[t]}</option>)}
                </optgroup>
              </select>
            </div>

            <div className="admin-form-group">
              <label className="admin-form-label">Nội dung đề</label>
              <textarea
                className="editor-textarea"
                value={form.promptText}
                onChange={e => setForm(f => ({ ...f, promptText: e.target.value }))}
                placeholder="Nhập nội dung đề viết..."
                style={{ minHeight: 160 }}
              />
            </div>

            {isTask1(form.essayType) && (
              <div className="admin-form-group">
                <label className="admin-form-label">URL hình ảnh (Task 1)</label>
                <input
                  type="text"
                  className="completion-input"
                  value={form.imageUrl}
                  onChange={e => setForm(f => ({ ...f, imageUrl: e.target.value }))}
                  placeholder="https://example.com/image.jpg"
                  style={{ maxWidth: '100%' }}
                />
                {form.imageUrl && (
                  <div className="prompt-image-container" style={{ marginTop: 12 }}>
                    <img src={form.imageUrl} alt="Preview" className="prompt-image" onError={(e) => { e.target.style.display = 'none'; }} />
                  </div>
                )}
              </div>
            )}

            <div className="admin-form-actions">
              <button className="btn btn-outline" onClick={closeModal}>Hủy</button>
              <button className="btn btn-primary" onClick={handleSave} disabled={saving}>
                {saving && <span className="spinner" />}
                {editing ? 'Cập nhật' : 'Tạo đề'}
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
            <p style={{ color: 'var(--color-text-secondary)', fontSize: '0.9rem', marginBottom: 24, lineHeight: 1.6 }}>
              Bạn có chắc chắn muốn xóa đề viết này? Hành động này không thể hoàn tác.
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

function formatDate(dateStr) {
  if (!dateStr) return '—';
  const d = new Date(dateStr);
  return d.toLocaleDateString('vi-VN', { day: '2-digit', month: 'short', year: 'numeric' });
}
