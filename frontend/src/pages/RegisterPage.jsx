import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function RegisterPage() {
  const [email, setEmail] = useState('');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const { register } = useAuth();
  const navigate = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      await register(email, username, password);
      navigate('/dashboard');
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Registration failed');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-page">
      <div className="auth-left">
        <div className="auth-left-content">
          <div className="auth-shapes">
            <div className="auth-shape auth-shape-1"></div>
            <div className="auth-shape auth-shape-2"></div>
            <div className="auth-shape auth-shape-3"></div>
          </div>
          <h1>IELTS SmartPrep</h1>
          <p>Master IELTS with AI-Powered Practice</p>
          <div className="auth-features">
            <div className="auth-feature">
              <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2"><path d="M3 3v18h18" /><path d="m19 9-5 5-4-4-3 3" /></svg>
              <span>Track Your Progress</span>
            </div>
            <div className="auth-feature">
              <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2"><circle cx="12" cy="12" r="10" /><path d="M12 6v6l4 2" /></svg>
              <span>Timed Practice Sessions</span>
            </div>
            <div className="auth-feature">
              <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2"><path d="M12 2L2 7l10 5 10-5-10-5z" /><path d="M2 17l10 5 10-5" /><path d="M2 12l10 5 10-5" /></svg>
              <span>Detailed Score Analytics</span>
            </div>
          </div>
        </div>
      </div>
      <div className="auth-right">
        <form className="auth-form" onSubmit={handleSubmit}>
          <h2 className="reveal">Create Account</h2>
          {error && <div className="error-msg reveal">{error}</div>}
          <div className="form-group floating reveal reveal-delay-1">
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
              id="register-email"
              placeholder=" "
            />
            <label htmlFor="register-email">Email</label>
          </div>
          <div className="form-group floating reveal reveal-delay-2">
            <input
              type="text"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              required
              id="register-username"
              placeholder=" "
            />
            <label htmlFor="register-username">Username</label>
          </div>
          <div className="form-group floating reveal reveal-delay-3">
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              minLength={6}
              id="register-password"
              placeholder=" "
            />
            <label htmlFor="register-password">Password (min 6 chars)</label>
          </div>
          <button type="submit" className="btn btn-primary reveal reveal-delay-4" disabled={loading} id="register-submit-btn">
            {loading ? <><span className="spinner"></span> Creating...</> : 'Create Account'}
          </button>
          <p className="auth-link reveal reveal-delay-5">
            Already have an account? <Link to="/login">Sign In</Link>
          </p>
        </form>
      </div>
    </div>
  );
}
