import { useState } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import styles from './MainLayout.module.css';

const ADMIN_MENU_ITEMS = [
  { path: '/admin',                  label: 'Dashboard', icon: 'dashboard',    end: true },
  { path: '/admin/users',            label: 'Students',  icon: 'group',        end: false },
  { path: '/admin/mock-tests',       label: 'Mock Tests', icon: 'quiz',         end: false },
  { path: '/admin/writing-prompts',  label: 'Writing Prompts',   icon: 'description',  end: false },
  { path: '/admin/reading-quizzes',  label: 'Reading Quizzes',   icon: 'menu_book',    end: false },
  { path: '/admin/listening',        label: 'Listening Parts',   icon: 'headphones',   end: false },
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
    <div className={styles['app-layout']}>
      {sidebarOpen && (
        <div className={styles['sidebar-overlay']} onClick={() => setSidebarOpen(false)} />
      )}

      {/* ── Sidebar ── */}
      <aside className={`${styles.sidebar} ${sidebarOpen ? styles['sidebar-open'] : ''}`}>

        {/* Logo */}
        <div className={styles['sidebar-brand']}>
          <span className={styles['sidebar-logo-fallback']}>SP</span>
          <span className={styles['sidebar-brand-text']}>SmartPrep</span>
        </div>

        {/* User Profile Card */}
        <div
          className={styles['sidebar-user-card']}
          onClick={() => { navigate('/profile'); setSidebarOpen(false); }}
        >
          <div className={styles['sidebar-avatar']}>{initials}</div>
          <div className={styles['sidebar-user-info']}>
            <span className={styles['sidebar-user-name']}>{displayName}</span>
            <span className={styles['sidebar-user-role']}>
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
        <nav className={styles['sidebar-nav']}>
          {ADMIN_MENU_ITEMS.map((item) => (
            <NavLink
              key={item.path}
              to={item.path}
              end={item.end}
              className={({ isActive }) => `${styles['sidebar-item']} ${isActive ? styles.active : ''}`}
              onClick={() => setSidebarOpen(false)}
              id={`nav-admin-${item.label.toLowerCase().replace(/\s/g, '-')}`}
            >
              <span className={styles['sidebar-icon']}>
                <span className="material-symbols-outlined">{item.icon}</span>
              </span>
              <span className={styles['sidebar-label']}>{item.label}</span>
            </NavLink>
          ))}
        </nav>

        {/* Footer: links only */}
        <div className={styles['sidebar-footer']}>
          <div className={styles['sidebar-footer-links']}>
            <button className={styles['sidebar-footer-link']} onClick={() => navigate('/profile')}>
              <span className="material-symbols-outlined" style={{ fontSize: 20 }}>settings</span>
              <span>Settings</span>
            </button>
            <button className={`${styles['sidebar-footer-link']} ${styles['sidebar-logout']}`} onClick={handleLogout} id="sidebar-logout-btn">
              <span className="material-symbols-outlined" style={{ fontSize: 20 }}>logout</span>
              <span>Logout</span>
            </button>
          </div>
        </div>
      </aside>

      {/* ── Main Area ── */}
      <div className={styles['main-area']}>
        <header className={styles.topbar}>
          <button
            className={styles.hamburger}
            onClick={() => setSidebarOpen(!sidebarOpen)}
            id="hamburger-btn"
          >
            <span className="material-symbols-outlined">menu</span>
          </button>

          <div className={styles['topbar-brand']}>
            <span className={styles['topbar-brand-text']} onClick={() => navigate('/admin')}>
              SmartPrep
            </span>
          </div>

          {/* Desktop nav links */}
          <nav className={styles['topbar-nav']} style={{ display: 'none' }}>
            <a href="#" className={`${styles['topbar-nav-link']} ${styles.active}`}>Practice</a>
            <a href="#" className={styles['topbar-nav-link']}>Exams</a>
            <a href="#" className={styles['topbar-nav-link']}>Resources</a>
          </nav>

          <div className={styles['topbar-right']}>
            <button className={styles['topbar-upgrade']} style={{ display: 'none' }}>Upgrade Pro</button>
            <button className={styles['topbar-icon-btn']}>
              <span className="material-symbols-outlined">notifications</span>
            </button>
            <span className={styles['topbar-greeting']}>{displayName}</span>
            <div
              className={styles['topbar-avatar']}
              onClick={() => navigate('/profile')}
              id="topbar-profile-btn"
            >
              {initials}
            </div>
          </div>
        </header>

        <main className={styles['page-content']}>
          <Outlet />
        </main>
      </div>
    </div>
  );
}
