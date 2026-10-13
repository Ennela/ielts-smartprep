import { useEffect, useState, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQueryClient, useQuery } from '@tanstack/react-query';
import adminApi from '../api/adminApi';
import { usePaginatedQuery } from '../hooks/usePaginatedQuery';
import Pagination from '../components/Pagination';
import ArchivedToggle from '../components/admin/ArchivedToggle';
import Modal from '../components/common/Modal';
import errorMessage from '../utils/errorMessage';

const TOPICS = [
  { value: 'ACCOMMODATION', label: 'Accommodation / Booking' },
  { value: 'EDUCATION', label: 'Campus / Education' },
  { value: 'CULTURE', label: 'Culture / Museum' },
  { value: 'SCIENCE', label: 'Science / Technology' },
  { value: 'ENVIRONMENT', label: 'Environment / Nature' },
];

// The bank serves only PUBLISHED parts with audio ready; publishing passes through HUMAN_REVIEWED.
const STATUS_BADGES = {
  DRAFT: { label: 'Draft', className: 'badge-status-warning' },
  AI_IMPORTED: { label: 'Imported', className: 'badge-status-warning' },
  HUMAN_REVIEWED: { label: 'Reviewed', className: 'badge-status-info' },
  PUBLISHED: { label: 'Published', className: 'badge-status-success' },
};

