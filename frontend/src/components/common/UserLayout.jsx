import { useState, useRef, useEffect } from 'react';
import { NavLink, Outlet, useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { useToast } from '../../context/ToastContext';
import authService from '../../api/authService';

export default function UserLayout() {
  const { user, logout } = useAuth();
  const { success, error } = useToast();
  const navigate = useNavigate();
  const location = useLocation();
  const [dropdownOpen, setDropdownOpen] = useState(false);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [resending, setResending] = useState(false);
  const [cooldown, setCooldown] = useState(0);
  const dropdownRef = useRef(null);

  useEffect(() => {
    if (cooldown > 0) {
      const timer = setTimeout(() => setCooldown(cooldown - 1), 1000);
      return () => clearTimeout(timer);
    }
  }, [cooldown]);

  const handleResendVerification = async () => {
    if (resending || cooldown > 0) return;
    setResending(true);
    try {
      await authService.resendVerification();
      success('Verification email has been resent successfully. Please check your inbox.');
      setCooldown(60);
    } catch (err) {
      const msg = err.response?.data?.message || err.message || 'Failed to resend verification email';
      error(msg);
    } finally {
      setResending(false);
    }
  };

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  const displayName = user?.displayName || user?.username || 'Student';
  const initials = displayName.charAt(0).toUpperCase();

  // Close dropdown on click outside
  useEffect(() => {
    function handleClickOutside(event) {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target)) {
        setDropdownOpen(false);
      }
    }
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  // Close mobile menu on route change
  useEffect(() => {
    setMobileMenuOpen(false);
  }, [location.pathname]);

  const navLinks = [
    { to: '/dashboard', label: 'Dashboard' },
    { to: '/mock-tests', label: 'Mock Test' },
    { to: '/reading', label: 'Reading' },
    { to: '/writing', label: 'Writing' },
    { to: '/listening', label: 'Listening' },
    { to: '/vocabulary', label: 'Vocabulary' },
    { to: '/history', label: 'History' },
  ];

  return (
    <div className="bg-background min-h-screen flex flex-col font-sans">
      {/* Top Sticky Navbar */}
      <nav className="sticky top-0 z-50 bg-surface-container-lowest border-b border-outline-variant/30 shadow-sm">
        <div className="flex items-center max-w-container_max_width mx-auto px-lg py-md w-full relative">
          
          {/* Brand Logo */}
          <div 
            className="flex items-center gap-sm cursor-pointer hover:opacity-80 transition-opacity flex-shrink-0 z-10"
            onClick={() => navigate('/dashboard')}
          >
            <span className="material-symbols-outlined text-primary text-[32px] icon-fill">school</span>
            <span className="font-sans text-xl font-extrabold text-primary tracking-tight">SmartPrep</span>
          </div>

          {/* Centered Navigation Links (Desktop) */}
          <div className="hidden md:flex items-center justify-center gap-lg flex-1 absolute inset-0 pointer-events-none">
            <div className="flex items-center gap-lg pointer-events-auto">
              {navLinks.map((link) => (
                <NavLink
                  key={link.to}
                  to={link.to}
                  aria-current={({ isActive }) => isActive ? 'page' : undefined}
                  className={({ isActive }) => `
                    text-label-md font-bold pb-1 border-b-2 transition-all duration-200
                    ${isActive 
                      ? 'text-primary border-primary' 
                      : 'text-on-surface-variant border-transparent hover:text-primary'
                    }
                  `}
                >
                  {link.label}
                </NavLink>
              ))}
            </div>
          </div>

          {/* Right Actions */}
          <div className="flex items-center gap-md ml-auto z-10">
            {/* Notification Bell */}
            <button className="text-on-surface-variant hover:text-primary transition-colors flex items-center justify-center w-10 h-10 rounded-full hover:bg-surface-container-low">
              <span className="material-symbols-outlined icon-fill">notifications</span>
            </button>

            {/* User Dropdown */}
            <div className="relative" ref={dropdownRef}>
              <button 
                className="w-10 h-10 rounded-full overflow-hidden border-2 border-primary-fixed hover:border-primary transition-colors flex items-center justify-center bg-primary-container text-white font-bold relative"
                onClick={() => setDropdownOpen(!dropdownOpen)}
              >
                <img 
                  alt="User avatar" 
                  className="w-full h-full object-cover" 
                  src={user?.avatarUrl ? (user.avatarUrl.startsWith('http') ? user.avatarUrl : (import.meta.env.VITE_API_URL || 'http://localhost:8080/api/v1').replace('/api/v1', '') + user.avatarUrl) : '/assets/avatars/avatar_sarah.png'}
                  onError={(e) => {
                    // Fallback to initials if image fails to load
                    e.currentTarget.style.display = 'none';
                  }}
                />
                <span className="absolute z-[-1]">{initials}</span>
              </button>

              {dropdownOpen && (
                <div className="absolute right-0 mt-2 w-56 bg-surface-container-lowest border border-outline-variant/50 rounded-xl shadow-lg py-2 z-50">
                  <div className="px-4 py-3 border-b border-outline-variant/30">
                    <p className="font-semibold text-on-surface text-sm">{displayName}</p>
                    <p className="text-on-surface-variant text-xs truncate">{user?.email || ''}</p>
                  </div>
                  <button 
                    className="w-full text-left px-4 py-2 text-sm text-on-surface hover:bg-surface-container-low transition-colors flex items-center gap-2"
                    onClick={() => { setDropdownOpen(false); navigate('/profile'); }}
                  >
                    <span className="material-symbols-outlined text-[20px]">settings</span>
                    Profile & Settings
                  </button>
                  <div className="border-t border-outline-variant/20 my-1" />
                  <button 
                    className="w-full text-left px-4 py-2 text-sm text-error hover:bg-error-container/10 transition-colors flex items-center gap-2"
                    onClick={handleLogout}
                  >
                    <span className="material-symbols-outlined text-[20px]">logout</span>
                    Logout
                  </button>
                </div>
              )}
            </div>

            {/* Mobile Hamburger Button */}
            <button 
              className="md:hidden text-on-surface-variant hover:text-primary transition-colors flex items-center justify-center w-10 h-10 rounded-full hover:bg-surface-container-low"
              onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
            >
              <span className="material-symbols-outlined">{mobileMenuOpen ? 'close' : 'menu'}</span>
            </button>
          </div>
        </div>

        {/* Mobile Navigation Drawer */}
        {mobileMenuOpen && (
          <div className="md:hidden border-t border-outline-variant/30 bg-surface-container-lowest shadow-inner py-2 px-md">
            <div className="flex flex-col gap-xs">
              {navLinks.map((link) => (
                <NavLink
                  key={link.to}
                  to={link.to}
                  aria-current={({ isActive }) => isActive ? 'page' : undefined}
                  className={({ isActive }) => `
                    px-4 py-3 rounded-lg font-bold text-sm transition-all duration-150 flex items-center justify-between
                    ${isActive 
                      ? 'bg-primary-fixed text-primary' 
                      : 'text-on-surface-variant hover:bg-surface-container-low hover:text-primary'
                    }
                  `}
                >
                  {({ isActive }) => (
                    <>
                      <span>{link.label}</span>
                      {isActive && <span className="w-1.5 h-1.5 rounded-full bg-primary" />}
                    </>
                  )}
                </NavLink>
              ))}
            </div>
          </div>
        )}
      </nav>

      {/* Unverified Email Warning Banner */}
      {user && user.emailVerified === false && (
        <div className="bg-tertiary-fixed text-on-tertiary-fixed border-b border-tertiary/20 py-2.5 px-md flex flex-wrap justify-center items-center gap-x-2 gap-y-1 text-xs font-semibold text-center relative z-40 animate-fade-in">
          <span className="material-symbols-outlined text-[18px] text-tertiary animate-pulse" style={{ fontVariationSettings: "'FILL' 1" }}>warning</span>
          <span>Your email is not verified. Please check your inbox or</span>
          <button 
            onClick={handleResendVerification}
            disabled={resending || cooldown > 0}
            className="text-primary hover:text-primary-container hover:underline font-bold focus:outline-none disabled:opacity-50"
          >
            {resending ? 'resending...' : cooldown > 0 ? `resend in ${cooldown}s` : 'click here to resend the verification link'}
          </button>
        </div>
      )}

      {/* Main Content Area */}
      <main className="flex-grow w-full max-w-container_max_width mx-auto px-md md:px-margin py-margin">
        <Outlet />
      </main>
    </div>
  );
}
