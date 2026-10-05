import { useState, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import adminApi from '../api/adminApi';
import { useAuth } from '../context/AuthContext';
import { useConfirm } from '../context/ConfirmContext';
import { useToast } from '../context/ToastContext';
import { usePaginatedQuery } from '../hooks/usePaginatedQuery';
import Pagination from '../components/Pagination';
import Modal from '../components/common/Modal';

export default function AdminUsersPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const confirm = useConfirm();
  const { success: showSuccess, error: showError } = useToast();
  const { user: me } = useAuth();
  // Students by default: admin accounts are not learners and used to be listed among them.
  const [roleFilter, setRoleFilter] = useState('STUDENT');
  const [updating, setUpdating] = useState(false);
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
    queryFn: (pg, sz) => adminApi.listUsers(debouncedSearch || null, pg, sz, undefined, roleFilter || null),
    filters: { search: debouncedSearch, role: roleFilter },
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

  const changeRoleFilter = (role) => {
    setRoleFilter(role);
    setPage(0);
  };

  // Role change or suspension, confirmed first; the list refreshes so its badges follow.
  const updateAccount = async (body, prompt) => {
    if (!(await confirm(prompt))) return;
    setUpdating(true);
    try {
      const res = await adminApi.updateUser(detail.userId, body);
      const updated = res.data?.data;
      setDetail((d) => ({ ...d, role: updated?.role ?? d.role, suspended: updated?.suspended ?? d.suspended }));
      queryClient.invalidateQueries({ queryKey: ['admin', 'users'] });
      showSuccess('Account updated');
    } catch (err) {
      showError(err.response?.data?.message || err.message || 'Could not update the account');
    } finally {
      setUpdating(false);
    }
  };

  const listNoun = roleFilter === 'STUDENT' ? 'students' : roleFilter === 'ADMIN' ? 'admins' : 'accounts';

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
          <p className="subtitle">{totalElements} {listNoun}</p>
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

      <div className="writing-filter reveal reveal-delay-1">
        <div className="archived-toggle" role="group" aria-label="Show students, admins or all accounts" id="admin-role-filter">
          {[['STUDENT', 'Students'], ['ADMIN', 'Admins'], ['', 'All']].map(([value, label]) => (
            <button
              key={label}
              type="button"
              className={`filter-btn ${roleFilter === value ? 'active' : ''}`}
              aria-pressed={roleFilter === value}
              onClick={() => changeRoleFilter(value)}
            >{label}</button>
          ))}
        </div>
      </div>

      {/* Users Table */}
      <div className={`admin-table-section reveal reveal-delay-2${isFetching && isPlaceholderData ? ' is-fetching' : ''}`}>
        {isLoading ? (
          <div className="loading-spinner"><div className="spinner" /></div>
        ) : content.length === 0 ? (
          <div className="empty-state">
            <p>No {listNoun} found{search ? ` with keyword "${search}"` : ''}.</p>
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
                    <th>Status</th>
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
                        <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                          {u.role === 'ADMIN' && <span className="essay-type-badge badge-status-info">Admin</span>}
                          {u.suspended
                            ? <span className="essay-type-badge badge-status-error">Suspended</span>
                            : <span className="essay-type-badge badge-status-success">Active</span>}
                        </div>
                      </td>
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
        <Modal ariaLabel="Student details" onClose={closeDetail} className="admin-modal">
          <button className="admin-modal-close" onClick={closeDetail} aria-label="Close" id="close-user-detail">
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
                    {detail.suspended && <span className="admin-role-badge" style={{ background: 'rgba(186,26,26,0.1)', color: 'var(--error)' }}>SUSPENDED</span>}
                  </p>
                </div>
              </div>

              {/* Account actions. The server refuses them on your own account too. */}
              <div className="admin-detail-section">
                <h3>Account</h3>
                {detail.userId === me?.userId ? (
                  <p className="admin-detail-meta">This is your own account; another admin has to change its role or suspend it.</p>
                ) : (
                  <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
                    <button
                      type="button"
                      className="btn btn-sm btn-outline"
                      disabled={updating}
                      onClick={() => updateAccount(
                        { role: detail.role === 'ADMIN' ? 'STUDENT' : 'ADMIN' },
                        detail.role === 'ADMIN'
                          ? { title: 'Make this account a student?', message: 'It loses access to the admin pages at its next request.', confirmLabel: 'Make student' }
                          : { title: 'Make this account an admin?', message: 'It gets full access to the admin pages, including other accounts.', confirmLabel: 'Make admin' },
                      )}
                    >
                      {detail.role === 'ADMIN' ? 'Make student' : 'Make admin'}
                    </button>
                    {detail.suspended ? (
                      <button
                        type="button"
                        className="btn btn-sm btn-outline"
                        disabled={updating}
                        onClick={() => updateAccount(
                          { suspended: false },
                          { title: 'Reactivate this account?', message: 'The user can log in again.', confirmLabel: 'Reactivate' },
                        )}
                      >Reactivate</button>
                    ) : (
                      <button
                        type="button"
                        className="btn btn-sm admin-btn-danger"
                        disabled={updating}
                        onClick={() => updateAccount(
                          { suspended: true },
                          { title: 'Suspend this account?', message: 'The user is signed out at their next request and cannot log in until reactivated. Their results are kept.', confirmLabel: 'Suspend', tone: 'danger' },
                        )}
                      >Suspend</button>
                    )}
                  </div>
                )}
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
        </Modal>
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
