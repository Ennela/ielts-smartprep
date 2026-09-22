import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import adminApi from '../api/adminApi';
import { usePaginatedQuery } from '../hooks/usePaginatedQuery';
import Pagination from '../components/Pagination';
import ArchivedToggle from '../components/admin/ArchivedToggle';

import {
  TASK1_TYPES, TASK2_TYPES, ALL_ESSAY_TYPES as ALL_TYPES,
  ESSAY_TYPE_LABELS as TYPE_LABELS, isTask1Type,
} from '../constants/examTypes';

export default function AdminWritingPromptsPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [filter, setFilter] = useState('');
  const [showArchived, setShowArchived] = useState(false);
  const [error, setError] = useState(null);
  const [successMsg, setSuccessMsg] = useState(null);

  // Modal state
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState({ promptText: '', essayType: 'OPINION', imageUrl: '' });
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
    queryKey: ['admin', 'writing-prompts'],
    queryFn: (pg, sz) => adminApi.listWritingPrompts(filter || null, pg, sz, 'createdAt,desc', showArchived),
    filters: { filter, showArchived },
  });

  const invalidateList = () => {
    queryClient.invalidateQueries({ queryKey: ['admin', 'writing-prompts'] });
  };

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
    if (!form.promptText.trim()) { setError('Prompt content cannot be empty'); return; }
    setSaving(true);
    setError(null);
    try {
      const payload = {
        promptText: form.promptText.trim(),
        essayType: form.essayType,
        taskType: isTask1Type(form.essayType) ? 'TASK_1' : 'TASK_2',
        imageUrl: form.imageUrl.trim() || null,
      };
      if (editing) {
        await adminApi.updateWritingPrompt(editing.promptId, payload);
        setSuccessMsg('Prompt updated successfully!');
      } else {
        await adminApi.createWritingPrompt(payload);
        setSuccessMsg('Prompt created successfully!');
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


  // Archived view: put the row back in the active list.
  const handleRestore = async (id) => {
    try {
      await adminApi.restoreWritingPrompt(id);
      setSuccessMsg('Prompt restored.');
      invalidateList();
      setTimeout(() => setSuccessMsg(null), 3000);
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Failed to restore');
    }
  };

  const handleDelete = async () => {
    if (!deleteId) return;
    setDeleting(true);
    try {
      await adminApi.deleteWritingPrompt(deleteId);
      setDeleteId(null);
      setSuccessMsg('Prompt archived successfully!');
      invalidateList();
      setTimeout(() => setSuccessMsg(null), 3000);
    } catch (err) {
      setError(err.message);
    } finally {
      setDeleting(false);
    }
  };

  const isTask1 = isTask1Type;

  return (
    <div className="admin-dashboard-content">
      {/* Header */}
      <div className="admin-dash-header reveal">
        <div>
          <button className="btn-back" onClick={() => navigate('/admin')} id="back-to-admin">
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2"><path d="M19 12H5M12 19l-7-7 7-7"/></svg>
            Overview
          </button>
          <h1>Writing Prompts Management</h1>
          <p className="subtitle">{totalElements} {showArchived ? 'archived ' : ''}writing prompts in system</p>
        </div>
        <button className="btn btn-primary" onClick={openCreate} id="create-prompt-btn">
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
          Add New Prompt
        </button>
      </div>

      {successMsg && <div className="success-msg">{successMsg}</div>}
      {error && !modalOpen && <div className="error-msg">{error}</div>}

      {/* Filter */}
      <div className="writing-filter reveal reveal-delay-1">
        <button className={`filter-btn ${filter === '' ? 'active' : ''}`} onClick={() => { setFilter(''); resetPage(); }}>All</button>
        {ALL_TYPES.map(t => (
          <button key={t} className={`filter-btn ${filter === t ? 'active' : ''}`} onClick={() => { setFilter(t); resetPage(); }}>
            {TYPE_LABELS[t]}
          </button>
        ))}
        <ArchivedToggle archived={showArchived} onChange={(v) => { setShowArchived(v); resetPage(); }} />
      </div>

      {/* Table */}
      <div className={`admin-table-section reveal reveal-delay-2${isFetching && isPlaceholderData ? ' is-fetching' : ''}`}>
        {isLoading ? (
          <div className="loading-spinner"><div className="spinner" /></div>
        ) : content.length === 0 ? (
          <div className="empty-state">
            <p>No {showArchived ? 'archived ' : ''}writing prompts found{filter ? ` of type "${TYPE_LABELS[filter]}"` : ''}.</p>
          </div>
        ) : (
          <>
            <div className="history-table-wrapper">
              <table className="history-table" id="admin-prompts-table">
                <thead>
                  <tr>
                    <th>#</th>
                    <th>Task</th>
                    <th>Prompt Type</th>
                    <th>Content</th>
                    <th>Created Date</th>
                    <th>Action</th>
                  </tr>
                </thead>
                <tbody>
                  {content.map((p, idx) => (
                    <tr key={p.promptId}>
                      <td>{page * size + idx + 1}</td>
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
                          {showArchived ? (
                            <button
                              className="btn btn-sm btn-outline"
                              onClick={() => handleRestore(p.promptId)}
                              id={`restore-prompt-${p.promptId}`}
                            >Restore</button>
                          ) : (
                            <>
                              <button
                                className="btn btn-sm btn-outline"
                                onClick={() => navigate(`/writing/editor/${p.promptId}?preview=true&adminView=true`)}
                                id={`preview-prompt-${p.promptId}`}
                              >👁 Xem thử</button>
                              <button
                                className="btn btn-sm btn-outline"
                                onClick={() => openEdit(p)}
                                id={`edit-prompt-${p.promptId}`}
                              >Edit</button>
                              <button
                                className="btn btn-sm admin-btn-danger"
                                onClick={() => setDeleteId(p.promptId)}
                                id={`delete-prompt-${p.promptId}`}
                              >Delete</button>
                            </>
                          )}
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
          <div className="admin-modal admin-modal-wide" onClick={(e) => e.stopPropagation()}>
            <button className="admin-modal-close" onClick={closeModal} id="close-prompt-modal">
              <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
            </button>

            <h2 style={{ fontFamily: 'var(--font-heading)', fontSize: '1.2rem', fontWeight: 700, marginBottom: 24 }}>
              {editing ? 'Edit Writing Prompt' : 'Create New Writing Prompt'}
            </h2>

            {error && <div className="error-msg">{error}</div>}

            <div className="admin-form-group">
              <label className="admin-form-label">Prompt Type</label>
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
              <label className="admin-form-label">Prompt Content</label>
              <textarea
                className="editor-textarea"
                value={form.promptText}
                onChange={e => setForm(f => ({ ...f, promptText: e.target.value }))}
                placeholder="Enter prompt content..."
                style={{ minHeight: 160 }}
              />
            </div>

            {isTask1(form.essayType) && (
              <div className="admin-form-group">
                <label className="admin-form-label">Image URL (Task 1)</label>
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
                    <img src={form.imageUrl} alt="Preview" className="prompt-image" loading="lazy" onError={(e) => { e.target.style.display = 'none'; }} />
                  </div>
                )}
              </div>
            )}

            <div className="admin-form-actions">
              <button className="btn btn-outline" onClick={closeModal}>Cancel</button>
              <button className="btn btn-primary" onClick={handleSave} disabled={saving}>
                {saving && <span className="spinner" />}
                {editing ? 'Update' : 'Create Prompt'}
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
              Confirm Archive
            </h2>
            <p style={{ color: 'var(--color-text-secondary)', fontSize: '0.9rem', marginBottom: 24, lineHeight: 1.6 }}>
              Archive this writing prompt? It will disappear from active lists, while linked student submissions remain intact and the prompt can be restored.
            </p>
            <div className="admin-form-actions">
              <button className="btn btn-outline" onClick={() => setDeleteId(null)}>Cancel</button>
              <button className="btn admin-btn-danger-fill" onClick={handleDelete} disabled={deleting}>
                {deleting && <span className="spinner" />}
                Archive Prompt
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
  return d.toLocaleDateString('en-US', { day: '2-digit', month: 'short', year: 'numeric' });
}
