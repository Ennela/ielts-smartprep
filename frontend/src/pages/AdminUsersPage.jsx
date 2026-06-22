import { useState, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import adminApi from '../api/adminApi';
import { usePaginatedQuery } from '../hooks/usePaginatedQuery';
import Pagination from '../components/Pagination';

export default function AdminUsersPage() {
  const navigate = useNavigate();
  const [search, setSearch] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [detail, setDetail] = useState(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [error, setError] = useState(null);
  const debounceRef = useRef(null);

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
    queryKey: ['admin', 'users'],
    queryFn: (pg, sz) => adminApi.listUsers(debouncedSearch || null, pg, sz),
    filters: { search: debouncedSearch },
  });

  // Debounced search
  const handleSearch = (val) => {
    setSearch(val);
    clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => {
      setDebouncedSearch(val);
      setPage(0);
    }, 400);
  };

  const openDetail = (userId) => {
    setDetailLoading(true);
    setDetail(null);
    adminApi.getUserDetail(userId)
      .then(res => setDetail(res.data?.data))
      .catch(err => setError(err.message))
      .finally(() => setDetailLoading(false));
  };

  const closeDetail = () => setDetail(null);

  return (
    <div className="admin-dashboard-content">
      {/* Header */}
      <div className="admin-dash-header reveal">
        <div>
          <button className="btn-back" onClick={() => navigate('/admin')} id="back-to-admin">
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2"><path d="M19 12H5M12 19l-7-7 7-7"/></svg>
            Overview
          </button>
          <h1>Student Management</h1>
          <p className="subtitle">Total of {totalElements} registered students</p>
        </div>
      </div>

      {error && <div className="error-msg">{error}</div>}

      {/* Search */}
      <div className="admin-search-bar reveal reveal-delay-1">
        <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
          <circle cx="11" cy="11" r="8" /><path d="m21 21-4.3-4.3" />
        </svg>
        <input
          type="text"
          placeholder="Search by name or email..."
          value={search}
          onChange={(e) => handleSearch(e.target.value)}
          id="admin-user-search"
        />
      </div>

      {/* Users Table */}
      <div className={`admin-table-section reveal reveal-delay-2${isFetching && isPlaceholderData ? ' is-fetching' : ''}`}>
        {isLoading ? (
          <div className="loading-spinner"><div className="spinner" /></div>
        ) : content.length === 0 ? (
          <div className="empty-state">
            <p>No students found{search ? ` with keyword "${search}"` : ''}.</p>
          </div>
        ) : (
          <>
            <div className="history-table-wrapper">
              <table className="history-table" id="admin-users-table">
                <thead>
                  <tr>
                    <th>#</th>
                    <th>Student</th>
                    <th>Email</th>
                    <th>Tests Taken</th>
                    <th>Avg Score</th>
                    <th>Join Date</th>
                    <th>Action</th>
                  </tr>
                </thead>
                <tbody>
                  {content.map((u, idx) => (
                    <tr key={u.userId}>
                      <td>{page * size + idx + 1}</td>
                      <td>
                        <div className="admin-user-cell">
                          <div className="admin-user-avatar">
                            {(u.displayName || u.username || 'U').charAt(0).toUpperCase()}
                          </div>
                          <div>
                            <span className="admin-user-name">{u.displayName || u.username}</span>
                            <span className="admin-user-username">@{u.username}</span>
                          </div>
                        </div>
                      </td>
                      <td className="admin-email-cell">{u.email}</td>
                      <td><span className="admin-tests-badge">{u.totalTests}</span></td>
                      <td>
                        <span className={`band-score band-${getBandClass(u.avgScore)}`}>
                          {u.avgScore != null ? Number(u.avgScore).toFixed(1) : '—'}
                        </span>
                      </td>
                      <td className="ht-date">{formatDate(u.createdAt)}</td>
                      <td>
                        <button
                          className="btn btn-sm btn-outline"
                          onClick={() => openDetail(u.userId)}
                          id={`view-user-${u.userId}`}
                        >
                          Details
                        </button>
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

      {/* Detail Modal */}
      {(detail || detailLoading) && (
        <div className="admin-modal-overlay" onClick={closeDetail}>
          <div className="admin-modal" onClick={(e) => e.stopPropagation()}>
            <button className="admin-modal-close" onClick={closeDetail} id="close-user-detail">
              <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
            </button>

            {detailLoading ? (
              <div className="loading-spinner"><div className="spinner" /></div>
            ) : detail && (
              <>
                <div className="admin-modal-header">
                  <div className="admin-detail-avatar">
                    {(detail.displayName || detail.username || 'U').charAt(0).toUpperCase()}
                  </div>
                  <div>
                    <h2>{detail.displayName || detail.username}</h2>
                    <p className="admin-detail-meta">@{detail.username} · {detail.email}</p>
                    <p className="admin-detail-meta">
                      Joined: {formatDate(detail.createdAt)}
                      {detail.role === 'ADMIN' && <span className="admin-role-badge">ADMIN</span>}
                    </p>
                  </div>
                </div>

                {/* Targets */}
                <div className="admin-detail-targets">
                  <h3>Target Band Scores</h3>
                  <div className="admin-target-row">
                    <span>Reading: <strong>{detail.targetReadingScore ?? '—'}</strong></span>
                    <span>Writing: <strong>{detail.targetWritingScore ?? '—'}</strong></span>
                    <span>Listening: <strong>{detail.targetListeningScore ?? '—'}</strong></span>
                  </div>
                </div>

                {/* Skill Stats */}
                {detail.skillStats?.length > 0 && (
                  <div className="admin-detail-section">
                    <h3>Skill Statistics</h3>
                    <div className="admin-skill-stats">
                      {detail.skillStats.map(s => (
                        <div key={s.skill} className="admin-skill-stat-card">
                          <span className={`ht-skill badge-${s.skill?.toLowerCase()}`}>{s.skill}</span>
                          <div className="admin-skill-stat-numbers">
                            <div>
                              <span className="stat-value">{s.totalTests}</span>
                              <span className="stat-label">Tests</span>
                            </div>
                            <div>
                              <span className="stat-value">{s.avgScore != null ? Number(s.avgScore).toFixed(1) : '—'}</span>
                              <span className="stat-label">Avg Score</span>
                            </div>
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {/* Recent Scores */}
                {detail.recentScores?.length > 0 && (
                  <div className="admin-detail-section">
                    <h3>Recent History</h3>
                    <div className="admin-recent-scores">
                      {detail.recentScores.map((s, i) => (
                        <div key={i} className="admin-recent-row">
                          <span className={`ht-skill badge-${s.skillType?.toLowerCase()}`}>{s.skillType}</span>
                          <span className={`band-score band-${getBandClass(s.score)}`}>
                            {Number(s.score).toFixed(1)}
                          </span>
                          <span className="ht-date">{formatDate(s.recordedAt)}</span>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

function getBandClass(score) {
  const s = parseFloat(score);
  if (isNaN(s)) return 'mid';
  if (s >= 7.0) return 'high';
  if (s >= 5.5) return 'mid';
  return 'low';
}

function formatDate(dateStr) {
  if (!dateStr) return '—';
  const d = new Date(dateStr);
  return d.toLocaleDateString('en-US', { day: '2-digit', month: 'short', year: 'numeric' });
}
