import { useState } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';

const MENU_ITEMS = [
  { path: '/dashboard', label: 'Dashboard',  icon: 'dashboard',  end: true },
  { path: '/mock-tests', label: 'Mock Test',  icon: 'quiz',       end: false },
  { path: '/reading',   label: 'Reading',    icon: 'menu_book',  end: false },
  { path: '/writing',   label: 'Writing',    icon: 'edit_note',  end: false },
  { path: '/listening', label: 'Listening',  icon: 'headphones', end: false },
];

const ADMIN_MENU_ITEMS = [
  { path: '/admin',                  label: 'Overview', icon: 'bar_chart',    end: true },
  { path: '/admin/users',            label: 'Students',  icon: 'group',        end: false },
  { path: '/admin/mock-tests',       label: 'Mock Tests', icon: 'quiz',         end: false },
  { path: '/admin/writing-prompts',  label: 'Writing Prompts',   icon: 'description',  end: false },
  { path: '/admin/reading-quizzes',  label: 'Reading Quizzes',   icon: 'menu_book',    end: false },
];

export default function MainLayout() {
  const { user, logout, isAdmin } = useAuth();
  const navigate = useNavigate();
  const [sidebarOpen, setSidebarOpen] = useState(false);

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const initials = (user?.displayName || user?.username || 'U').charAt(0).toUpperCase();
  const displayName = user?.displayName || user?.username || '';

  return (
    <div className="app-layout">
      {sidebarOpen && (
        <div className="sidebar-overlay" onClick={() => setSidebarOpen(false)} />
      )}

      {/* ── Sidebar ── */}
      <aside className={`sidebar ${sidebarOpen ? 'sidebar-open' : ''}`}>

        {/* Logo */}
        <div className="sidebar-brand">
          <span className="sidebar-logo-fallback">SP</span>
          <span className="sidebar-brand-text">SmartPrep</span>
        </div>

        {/* User Profile Card */}
        <div
          className="sidebar-user-card"
          onClick={() => { navigate('/profile'); setSidebarOpen(false); }}
        >
          <div className="sidebar-avatar">{initials}</div>
          <div className="sidebar-user-info">
            <span className="sidebar-user-name">{displayName}</span>
            <span className="sidebar-user-role">
              {isAdmin ? 'Admin' : `Band Target: ${(() => {
                if (!user) return '6.5';
                const r = parseFloat(user.targetReadingScore) || 6.5;
                const w = parseFloat(user.targetWritingScore) || 6.5;
                const l = parseFloat(user.targetListeningScore) || 6.5;
                const avg = (r + w + l) / 3;
                const base = Math.floor(avg);
                const fractional = avg - base;
                if (fractional < 0.25) return base.toFixed(1);
                if (fractional < 0.75) return (base + 0.5).toFixed(1);
                return (base + 1.0).toFixed(1);
              })()}`}
            </span>
          </div>
        </div>

        {/* Main Navigation */}
        <nav className="sidebar-nav">
          {MENU_ITEMS.map((item) => (
            <NavLink
              key={item.path}
              to={item.path}
              end={item.end}
              className={({ isActive }) => `sidebar-item ${isActive ? 'active' : ''}`}
              onClick={() => setSidebarOpen(false)}
              id={`nav-${item.label.toLowerCase().replace(/\s/g, '-')}`}
            >
              <span className="sidebar-icon">
                <span className="material-symbols-outlined">{item.icon}</span>
              </span>
              <span className="sidebar-label">{item.label}</span>
            </NavLink>
          ))}

          {isAdmin && (
            <>
              <div className="sidebar-divider" />
              <span className="sidebar-section-label">Administration</span>
              {ADMIN_MENU_ITEMS.map((item) => (
                <NavLink
                  key={item.path}
                  to={item.path}
                  end={item.end}
                  className={({ isActive }) => `sidebar-item ${isActive ? 'active' : ''}`}
                  onClick={() => setSidebarOpen(false)}
                  id={`nav-admin-${item.label.toLowerCase().replace(/\s/g, '-')}`}
                >
                  <span className="sidebar-icon">
                    <span className="material-symbols-outlined">{item.icon}</span>
                  </span>
                  <span className="sidebar-label">{item.label}</span>
                </NavLink>
              ))}
            </>
          )}
        </nav>

        {/* Footer: CTA + links */}
        <div className="sidebar-footer">
          <button className="sidebar-cta" onClick={() => navigate('/mock-tests')}>
            Start Mock Test
          </button>
          <div className="sidebar-footer-links">
            <button className="sidebar-footer-link" onClick={() => navigate('/profile')}>
              <span className="material-symbols-outlined" style={{ fontSize: 20 }}>settings</span>
              <span>Settings</span>
            </button>
            <button className="sidebar-footer-link sidebar-logout" onClick={handleLogout} id="sidebar-logout-btn">
              <span className="material-symbols-outlined" style={{ fontSize: 20 }}>logout</span>
              <span>Logout</span>
            </button>
          </div>
        </div>
      </aside>

      {/* ── Main Area ── */}
      <div className="main-area">
        <header className="topbar">
          <button
            className="hamburger"
            onClick={() => setSidebarOpen(!sidebarOpen)}
            id="hamburger-btn"
          >
            <span className="material-symbols-outlined">menu</span>
          </button>

          <div className="topbar-brand">
            <span className="topbar-brand-text" onClick={() => navigate('/dashboard')}>
              SmartPrep
            </span>
          </div>

          {/* Desktop nav links */}
          <nav className="topbar-nav" style={{ display: 'none' }}>
            <a href="#" className="topbar-nav-link active">Practice</a>
            <a href="#" className="topbar-nav-link">Exams</a>
            <a href="#" className="topbar-nav-link">Resources</a>
          </nav>

          <div className="topbar-right">
            <button className="topbar-upgrade" style={{ display: 'none' }}>Upgrade Pro</button>
            <button className="topbar-icon-btn">
              <span className="material-symbols-outlined">notifications</span>
            </button>
            <span className="topbar-greeting">{displayName}</span>
            <div
              className="topbar-avatar"
              onClick={() => navigate('/profile')}
              id="topbar-profile-btn"
            >
              {initials}
            </div>
          </div>
        </header>

        <main className="page-content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
