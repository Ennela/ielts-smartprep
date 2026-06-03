import { useEffect, useState, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import adminApi from '../api/adminApi';

const TOPICS = [
  { value: 'ACCOMMODATION', label: 'Accommodation / Booking' },
  { value: 'EDUCATION', label: 'Campus / Education' },
  { value: 'CULTURE', label: 'Culture / Museum' },
  { value: 'SCIENCE', label: 'Science / Technology' },
  { value: 'ENVIRONMENT', label: 'Environment / Nature' },
];

const STATUS_LABELS = {
  READY: 'Ready',
  PENDING: 'Pending',
  FAILED: 'Failed',
};

export default function AdminListeningListPage() {
  const navigate = useNavigate();
  const [partsData, setPartsData] = useState(null);
  const [filterTopic, setFilterTopic] = useState('');
  const [filterStatus, setFilterStatus] = useState('');
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [successMsg, setSuccessMsg] = useState(null);

  // Audio Preview state
  const [previewAudioUrl, setPreviewAudioUrl] = useState(null);
  const [previewTitle, setPreviewTitle] = useState('');
  const audioRef = useRef(null);

  // Delete state
  const [deleteId, setDeleteId] = useState(null);
  const [deleting, setDeleting] = useState(false);

  // Stats state
  const [stats, setStats] = useState(null);

  const fetchParts = (status, topic, pageVal, showLoading = true) => {
    if (showLoading) setLoading(true);
    adminApi.listListeningParts(status || null, topic || null, pageVal, 10)
      .then(res => {
        setPartsData(res.data?.data);
        setError(null);
      })
      .catch(err => setError(err.message))
      .finally(() => {
        if (showLoading) setLoading(false);
      });
  };

  const fetchStats = () => {
    adminApi.getListeningStats()
      .then(res => setStats(res.data?.data))
      .catch(err => console.error('Failed to fetch stats', err));
  };

  useEffect(() => {
    fetchParts(filterStatus, filterTopic, page, true);
    fetchStats();
  }, [filterStatus, filterTopic, page]);

  // Polling for PENDING parts
  useEffect(() => {
    const hasPending = partsData?.content?.some(part => part.audioStatus === 'PENDING');
    if (!hasPending) return;

    const interval = setInterval(() => {
      // Fetch silently (without spinner) to update statuses
      fetchParts(filterStatus, filterTopic, page, false);
      fetchStats();
    }, 3000);

    return () => clearInterval(interval);
  }, [partsData, filterStatus, filterTopic, page]);

  const handleDelete = async () => {
    if (!deleteId) return;
    setDeleting(true);
    try {
      await adminApi.deleteListeningPart(deleteId);
      setDeleteId(null);
      setSuccessMsg('Listening part deleted successfully!');
      fetchParts(filterStatus, filterTopic, page, true);
      fetchStats();
      setTimeout(() => setSuccessMsg(null), 3000);
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Failed to delete listening part');
    } finally {
      setDeleting(false);
    }
  };

  const handleRegenerate = async (id) => {
    try {
      setSuccessMsg('Audio generation queued...');
      await adminApi.regenerateListeningAudio(id);
      fetchParts(filterStatus, filterTopic, page, false);
      setTimeout(() => setSuccessMsg(null), 3000);
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Failed to regenerate audio');
    }
  };

  const handleRetryFailed = async () => {
    try {
      setSuccessMsg('Retrying audio generation for failed parts...');
      await adminApi.retryFailedListeningAudio();
      fetchParts(filterStatus, filterTopic, page, false);
      setTimeout(() => setSuccessMsg(null), 3000);
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Failed to retry audio generation');
    }
  };

  const playPreview = (part) => {
    const url = part.audioUrl;
    if (!url) return;
    setPreviewTitle(`Part ${part.partNumber}: ${part.title}`);
    setPreviewAudioUrl(url);
    if (audioRef.current) {
      audioRef.current.load();
      audioRef.current.play().catch(e => console.log('Audio autoplay blocked', e));
    }
  };

  const content = partsData?.content || [];
  const totalPages = partsData?.totalPages || 0;
  const totalElements = partsData?.totalElements || 0;

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
          <p className="subtitle">{totalElements} listening parts in the system</p>
        </div>
        <div style={{ display: 'flex', gap: '0.75rem' }}>
          {stats?.statusCounts?.FAILED > 0 && (
            <button className="btn btn-outline" onClick={handleRetryFailed} id="retry-failed-btn" style={{ borderColor: 'var(--color-danger)', color: 'var(--color-danger)' }}>
              Retry Failed Audio ({stats.statusCounts.FAILED})
            </button>
          )}
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
          onChange={(e) => { setFilterTopic(e.target.value); setPage(0); }}
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
          onChange={(e) => { setFilterStatus(e.target.value); setPage(0); }}
          className="matching-select"
          style={{ minWidth: '180px', width: 'auto' }}
        >
          <option value="">All Audio Status</option>
          <option value="READY">Ready</option>
          <option value="PENDING">Pending</option>
          <option value="FAILED">Failed</option>
        </select>
      </div>

      {/* Table */}
      <div className="admin-table-section reveal reveal-delay-2" style={{ marginTop: '1.5rem' }}>
        {loading ? (
          <div className="loading-spinner"><div className="spinner" /></div>
        ) : content.length === 0 ? (
          <div className="empty-state">
            <p>No listening parts found.</p>
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
                          <span className="essay-type-badge badge-opinion" style={{ backgroundColor: 'rgba(16, 185, 129, 0.1)', color: '#10b981', border: '1px solid rgba(16, 185, 129, 0.2)' }}>
                            Ready
                          </span>
                        )}
                        {part.audioStatus === 'PENDING' && (
                          <span className="essay-type-badge badge-opinion" style={{ backgroundColor: 'rgba(245, 158, 11, 0.1)', color: '#f59e0b', border: '1px solid rgba(245, 158, 11, 0.2)', animation: 'pulse 1.5s infinite' }}>
                            Pending...
                          </span>
                        )}
                        {part.audioStatus === 'FAILED' && (
                          <span className="essay-type-badge badge-opinion" style={{ backgroundColor: 'rgba(239, 68, 68, 0.1)', color: '#ef4444', border: '1px solid rgba(239, 68, 68, 0.2)' }}>
                            Failed
                          </span>
                        )}
                      </td>
                      <td>{Math.floor(part.durationSeconds / 60)}:{(part.durationSeconds % 60).toString().padStart(2, '0')}</td>
                      <td>{part.questionCount} questions</td>
                      <td style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>{part.createdBy}</td>
                      <td>
                        <div className="admin-action-btns">
                          {part.audioStatus === 'READY' && (
                            <button
                              className="btn btn-sm btn-outline"
                              onClick={() => playPreview(part)}
                              style={{ display: 'flex', alignItems: 'center', gap: '4px' }}
                            >
                              <span className="material-symbols-outlined" style={{ fontSize: 16 }}>play_arrow</span>
                              Play
                            </button>
                          )}
                          <button
                            className="btn btn-sm btn-outline"
                            onClick={() => navigate(`/admin/listening/edit/${part.partId}`)}
                          >Edit</button>
                          <button
                            className="btn btn-sm btn-outline"
                            onClick={() => handleRegenerate(part.partId)}
                            title="Regenerate TTS Audio"
                          >Regen Audio</button>
                          <button
                            className="btn btn-sm admin-btn-danger"
                            onClick={() => setDeleteId(part.partId)}
                          >Delete</button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {totalPages > 1 && (
              <div className="ht-pagination">
                <button className="btn btn-sm btn-outline" disabled={page === 0} onClick={() => setPage(p => Math.max(0, p - 1))}>Prev</button>
                <span className="ht-page-info">Page {page + 1} / {totalPages}</span>
                <button className="btn btn-sm btn-outline" disabled={page >= totalPages - 1} onClick={() => setPage(p => p + 1)}>Next</button>
              </div>
            )}
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

      {/* Delete Confirm Modal */}
      {deleteId && (
        <div className="admin-modal-overlay" onClick={() => setDeleteId(null)}>
          <div className="admin-modal" onClick={(e) => e.stopPropagation()} style={{ maxWidth: 420 }}>
            <h2 style={{ fontFamily: 'var(--font-heading)', fontSize: '1.1rem', fontWeight: 700, marginBottom: 12 }}>
              Confirm Delete
            </h2>
            <p style={{ color: 'var(--text-secondary)', fontSize: '0.9rem', marginBottom: 24, lineHeight: 1.6 }}>
              Are you sure you want to delete this listening part? This will delete all associated questions, option choices, and the MP3 audio file. This action cannot be undone.
            </p>
            <div className="admin-form-actions">
              <button className="btn btn-outline" onClick={() => setDeleteId(null)}>Cancel</button>
              <button className="btn admin-btn-danger-fill" onClick={handleDelete} disabled={deleting}>
                {deleting && <span className="spinner" />}
                Delete Part
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
