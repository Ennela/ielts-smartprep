import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import adminApi from '../api/adminApi';
import ImageUploadField from '../components/admin/ImageUploadField';
import { usePaginatedQuery } from '../hooks/usePaginatedQuery';
import Pagination from '../components/Pagination';
import ArchivedToggle from '../components/admin/ArchivedToggle';
import Modal from '../components/common/Modal';

import {
  TASK1_TYPES, TASK2_TYPES, ALL_ESSAY_TYPES as ALL_TYPES,
  ESSAY_TYPE_LABELS as TYPE_LABELS, isTask1Type,
} from '../constants/examTypes';
import errorMessage from '../utils/errorMessage';

// The topics learners pick from when generating a Writing test.
const TOPICS = [
  { value: 'EDUCATION', label: 'Education' },
  { value: 'TECHNOLOGY', label: 'Technology' },
  { value: 'HEALTH', label: 'Health' },
  { value: 'ENVIRONMENT', label: 'Environment' },
  { value: 'HISTORY', label: 'History' },
];

// The bank serves only PUBLISHED prompts; publishing passes through HUMAN_REVIEWED.
const STATUS_BADGES = {
  DRAFT: { label: 'Draft', className: 'badge-status-warning' },
  AI_IMPORTED: { label: 'Imported', className: 'badge-status-warning' },
  HUMAN_REVIEWED: { label: 'Reviewed', className: 'badge-status-info' },
  PUBLISHED: { label: 'Published', className: 'badge-status-success' },
};

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

  // NotebookLM import; essayType '' asks for the Task 1 + Task 2 pair.
  const [importOpen, setImportOpen] = useState(false);
  const [importForm, setImportForm] = useState({ topic: 'EDUCATION', essayType: '', content: '' });
  const [importError, setImportError] = useState(null);
  const [importing, setImporting] = useState(false);
  const [copied, setCopied] = useState(null); // 'instructions' | 'message'
  const [sourceTitle, setSourceTitle] = useState(null);
  const [statusBusyId, setStatusBusyId] = useState(null);

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
      setError(errorMessage(err, 'Failed to save the prompt'));
    } finally {
      setSaving(false);
    }
  };


  const openImport = () => {
    setImportForm(f => ({ ...f, content: '' }));
    setImportError(null);
    setCopied(null);
    setSourceTitle(null);
    setImportOpen(true);
  };

  // kind: 'instructions' (added once as a NotebookLM source) or 'message' (the chat).
  const handleCopy = async (kind) => {
    setImportError(null);
    try {
      const res = await adminApi.getWritingImportPrompt(importForm.topic, importForm.essayType || null);
      await navigator.clipboard.writeText(res.data.data[kind]);
      setSourceTitle(res.data.data.sourceTitle);
      setCopied(kind);
    } catch (err) {
      setImportError(errorMessage(err, 'Failed to copy'));
    }
  };

  const handleImport = async () => {
    if (!importForm.content.trim()) { setImportError('Paste the JSON NotebookLM returned'); return; }
    setImporting(true);
    setImportError(null);
    try {
      const res = await adminApi.importWritingPrompts({ topic: importForm.topic, content: importForm.content });
      const count = res.data.data?.length || 0;
      setImportOpen(false);
      setSuccessMsg(`${count} prompt${count === 1 ? '' : 's'} imported. Preview, then Publish to add to the question bank.`);
      invalidateList();
      setTimeout(() => setSuccessMsg(null), 5000);
    } catch (err) {
      setImportError(errorMessage(err, 'Failed to import prompts'));
    } finally {
      setImporting(false);
    }
  };

  const handlePublish = async (p) => {
    setStatusBusyId(p.promptId);
    try {
      if (p.contentStatus !== 'HUMAN_REVIEWED') {
        await adminApi.updateContentStatus('WRITING', p.promptId, 'HUMAN_REVIEWED');
      }
      await adminApi.updateContentStatus('WRITING', p.promptId, 'PUBLISHED');
      setSuccessMsg('Prompt published to the question bank.');
      setTimeout(() => setSuccessMsg(null), 3000);
    } catch (err) {
      setError(errorMessage(err, 'Failed to publish'));
    } finally {
      setStatusBusyId(null);
      invalidateList();
    }
  };

  const handleUnpublish = async (p) => {
    setStatusBusyId(p.promptId);
    try {
      await adminApi.updateContentStatus('WRITING', p.promptId, 'DRAFT');
      setSuccessMsg('Prompt withdrawn from the question bank.');
      setTimeout(() => setSuccessMsg(null), 3000);
    } catch (err) {
      setError(errorMessage(err, 'Failed to unpublish'));
    } finally {
      setStatusBusyId(null);
      invalidateList();
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
      setError(errorMessage(err, 'Failed to restore'));
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
      setError(errorMessage(err, 'Failed to archive the prompt'));
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
        <div style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap' }}>
          <button className="btn btn-outline" onClick={openImport} id="import-prompt-btn">
            Import from NotebookLM
          </button>
          <button className="btn btn-primary" onClick={openCreate} id="create-prompt-btn">
            <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
            Add New Prompt
          </button>
        </div>
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
                    <th>Topic</th>
                    <th>Status</th>
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
                      <td>{TOPICS.find(t => t.value === p.topic)?.label || p.topic || '-'}</td>
                      <td>
                        {STATUS_BADGES[p.contentStatus] ? (
                          <span className={`essay-type-badge ${STATUS_BADGES[p.contentStatus].className}`}>
                            {STATUS_BADGES[p.contentStatus].label}
                          </span>
                        ) : (p.contentStatus || '-')}
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
                              >Preview</button>
                              <button
                                className="btn btn-sm btn-outline"
                                onClick={() => openEdit(p)}
                                id={`edit-prompt-${p.promptId}`}
                              >Edit</button>
                              {p.contentStatus === 'PUBLISHED' ? (
                                <button
                                  className="btn btn-sm btn-outline"
                                  onClick={() => handleUnpublish(p)}
                                  disabled={statusBusyId === p.promptId}
                                  id={`unpublish-prompt-${p.promptId}`}
                                >Unpublish</button>
                              ) : (
                                <button
                                  className="btn btn-sm btn-primary"
                                  onClick={() => handlePublish(p)}
                                  disabled={statusBusyId === p.promptId}
                                  id={`publish-prompt-${p.promptId}`}
                                >Publish</button>
                              )}
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
        <Modal ariaLabel="Writing prompt form" onClose={closeModal} className="admin-modal admin-modal-wide">
          <button className="admin-modal-close" onClick={closeModal} aria-label="Close" id="close-prompt-modal">
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
              <ImageUploadField
                id="writing-prompt-image"
                label="Chart / diagram (Task 1)"
                value={form.imageUrl}
                onChange={url => setForm(f => ({ ...f, imageUrl: url }))}
              />
            </div>
          )}

          <div className="admin-form-actions">
            <button className="btn btn-outline" onClick={closeModal}>Cancel</button>
            <button className="btn btn-primary" onClick={handleSave} disabled={saving}>
              {saving && <span className="spinner" />}
              {editing ? 'Update' : 'Create Prompt'}
            </button>
          </div>
        </Modal>
      )}

      {/* NotebookLM Import Modal */}
      {importOpen && (
        <Modal ariaLabel="Import writing prompts" onClose={() => setImportOpen(false)} className="admin-modal admin-modal-wide" style={{ maxWidth: '760px', width: '90%', maxHeight: '90vh', overflowY: 'auto' }}>
          <button className="admin-modal-close" onClick={() => setImportOpen(false)} aria-label="Close" id="close-import-modal">
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
          </button>

          <h2 style={{ fontFamily: 'var(--font-heading)', fontSize: '1.25rem', fontWeight: 700, marginBottom: 12 }}>
            Import from NotebookLM
          </h2>
          <ol style={{ color: 'var(--text-secondary)', fontSize: '0.9rem', lineHeight: 1.6, margin: '0 0 1.25rem 1.25rem' }}>
            <li>Once: copy the format instructions and add them to your NotebookLM notebook as a <em>Copied text</em> source.</li>
            <li>For each prompt: copy the chat message and send it in the notebook chat.</li>
            <li>Paste the JSON it returns below (Task 1, Task 2 or both). The prompts are saved as Imported; Publish them once you have checked them.</li>
          </ol>

          {importError && <div className="error-msg" style={{ marginBottom: '1rem' }}>{importError}</div>}

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '1rem', marginBottom: '1rem', alignItems: 'end' }}>
            <div className="admin-form-group">
              <label className="admin-form-label" htmlFor="import-topic">Topic</label>
              <select
                id="import-topic"
                className="matching-select"
                value={importForm.topic}
                onChange={e => { setImportForm(f => ({ ...f, topic: e.target.value })); setCopied(null); }}
                style={{ width: '100%', maxWidth: '100%' }}
              >
                {TOPICS.map(t => <option key={t.value} value={t.value}>{t.label}</option>)}
              </select>
            </div>
            <div className="admin-form-group">
              <label className="admin-form-label" htmlFor="import-essay-type">Ask for</label>
              <select
                id="import-essay-type"
                className="matching-select"
                value={importForm.essayType}
                onChange={e => { setImportForm(f => ({ ...f, essayType: e.target.value })); setCopied(null); }}
                style={{ width: '100%', maxWidth: '100%' }}
              >
                <option value="">Task 1 + Task 2 pair (Academic)</option>
                <optgroup label="Task 1 only">
                  {TASK1_TYPES.map(t => <option key={t} value={t}>{TYPE_LABELS[t]}</option>)}
                </optgroup>
                <optgroup label="Task 2 only">
                  {TASK2_TYPES.map(t => <option key={t} value={t}>{TYPE_LABELS[t]}</option>)}
                </optgroup>
              </select>
            </div>
          </div>

          <div style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap', marginBottom: '0.5rem' }}>
            <button type="button" className="btn btn-outline" onClick={() => handleCopy('instructions')} id="copy-import-instructions">
              {copied === 'instructions' ? 'Instructions copied' : '1. Copy format instructions'}
            </button>
            <button type="button" className="btn btn-outline" onClick={() => handleCopy('message')} id="copy-import-message">
              {copied === 'message' ? 'Message copied' : '2. Copy chat message'}
            </button>
          </div>
          {sourceTitle && (
            <p style={{ color: 'var(--text-secondary)', fontSize: '0.85rem', marginBottom: '1rem' }}>
              The chat message refers to the source as <strong>{sourceTitle}</strong>. Give the source that title in NotebookLM.
            </p>
          )}

          <div className="admin-form-group" style={{ marginBottom: '1.5rem' }}>
            <label className="admin-form-label" htmlFor="import-content">JSON from NotebookLM</label>
            <textarea
              id="import-content"
              className="editor-textarea"
              value={importForm.content}
              onChange={e => setImportForm(f => ({ ...f, content: e.target.value }))}
              placeholder='{ "task1": { "promptText": "...", "essayType": "BAR_CHART", "visualData": { ... } }, "task2": { ... } }'
              style={{ minHeight: 240, width: '100%', fontFamily: 'monospace', fontSize: '0.85rem' }}
            />
          </div>

          <div className="admin-form-actions">
            <button className="btn btn-outline" onClick={() => setImportOpen(false)} type="button">Cancel</button>
            <button className="btn btn-primary" onClick={handleImport} disabled={importing} type="button" id="submit-import">
              {importing && <span className="spinner" />}
              Import Prompts
            </button>
          </div>
        </Modal>
      )}

      {/* Delete Confirm Modal */}
      {deleteId && (
        <Modal ariaLabel="Delete writing prompt" onClose={() => setDeleteId(null)} className="admin-modal" style={{ maxWidth: 420 }}>
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
        </Modal>
      )}
    </div>
  );
}

function formatDate(dateStr) {
  if (!dateStr) return '—';
  const d = new Date(dateStr);
  return d.toLocaleDateString('en-US', { day: '2-digit', month: 'short', year: 'numeric' });
}
