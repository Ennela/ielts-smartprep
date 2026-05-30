import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import adminApi from '../api/adminApi';

const STAT_CARDS = [
  {
    key: 'totalUsers',
    label: 'Tổng học viên',
    icon: (
      <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" /><circle cx="9" cy="7" r="4" /><path d="M22 21v-2a4 4 0 0 0-3-3.87" /><path d="M16 3.13a4 4 0 0 1 0 7.75" />
      </svg>
    ),
    color: 'var(--color-primary)',
    bg: 'rgba(15,118,110,0.08)',
    format: (v) => v?.toLocaleString?.() ?? '—',
  },
  {
    key: 'testsToday',
    label: 'Lượt làm bài hôm nay',
    icon: (
      <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <path d="M12 20V10" /><path d="M18 20V4" /><path d="M6 20v-4" />
      </svg>
    ),
    color: 'var(--color-blue)',
    bg: 'rgba(37,99,235,0.08)',
    format: (v) => v?.toLocaleString?.() ?? '—',
  },
  {
    key: 'pendingWritings',
    label: 'Bài viết chờ chấm',
    icon: (
      <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <path d="M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z" /><path d="m15 5 4 4" />
      </svg>
    ),
    color: 'var(--color-amber)',
    bg: 'rgba(245,158,11,0.08)',
    format: (v) => v?.toLocaleString?.() ?? '—',
  },
  {
    key: 'apiHealthy',
    label: 'Sức khỏe API',
    icon: (
      <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <path d="M22 12h-4l-3 9L9 3l-3 9H2" />
      </svg>
    ),
    color: 'var(--color-success)',
    bg: 'rgba(16,185,129,0.08)',
    format: (v) => v ? 'Hoạt động tốt' : 'Có sự cố',
    statusClass: (v) => v ? 'admin-health-ok' : 'admin-health-err',
  },
];

export default function AdminDashboardPage() {
  const navigate = useNavigate();
  const [stats, setStats] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    adminApi.getDashboard()
      .then(res => setStats(res.data?.data))
      .catch(err => setError(err.message))
      .finally(() => setLoading(false));
  }, []);

  const today = new Date().toLocaleDateString('vi-VN', {
    weekday: 'long', year: 'numeric', month: 'long', day: 'numeric',
  });

  if (loading) {
    return (
      <div className="admin-dashboard-content">
        <div className="loading-spinner">
          <div className="spinner" />
        </div>
      </div>
    );
  }

  return (
    <div className="admin-dashboard-content">
      {/* Header */}
      <div className="admin-dash-header reveal">
        <div>
          <h1>Bảng điều khiển quản trị</h1>
          <p className="subtitle">Tổng quan hoạt động hệ thống IELTS SmartPrep</p>
        </div>
        <div className="dash-date-badge">
          <span>{today}</span>
        </div>
      </div>

      {/* Stat Cards */}
      {error && <div className="error-msg">{error}</div>}

      <div className="admin-stat-grid reveal reveal-delay-1">
        {STAT_CARDS.map((card) => {
          const value = stats?.[card.key];
          return (
            <div className="admin-stat-card" key={card.key}>
              <div className="admin-stat-icon" style={{ background: card.bg, color: card.color }}>
                {card.icon}
              </div>
              <div className="admin-stat-body">
                <span className="admin-stat-label">{card.label}</span>
                <span
                  className={`admin-stat-value ${card.statusClass ? card.statusClass(value) : ''}`}
                  style={!card.statusClass ? { color: card.color } : undefined}
                >
                  {card.format(value)}
                </span>
              </div>
            </div>
          );
        })}
      </div>

      {/* Quick Nav Cards */}
      <div className="admin-quick-nav reveal reveal-delay-2">
        <h2>Quản lý nhanh</h2>
        <div className="admin-quick-grid">
          <div className="card card-clickable" onClick={() => navigate('/admin/users')} id="admin-nav-users">
            <div className="admin-quick-icon" style={{ background: 'rgba(15,118,110,0.08)', color: 'var(--color-primary)' }}>
              <svg viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" /><circle cx="9" cy="7" r="4" /><path d="M22 21v-2a4 4 0 0 0-3-3.87" /><path d="M16 3.13a4 4 0 0 1 0 7.75" />
              </svg>
            </div>
            <h3>Quản lý học viên</h3>
            <p>Xem danh sách, tìm kiếm và xem chi tiết tiến trình từng học viên</p>
            <span className="card-action">Xem danh sách →</span>
          </div>

          <div className="card card-clickable" onClick={() => navigate('/admin/writing-prompts')} id="admin-nav-prompts">
            <div className="admin-quick-icon" style={{ background: 'rgba(37,99,235,0.08)', color: 'var(--color-blue)' }}>
              <svg viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M14.5 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7.5L14.5 2z" /><polyline points="14 2 14 8 20 8" /><line x1="16" y1="13" x2="8" y2="13" /><line x1="16" y1="17" x2="8" y2="17" />
              </svg>
            </div>
            <h3>Quản lý đề viết</h3>
            <p>Thêm, sửa, xóa đề viết Task 1 và Task 2 cho học viên luyện tập</p>
            <span className="card-action">Quản lý đề →</span>
          </div>
        </div>
      </div>
    </div>
  );
}