export default function AdminListeningListPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [filterTopic, setFilterTopic] = useState('');
  const [filterStatus, setFilterStatus] = useState('');
  const [error, setError] = useState(null);
  const [successMsg, setSuccessMsg] = useState(null);

  // Audio Preview state
  const [previewAudioUrl, setPreviewAudioUrl] = useState(null);
  const [previewTitle, setPreviewTitle] = useState('');
  const audioRef = useRef(null);

  // Delete state
  const [deleteId, setDeleteId] = useState(null);
  const [deleting, setDeleting] = useState(false);
  const [showArchived, setShowArchived] = useState(false);

  // NotebookLM import
  const [importOpen, setImportOpen] = useState(false);
  const [importForm, setImportForm] = useState({ partNumber: 1, topic: 'ACCOMMODATION', content: '' });
  const [importError, setImportError] = useState(null);
  const [importing, setImporting] = useState(false);
  const [copied, setCopied] = useState(null); // 'instructions' | 'message'
  const [sourceTitle, setSourceTitle] = useState(null);
  const [statusBusyId, setStatusBusyId] = useState(null);

  // Detect if any PENDING parts exist for auto-polling
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
    refetch: refetchParts,
  } = usePaginatedQuery({
    queryKey: ['admin', 'listening-parts'],
    queryFn: (pg, sz) => adminApi.listListeningParts(
      filterStatus || null, filterTopic || null, pg, sz, 'createdAt,desc', showArchived
    ),
    filters: { filterStatus, filterTopic, showArchived },
    refetchInterval: undefined, // Polling handled below
  });

  // Stats query
  const { data: stats, refetch: refetchStats } = useQuery({
    queryKey: ['admin', 'listening-stats'],
    queryFn: () => adminApi.getListeningStats().then(res => res.data?.data),
  });

  // Polling for PENDING parts — auto refetch every 3s when any part is pending
  useEffect(() => {
    const hasPending = content?.some(part => part.audioStatus === 'PENDING');
    if (!hasPending) return;

    const interval = setInterval(() => {
      refetchParts();
      refetchStats();
    }, 3000);

    return () => clearInterval(interval);
  }, [content, refetchParts, refetchStats]);

  const invalidateList = () => {
    queryClient.invalidateQueries({ queryKey: ['admin', 'listening-parts'] });
    refetchStats();
  };

  const openImport = () => {
    setImportForm(f => ({ ...f, content: '' }));
    setImportError(null);
    setCopied(null);
    setSourceTitle(null);
    setImportOpen(true);
  };

  // kind: 'instructions' (added once per part number as a NotebookLM source) or 'message' (the chat).
  const handleCopy = async (kind) => {
    setImportError(null);
    try {
      const res = await adminApi.getListeningImportPrompt(importForm.partNumber, importForm.topic);
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
      await adminApi.importListeningPart(importForm);
      setImportOpen(false);
      setSuccessMsg('Part imported; its audio is being generated. Preview it, then Publish to add it to the question bank.');
      invalidateList();
      setTimeout(() => setSuccessMsg(null), 5000);
    } catch (err) {
      setImportError(errorMessage(err, 'Failed to import part'));
    } finally {
      setImporting(false);
    }
  };

  const handlePublish = async (part) => {
    setStatusBusyId(part.partId);
    try {
      if (part.contentStatus !== 'HUMAN_REVIEWED') {
        await adminApi.updateContentStatus('LISTENING', part.partId, 'HUMAN_REVIEWED');
      }
      await adminApi.updateContentStatus('LISTENING', part.partId, 'PUBLISHED');
      setSuccessMsg('Part published to the question bank.');
      setTimeout(() => setSuccessMsg(null), 3000);
    } catch (err) {
      setError(errorMessage(err, 'Failed to publish'));
    } finally {
      setStatusBusyId(null);
      invalidateList();
    }
  };

  const handleUnpublish = async (part) => {
    setStatusBusyId(part.partId);
    try {
      await adminApi.updateContentStatus('LISTENING', part.partId, 'DRAFT');
      setSuccessMsg('Part withdrawn from the question bank.');
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
      await adminApi.restoreListeningPart(id);
      setSuccessMsg('Listening part restored.');
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
      await adminApi.deleteListeningPart(deleteId);
      setDeleteId(null);
      setSuccessMsg('Listening part archived successfully!');
      invalidateList();
      setTimeout(() => setSuccessMsg(null), 3000);
    } catch (err) {
      setError(errorMessage(err, 'Failed to archive listening part'));
    } finally {
      setDeleting(false);
    }
  };

  const handleRegenerate = async (id) => {
    try {
      setSuccessMsg('Audio generation queued...');
      await adminApi.regenerateListeningAudio(id);
      invalidateList();
      setTimeout(() => setSuccessMsg(null), 3000);
    } catch (err) {
      setError(errorMessage(err, 'Failed to regenerate audio'));
    }
  };

  const handleRetryFailed = async () => {
    try {
      setSuccessMsg('Retrying audio generation for failed parts...');
      await adminApi.retryFailedListeningAudio();
      invalidateList();
      setTimeout(() => setSuccessMsg(null), 3000);
    } catch (err) {
      setError(errorMessage(err, 'Failed to retry audio generation'));
    }
  };

  const playPreview = (part) => {
    const url = part.audioUrl;
    if (!url) return;
    setPreviewTitle(`Part ${part.partNumber}: ${part.title}`);
    setPreviewAudioUrl(url);
    if (audioRef.current) {
      audioRef.current.load();
      audioRef.current.play().catch(e => console.warn('Audio autoplay blocked', e));
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
          <h1>Listening Parts Management</h1>
          <p className="subtitle">{totalElements} {showArchived ? 'archived ' : ''}listening parts in the system</p>
        </div>
        <div style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap' }}>
          {stats?.statusCounts?.FAILED > 0 && (
            <button className="btn btn-outline" onClick={handleRetryFailed} id="retry-failed-btn" style={{ borderColor: 'var(--color-danger)', color: 'var(--color-danger)' }}>
              Retry Failed Audio ({stats.statusCounts.FAILED})
            </button>
          )}
          <button className="btn btn-outline" onClick={openImport} id="import-part-btn">
            Import from NotebookLM
          </button>
          <button className="btn btn-primary" onClick={() => navigate('/admin/listening/new')} id="create-part-btn">
            <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
            Add New Part
          </button>
        </div>
      </div>

      {successMsg && <div className="success-msg">{successMsg}</div>}
      {error && <div className="error-msg">{error}</div>}

      {/* Stats Summary Widgets */}
      {stats && (
        <div style={{ display: 'flex', gap: '1rem', marginBottom: '1.5rem', flexWrap: 'wrap' }}>
          <div style={{ background: 'var(--card-bg)', border: '1px solid var(--border-color)', borderRadius: '8px', padding: '0.75rem 1.25rem', display: 'flex', gap: '1rem', alignItems: 'center' }}>
            <span style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>Total Parts:</span>
            <strong style={{ fontSize: '1.1rem' }}>{stats.totalParts}</strong>
          </div>
          <div style={{ background: 'var(--card-bg)', border: '1px solid var(--border-color)', borderRadius: '8px', padding: '0.75rem 1.25rem', display: 'flex', gap: '1rem', alignItems: 'center' }}>
            <span style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>Ready:</span>
            <strong style={{ color: 'var(--color-success)', fontSize: '1.1rem' }}>{stats.statusCounts?.READY || 0}</strong>
          </div>
          <div style={{ background: 'var(--card-bg)', border: '1px solid var(--border-color)', borderRadius: '8px', padding: '0.75rem 1.25rem', display: 'flex', gap: '1rem', alignItems: 'center' }}>
            <span style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>Pending:</span>
            <strong style={{ color: 'var(--color-amber)', fontSize: '1.1rem' }}>{stats.statusCounts?.PENDING || 0}</strong>
          </div>
          <div style={{ background: 'var(--card-bg)', border: '1px solid var(--border-color)', borderRadius: '8px', padding: '0.75rem 1.25rem', display: 'flex', gap: '1rem', alignItems: 'center' }}>
            <span style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>Failed:</span>
            <strong style={{ color: 'var(--color-danger)', fontSize: '1.1rem' }}>{stats.statusCounts?.FAILED || 0}</strong>
          </div>
        </div>
      )}

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
          value={filterStatus}
          onChange={(e) => { setFilterStatus(e.target.value); resetPage(); }}
          className="matching-select"
          style={{ minWidth: '180px', width: 'auto' }}
        >
          <option value="">All Audio Status</option>
          <option value="READY">Ready</option>
          <option value="PENDING">Pending</option>
          <option value="FAILED">Failed</option>
        </select>
        <ArchivedToggle archived={showArchived} onChange={(v) => { setShowArchived(v); resetPage(); }} />
      </div>

      {/* Table */}
      <div className={`admin-table-section reveal reveal-delay-2${isFetching && isPlaceholderData ? ' is-fetching' : ''}`} style={{ marginTop: '1.5rem' }}>
        {isLoading ? (
          <div className="loading-spinner"><div className="spinner" /></div>
        ) : content.length === 0 ? (
          <div className="empty-state">
            <p>No {showArchived ? 'archived ' : ''}listening parts found.</p>
          </div>
        ) : (
          <>
            <div className="history-table-wrapper">
              <table className="history-table" id="admin-listening-table">
                <thead>
                  <tr>
                    <th>Part #</th>
                    <th>Title</th>
                    <th>Topic</th>
                    <th>Audio Status</th>
                    <th>Status</th>
                    <th>Duration</th>
                    <th>Questions</th>
                    <th>Created By</th>
                    <th>Action</th>
                  </tr>
                </thead>
                <tbody>
                  {content.map((part) => (
                    <tr key={part.partId}>
                      <td style={{ fontWeight: 600 }}>Part {part.partNumber}</td>
                      <td>{part.title}</td>
                      <td>{part.topic}</td>
                      <td>
                        {part.audioStatus === 'READY' && (
                          <span className="essay-type-badge badge-status-success">
                            Ready
                          </span>
                        )}
                        {part.audioStatus === 'PENDING' && (
                          <span className="essay-type-badge badge-status-warning" style={{ animation: 'pulse 1.5s infinite' }}>
                            Pending...
                          </span>
                        )}
                        {part.audioStatus === 'FAILED' && (
                          <span className="essay-type-badge badge-status-error">
                            Failed
                          </span>
                        )}
                      </td>
                      <td>
                        {STATUS_BADGES[part.contentStatus] ? (
                          <span className={`essay-type-badge ${STATUS_BADGES[part.contentStatus].className}`}>
                            {STATUS_BADGES[part.contentStatus].label}
                          </span>
                        ) : (part.contentStatus || '-')}
                      </td>
                      <td>{Math.floor(part.durationSeconds / 60)}:{(part.durationSeconds % 60).toString().padStart(2, '0')}</td>
                      <td>{part.questionCount} questions</td>
                      <td style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>{part.createdBy}</td>
                      <td>
                        <div className="admin-action-btns">
                          {showArchived ? (
                            <button
                              className="btn btn-sm btn-outline"
                              onClick={() => handleRestore(part.partId)}
                              id={`restore-part-${part.partId}`}
                            >Restore</button>
                          ) : (
                          <>
                          <button
                            className="btn btn-sm btn-outline"
                            onClick={() => navigate(`/listening/exam?parts=${part.partId}&preview=true&adminView=true`)}
                            id={`preview-part-${part.partId}`}
                          >Preview</button>
                          {part.audioStatus === 'READY' && (
                            <button
                              className="btn btn-sm btn-outline"
                              onClick={() => playPreview(part)}
                              style={{ display: 'flex', alignItems: 'center', gap: '4px' }}
                            >
                              <span aria-hidden="true" className="material-symbols-outlined" style={{ fontSize: 16 }}>play_arrow</span>
                              Play
                            </button>
                          )}
                          <button
                            className="btn btn-sm btn-outline"
                            onClick={() => navigate(`/admin/listening/edit/${part.partId}`)}
                          >Edit</button>
                          {part.contentStatus === 'PUBLISHED' ? (
                            <button
                              className="btn btn-sm btn-outline"
                              onClick={() => handleUnpublish(part)}
                              disabled={statusBusyId === part.partId}
                              id={`unpublish-part-${part.partId}`}
                            >Unpublish</button>
                          ) : (
                            <button
                              className="btn btn-sm btn-primary"
                              onClick={() => handlePublish(part)}
                              disabled={statusBusyId === part.partId}
                              id={`publish-part-${part.partId}`}
                            >Publish</button>
                          )}
                          <button
                            className="btn btn-sm btn-outline"
                            onClick={() => handleRegenerate(part.partId)}
                            title="Regenerate TTS Audio"
                          >Regen Audio</button>
                          <button
                            className="btn btn-sm admin-btn-danger"
                            onClick={() => setDeleteId(part.partId)}
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

      {/* Audio Player Sticky Footer */}
      {previewAudioUrl && (
        <div style={{
          position: 'fixed',
          bottom: 0,
          left: 0,
          right: 0,
          background: 'var(--card-bg)',
          borderTop: '2px solid var(--primary-color)',
          padding: '1rem 2rem',
          boxShadow: '0 -4px 20px rgba(0, 0, 0, 0.15)',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          zIndex: 1000,
          gap: '1rem',
          flexWrap: 'wrap'
        }}>
          <div>
            <h4 style={{ fontSize: '0.9rem', fontWeight: 600, color: 'var(--text-main)', marginBottom: '0.25rem' }}>Playing Preview</h4>
            <p style={{ fontSize: '0.8rem', color: 'var(--text-secondary)' }}>{previewTitle}</p>
          </div>
          <audio ref={audioRef} controls src={previewAudioUrl} style={{ flexGrow: 1, maxWidth: '600px' }} autoPlay>
            Your browser does not support the audio element.
          </audio>
          <button
            className="btn btn-sm btn-outline"
            onClick={() => { setPreviewAudioUrl(null); setPreviewTitle(''); }}
          >Close</button>
        </div>
      )}

      {/* NotebookLM Import Modal */}
      {importOpen && (
        <Modal ariaLabel="Import listening part" onClose={() => setImportOpen(false)} className="admin-modal admin-modal-wide" style={{ maxWidth: '760px', width: '90%', maxHeight: '90vh', overflowY: 'auto' }}>
          <button className="admin-modal-close" onClick={() => setImportOpen(false)} aria-label="Close" id="close-import-modal">
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
          </button>

          <h2 style={{ fontFamily: 'var(--font-heading)', fontSize: '1.25rem', fontWeight: 700, marginBottom: 12 }}>
            Import from NotebookLM
          </h2>
          <ol style={{ color: 'var(--text-secondary)', fontSize: '0.9rem', lineHeight: 1.6, margin: '0 0 1.25rem 1.25rem' }}>
            <li>Once per part number: copy the format instructions and add them to your NotebookLM notebook as a <em>Copied text</em> source.</li>
            <li>For each part: copy the chat message and send it in the notebook chat.</li>
            <li>Paste the JSON it returns below. The part is saved as Imported and its audio is generated; Publish it once you have checked it.</li>
          </ol>

          {importError && <div className="error-msg" style={{ marginBottom: '1rem' }}>{importError}</div>}

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '1rem', marginBottom: '1rem', alignItems: 'end' }}>
            <div className="admin-form-group">
              <label className="admin-form-label" htmlFor="import-part-number">Part</label>
              <select
                id="import-part-number"
                className="matching-select"
                value={importForm.partNumber}
                onChange={e => { setImportForm(f => ({ ...f, partNumber: Number(e.target.value) })); setCopied(null); setSourceTitle(null); }}
                style={{ width: '100%', maxWidth: '100%' }}
              >
                {[1, 2, 3, 4].map(n => <option key={n} value={n}>Part {n}</option>)}
              </select>
            </div>
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
              placeholder='{ "partNumber": 1, "title": "...", "script": "...", "questions": [ ... ] }'
              style={{ minHeight: 240, width: '100%', fontFamily: 'monospace', fontSize: '0.85rem' }}
            />
          </div>

          <div className="admin-form-actions">
            <button className="btn btn-outline" onClick={() => setImportOpen(false)} type="button">Cancel</button>
            <button className="btn btn-primary" onClick={handleImport} disabled={importing} type="button" id="submit-import">
              {importing && <span className="spinner" />}
              Import Part
            </button>
          </div>
        </Modal>
      )}

      {/* Delete Confirm Modal */}
      {deleteId && (
        <Modal ariaLabel="Delete listening part" onClose={() => setDeleteId(null)} className="admin-modal" style={{ maxWidth: 420 }}>
          <h2 style={{ fontFamily: 'var(--font-heading)', fontSize: '1.1rem', fontWeight: 700, marginBottom: 12 }}>
            Confirm Archive
          </h2>
          <p style={{ color: 'var(--text-secondary)', fontSize: '0.9rem', marginBottom: 24, lineHeight: 1.6 }}>
            Archive this listening part? It will disappear from active lists, while its questions, mock-test links, test history, and MP3 audio remain available for restoration.
          </p>
          <div className="admin-form-actions">
            <button className="btn btn-outline" onClick={() => setDeleteId(null)}>Cancel</button>
            <button className="btn admin-btn-danger-fill" onClick={handleDelete} disabled={deleting}>
              {deleting && <span className="spinner" />}
              Archive Part
            </button>
          </div>
        </Modal>
      )}
    </div>
  );
}
